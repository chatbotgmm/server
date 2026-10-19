"""별도 임시 SQLite 파일만 사용하는 수동 검증 테스트 초안. 운영 DB/프로젝트 미실행."""
from __future__ import annotations

import importlib.util
import hashlib
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location("reset_all_points", Path(__file__).with_name("reset_all_points.py"))
assert SPEC and SPEC.loader
reset = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reset)
NOW = 1_800_000_000_000


class ResetPointsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="gmm-reset-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.db = self.root / "isolated.db"
        self.backup = self.root / "before-reset.sqlite3"
        with sqlite3.connect(self.db) as conn:
            conn.executescript("""
                CREATE TABLE Player(id TEXT PRIMARY KEY, rankPoint INTEGER NOT NULL,
                    rankWins INTEGER NOT NULL, rankLosses INTEGER NOT NULL, credits INTEGER NOT NULL);
                CREATE TABLE RankedProfile(playerId TEXT PRIMARY KEY, tierIndex INTEGER NOT NULL,
                    lp INTEGER NOT NULL, promotionActive INTEGER NOT NULL, promotionWins INTEGER NOT NULL,
                    promotionLosses INTEGER NOT NULL, version INTEGER NOT NULL, updatedAt DATETIME NOT NULL,
                    rankedMmr REAL NOT NULL, casualPriorOffset REAL NOT NULL, games INTEGER NOT NULL,
                    wins INTEGER NOT NULL, losses INTEGER NOT NULL, draws INTEGER NOT NULL);
                CREATE TABLE RealtimeRoom(id TEXT PRIMARY KEY, state TEXT, resultJson TEXT);
                CREATE TABLE RankedMatch(roomId TEXT PRIMARY KEY, state TEXT, winner INTEGER);
                CREATE TABLE RealtimeActivity(playerId TEXT PRIMARY KEY, kind TEXT, expiresAt DATETIME);
                CREATE TABLE RealtimeAttempt(id TEXT PRIMARY KEY, state TEXT, leaseUntil DATETIME);
                CREATE TABLE RankedSettlement(roomId TEXT PRIMARY KEY, receiptJson TEXT);
                CREATE TABLE CasualHumanRating(playerId TEXT PRIMARY KEY, mmr REAL, games INTEGER);
                CREATE TABLE CasualHumanSettlement(roomId TEXT PRIMARY KEY, receiptJson TEXT);
                INSERT INTO Player VALUES('a',1600,40,12,123),('b',700,10,30,456);
                INSERT INTO RankedProfile VALUES('a',2,100,1,1,1,7,1700000000000,1245.25,20,11,6,4,1);
                INSERT INTO RankedProfile VALUES('b',1,60,0,0,0,3,1700000000000,950,-5,10,2,7,1);
                INSERT INTO RealtimeRoom VALUES('old-room','FINISHED','{"winner":0}');
                INSERT INTO RankedMatch VALUES('old-room','FINISHED',0);
                INSERT INTO RankedSettlement VALUES('old-room','{"immutable":"rank-receipt"}');
                INSERT INTO CasualHumanRating VALUES('a',1100.5,50),('b',990.25,20);
                INSERT INTO CasualHumanSettlement VALUES('old-casual','{"immutable":"casual-receipt"}');
            """)

    def rows(self, table, db=None):
        with sqlite3.connect(db or self.db) as conn:
            return conn.execute(f'SELECT * FROM "{table}" ORDER BY rowid').fetchall()

    def apply(self, backup=None):
        return reset.reset_points(self.db, backup_path=backup or self.backup, apply=True,
                                  confirmation=reset.CONFIRMATION, now_ms=NOW)

    def execute(self, sql, args=()):
        with sqlite3.connect(self.db) as conn:
            conn.execute(sql, args)

    def test_default_preflight_does_not_write_or_create_backup(self):
        before = self.db.read_bytes()
        result = reset.reset_points(self.db, backup_path=self.backup, now_ms=NOW)
        self.assertFalse(result["applied"])
        self.assertEqual(before, self.db.read_bytes())
        self.assertFalse(self.backup.exists())

    def test_only_requested_fields_change_and_original_backup_is_valid(self):
        tables = ["Player", "RankedProfile", "RealtimeRoom", "RankedMatch", "RankedSettlement",
                  "CasualHumanRating", "CasualHumanSettlement", "RealtimeAttempt", "RealtimeActivity"]
        before = {table: self.rows(table) for table in tables}
        result = self.apply()
        self.assertTrue(result["applied"])
        self.assertEqual(len(result["backup_sha256"]), 64)
        self.assertEqual(result["backup_sha256"], hashlib.sha256(self.backup.read_bytes()).hexdigest())
        self.assertEqual(self.backup.stat().st_mode & 0o777, 0o600)
        for table in tables:
            self.assertEqual(self.rows(table, self.backup), before[table], table)
        for old, new in zip(before["Player"], self.rows("Player")):
            self.assertEqual(new, (old[0], 0, *old[2:]))
        for old, new in zip(before["RankedProfile"], self.rows("RankedProfile")):
            self.assertEqual(new[:6], (old[0], 0, 0, 0, 0, 0))
            self.assertEqual(new[6], old[6] + 1)
            self.assertEqual(new[7], NOW)
            self.assertEqual(new[8:], old[8:])
        for table in tables[2:]:
            self.assertEqual(self.rows(table), before[table], table)
        with sqlite3.connect(self.backup) as conn:
            self.assertEqual(conn.execute("PRAGMA integrity_check").fetchall(), [("ok",)])

    def test_refuses_missing_confirmation_or_missing_backup(self):
        with self.assertRaises(reset.ResetRefused):
            reset.reset_points(self.db, backup_path=self.backup, apply=True, now_ms=NOW)
        with self.assertRaises(reset.ResetRefused):
            reset.reset_points(self.db, apply=True, confirmation=reset.CONFIRMATION, now_ms=NOW)
        self.assertFalse(self.backup.exists())
        self.assertEqual(self.rows("Player")[0][1], 1600)

    def test_no_existing_backup_is_overwritten(self):
        self.backup.write_bytes(b"existing backup do not replace")
        with self.assertRaises(reset.ResetRefused):
            self.apply()
        self.assertEqual(self.backup.read_bytes(), b"existing backup do not replace")
        self.assertEqual(self.rows("Player")[0][1], 1600)

    def test_exclusive_file_creation_closes_check_create_race(self):
        original = reset._backup_locked_source
        def raced(source, destination):
            destination.write_bytes(b"created by someone else")
            return original(source, destination)
        with mock.patch.object(reset, "_backup_locked_source", side_effect=raced):
            with self.assertRaises(FileExistsError):
                self.apply()
        self.assertEqual(self.backup.read_bytes(), b"created by someone else")
        self.assertEqual(self.rows("Player")[0][1], 1600)

    def test_source_path_must_exist(self):
        missing = self.root / "missing.db"
        with self.assertRaises(FileNotFoundError):
            reset.reset_points(missing, now_ms=NOW)
        self.assertFalse(missing.exists())

    def test_every_active_or_preparing_room_and_active_ranked_match_blocks(self):
        for table, state in [("RealtimeRoom", "ACTIVE"), ("RealtimeRoom", "PREPARING"), ("RankedMatch", "ACTIVE")]:
            with self.subTest(table=table, state=state):
                self.execute(f'UPDATE "{table}" SET state=?', (state,))
                with self.assertRaises(reset.ResetRefused):
                    self.apply()
                self.assertFalse(self.backup.exists())
                self.execute(f'UPDATE "{table}" SET state=?', ("FINISHED",))

    def test_any_activity_blocks_even_if_lease_looks_expired(self):
        self.execute("INSERT INTO RealtimeActivity VALUES('a','QUEUE',?)", (NOW - 1000,))
        with self.assertRaises(reset.ResetRefused):
            self.apply()
        self.assertFalse(self.backup.exists())

    def test_live_waiting_blocks_numeric_and_iso_timestamps(self):
        for value in [NOW + 1000, "2030-01-01T00:00:00.000Z", (NOW + 1000) // 1000, None, "bad-date"]:
            with self.subTest(value=value):
                self.execute("INSERT INTO RealtimeAttempt VALUES('waiting','WAITING',?)", (value,))
                with self.assertRaises(reset.ResetRefused):
                    self.apply()
                self.execute("DELETE FROM RealtimeAttempt WHERE id='waiting'")

    def test_expired_waiting_is_preserved_but_not_blocking(self):
        self.execute("INSERT INTO RealtimeAttempt VALUES('expired','WAITING',?)", (NOW - 1,))
        before = self.rows("RealtimeAttempt")
        self.apply()
        self.assertEqual(self.rows("RealtimeAttempt"), before)

    def test_state_is_rechecked_after_begin_immediate(self):
        original = reset._preflight
        def raced(source, now_ms):
            value = original(source, now_ms)
            self.execute("INSERT INTO RealtimeActivity VALUES('a','ROOM',?)", (NOW + 1000,))
            return value
        with mock.patch.object(reset, "_preflight", side_effect=raced):
            with self.assertRaises(reset.ResetRefused):
                self.apply()
        self.assertFalse(self.backup.exists())
        self.assertEqual(self.rows("Player")[0][1], 1600)

    def test_backup_failure_never_changes_points(self):
        before = self.rows("RankedProfile")
        with mock.patch.object(reset, "_backup_locked_source", side_effect=reset.ResetRefused("forced backup failure")):
            with self.assertRaises(reset.ResetRefused):
                self.apply()
        self.assertEqual(self.rows("Player")[0][1], 1600)
        self.assertEqual(self.rows("RankedProfile"), before)

    def test_failure_after_first_update_rolls_back_both_tables(self):
        # 예상하지 못한 UPDATE 트리거는 원래 거절됩니다. 테스트에서만 사전검사를 우회해
        # 두 번째 UPDATE 실패 시 첫 번째 UPDATE도 rollback되는지 검증합니다.
        self.execute("""CREATE TRIGGER fail_rank_update BEFORE UPDATE ON RankedProfile
            BEGIN SELECT RAISE(ABORT,'forced-second-update-failure'); END""")
        before_players = self.rows("Player")
        before_profiles = self.rows("RankedProfile")
        with mock.patch.object(reset, "_assert_schema", return_value=None):
            with self.assertRaises(sqlite3.DatabaseError):
                self.apply()
        self.assertEqual(self.rows("Player"), before_players)
        self.assertEqual(self.rows("RankedProfile"), before_profiles)
        self.assertTrue(self.backup.exists())

    def test_unreviewed_update_trigger_is_rejected_without_disabling_it(self):
        self.execute("""CREATE TRIGGER change_credits AFTER UPDATE ON Player
            BEGIN UPDATE Player SET credits=0 WHERE id=NEW.id; END""")
        with self.assertRaises(reset.ResetRefused):
            self.apply()
        self.assertEqual(self.rows("Player")[0][-1], 123)
        self.assertFalse(self.backup.exists())

    def test_version_overflow_is_rejected_without_changing_points(self):
        self.execute("UPDATE RankedProfile SET version=2147483647 WHERE playerId='a'")
        with self.assertRaises(reset.ResetRefused):
            self.apply()
        self.assertEqual(self.rows("Player")[0][1], 1600)
        self.assertFalse(self.backup.exists())

    def test_delete_guard_trigger_does_not_block_score_only_update(self):
        self.execute("""CREATE TRIGGER delete_guard BEFORE DELETE ON Player
            BEGIN SELECT RAISE(ABORT,'no deletion'); END""")
        self.assertTrue(self.apply()["applied"])

    def test_backup_contains_committed_wal_data(self):
        writer = sqlite3.connect(self.db)
        self.addCleanup(writer.close)
        writer.execute("PRAGMA journal_mode=WAL")
        writer.execute("UPDATE Player SET rankPoint=1777 WHERE id='a'")
        writer.commit()
        self.apply()
        self.assertEqual(self.rows("Player", self.backup)[0][1], 1777)
        self.assertEqual(self.rows("Player")[0][1], 0)

    def test_write_lock_blocks_other_writers_until_backup_finishes(self):
        original = reset._backup_locked_source
        def probe(source, destination):
            other = sqlite3.connect(self.db, timeout=0, isolation_level=None)
            try:
                with self.assertRaises(sqlite3.OperationalError):
                    other.execute("UPDATE Player SET rankPoint=9999 WHERE id='a'")
            finally:
                other.close()
            return original(source, destination)
        with mock.patch.object(reset, "_backup_locked_source", side_effect=probe):
            self.apply()
        self.assertEqual(self.rows("Player", self.backup)[0][1], 1600)


if __name__ == "__main__":
    unittest.main()
