#!/usr/bin/env python3
"""수동 유지보수 전용: 기존/신규 점수만 0으로 초기화합니다.

기본 동작은 읽기 전용 점검입니다. 실제 적용에는 --apply, --confirm, --backup이
모두 필요합니다. CI/시작 스크립트/마이그레이션에서 호출하지 마세요.
이 파일은 작성만 된 초안이며 운영 DB에서 실행하지 않았습니다.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sqlite3
import stat
import sys
import time
from datetime import datetime, timezone

CONFIRMATION = "RESET_ALL_POINTS_TO_ZERO"
REQUIRED_COLUMNS = {
    "Player": {"id", "rankPoint"},
    "RankedProfile": {"playerId", "tierIndex", "lp", "promotionActive", "promotionWins",
                      "promotionLosses", "version", "updatedAt", "rankedMmr",
                      "casualPriorOffset", "games", "wins", "losses", "draws"},
    "RealtimeRoom": {"state"},
    "RankedMatch": {"state"},
    "RealtimeActivity": {"playerId"},
    "RealtimeAttempt": {"state", "leaseUntil"},
}


class ResetRefused(RuntimeError):
    """안전 조건이 충족되지 않아 아무 점수도 변경하지 않았습니다."""


def _uri(path: Path, mode: str) -> str:
    return path.as_uri() + "?mode=" + mode


def _connect(path: Path, mode: str) -> sqlite3.Connection:
    # mode=rw/ro: 경로를 잘못 입력했을 때 새 빈 운영 DB를 생성하지 않습니다.
    conn = sqlite3.connect(_uri(path, mode), uri=True, timeout=10.0, isolation_level=None)
    conn.execute("PRAGMA busy_timeout=10000")
    return conn


def _source_path(value: str | Path) -> Path:
    path = Path(value).expanduser().resolve(strict=True)
    if not stat.S_ISREG(path.stat().st_mode):
        raise ResetRefused("--db는 이미 존재하는 일반 SQLite 파일이어야 합니다.")
    return path


def _assert_schema(conn: sqlite3.Connection) -> None:
    for table, required in REQUIRED_COLUMNS.items():
        found = conn.execute("SELECT type FROM sqlite_master WHERE name=?", (table,)).fetchone()
        if found != ("table",):
            raise ResetRefused(f"필수 테이블이 없습니다: {table}. 스키마를 자동 생성하지 않습니다.")
        # 식별자는 위의 고정 목록에서만 옵니다.
        columns = {row[1] for row in conn.execute(f'PRAGMA table_info("{table}")')}
        missing = required - columns
        if missing:
            raise ResetRefused(f"{table} 필수 열 누락: {', '.join(sorted(missing))}")
    invalid_versions = conn.execute("""SELECT COUNT(*) FROM RankedProfile
        WHERE typeof(version)<>'integer' OR version<0 OR version>=2147483647""").fetchone()[0]
    if invalid_versions:
        raise ResetRefused("RankedProfile.version이 올바른 Prisma Int 증가 범위를 벗어납니다.")
    # 예상하지 못한 UPDATE 트리거가 다른 전적/재화를 바꾸는 것을 막습니다.
    # 계정 삭제 보호용 DELETE 트리거는 이 UPDATE 작업에 반응하지 않습니다.
    triggers = conn.execute("SELECT name, sql FROM sqlite_master WHERE type='trigger' AND tbl_name IN ('Player','RankedProfile')").fetchall()
    for name, sql in triggers:
        header = re.split(r"\bBEGIN\b", sql or "", maxsplit=1, flags=re.IGNORECASE)[0]
        if not header or re.search(r"\bUPDATE\b", header, flags=re.IGNORECASE):
            raise ResetRefused(f"검토하지 않은 UPDATE 트리거가 있습니다: {name}. 트리거를 자동 해제하지 않습니다.")


def _epoch_ms(value: object) -> int:
    # Prisma SQLite DateTime은 보통 Unix ms 정수이며 SQL fixture/수동 기록은 ISO 문자일 수 있습니다.
    if isinstance(value, bool) or value is None:
        raise ResetRefused("WAITING 요청의 leaseUntil이 없어 안전하게 만료를 판단할 수 없습니다.")
    if isinstance(value, (int, float)):
        if not math.isfinite(value):
            raise ResetRefused("WAITING 요청의 leaseUntil이 유효하지 않습니다.")
        if 1_000_000_000 <= value < 100_000_000_000:
            raise ResetRefused("WAITING leaseUntil이 Unix 초처럼 보입니다. Prisma Unix ms인지 확인하세요.")
        return int(value)
    if isinstance(value, str):
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=timezone.utc)
            return int(parsed.timestamp() * 1000)
        except (ValueError, OverflowError) as exc:
            raise ResetRefused("WAITING 요청의 leaseUntil을 해석할 수 없습니다.") from exc
    raise ResetRefused("WAITING 요청의 leaseUntil 형식이 지원되지 않습니다.")


def _assert_quiet(conn: sqlite3.Connection, now_ms: int) -> None:
    blockers = {
        "RealtimeRoom ACTIVE/PREPARING": conn.execute("SELECT COUNT(*) FROM RealtimeRoom WHERE state IN ('ACTIVE','PREPARING')").fetchone()[0],
        "RankedMatch ACTIVE": conn.execute("SELECT COUNT(*) FROM RankedMatch WHERE state='ACTIVE'").fetchone()[0],
        "RealtimeActivity": conn.execute("SELECT COUNT(*) FROM RealtimeActivity").fetchone()[0],
    }
    live_waiting = 0
    for (lease_until,) in conn.execute("SELECT leaseUntil FROM RealtimeAttempt WHERE state='WAITING'"):
        if _epoch_ms(lease_until) > now_ms:
            live_waiting += 1
    blockers["live WAITING"] = live_waiting
    present = {name: count for name, count in blockers.items() if count}
    if present:
        raise ResetRefused("진행 중 상태 때문에 초기화를 중단했습니다: " + json.dumps(present, ensure_ascii=False))


def _summary(conn: sqlite3.Connection) -> dict[str, int]:
    players, changed_players = conn.execute("SELECT COUNT(*), COALESCE(SUM(rankPoint<>0),0) FROM Player").fetchone()
    profiles, changed_profiles = conn.execute("""SELECT COUNT(*), COALESCE(SUM(
        tierIndex<>0 OR lp<>0 OR promotionActive<>0 OR promotionWins<>0 OR promotionLosses<>0),0)
        FROM RankedProfile""").fetchone()
    return {"players": players, "players_with_nonzero_points": changed_players,
            "ranked_profiles": profiles, "profiles_with_nonzero_rank_state": changed_profiles}


def _preflight(source: Path, now_ms: int) -> dict[str, int]:
    conn = _connect(source, "ro")
    try:
        conn.execute("BEGIN")
        _assert_schema(conn)
        _assert_quiet(conn, now_ms)
        result = _summary(conn)
        conn.execute("ROLLBACK")
        return result
    finally:
        conn.close()


def _integrity_ok(conn: sqlite3.Connection) -> None:
    results = conn.execute("PRAGMA integrity_check").fetchall()
    if results != [("ok",)]:
        raise ResetRefused("SQLite integrity_check 실패: " + repr(results[:10]))


def _backup_locked_source(source: Path, destination: Path) -> str:
    """호출자는 이미 원본에 BEGIN IMMEDIATE를 걸어 모든 다른 writer를 막아야 합니다.

    아직 원본 UPDATE 전이므로 별도 읽기 연결의 백업은 정확한 직전 상태입니다.
    원본 파일을 복사하지 않고 SQLite backup API를 사용하므로 WAL의 commit도 포함합니다.
    """
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    flags |= getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)
    fd = os.open(destination, flags, 0o600)  # 기존 파일/심볼릭 링크를 덮어쓰지 않습니다.
    os.close(fd)
    source_conn = None
    backup_conn = None
    try:
        source_conn = _connect(source, "ro")
        backup_conn = _connect(destination, "rw")
        source_conn.backup(backup_conn)
        # 독립적인 단일 파일 백업으로 남깁니다. 원본 journal_mode는 변경하지 않습니다.
        backup_conn.execute("PRAGMA journal_mode=DELETE")
        _integrity_ok(backup_conn)
    except Exception as exc:
        # 실패한 파일도 자동 삭제하지 않습니다. 보존한 파일을 유효 백업으로 오인하면 안 됩니다.
        raise ResetRefused(f"백업 검증 실패. 점수는 변경하지 않았습니다. 실패 파일 확인 필요: {destination}") from exc
    finally:
        if backup_conn is not None:
            backup_conn.close()
        if source_conn is not None:
            source_conn.close()
    with destination.open("rb") as handle:
        os.fsync(handle.fileno())
        hasher = hashlib.sha256()
        while chunk := handle.read(1024 * 1024):
            hasher.update(chunk)
        digest = hasher.hexdigest()
    # 디렉터리 엔트리도 저장한 뒤에만 원본 점수를 바꿉니다. Unix 서버용입니다.
    directory_fd = os.open(destination.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(directory_fd)
    finally:
        os.close(directory_fd)
    return digest


def reset_points(db_path: str | Path, *, backup_path: str | Path | None = None,
                 apply: bool = False, confirmation: str | None = None,
                 now_ms: int | None = None) -> dict[str, object]:
    source = _source_path(db_path)
    at_ms = int(time.time() * 1000) if now_ms is None else now_ms
    if not isinstance(at_ms, int) or isinstance(at_ms, bool) or at_ms < 0:
        raise ResetRefused("유효한 UTC Unix ms 시간이 필요합니다.")
    observed = _preflight(source, at_ms)
    if not apply:
        return {"applied": False, "database": str(source), "preflight": observed,
                "message": "읽기 전용 점검 완료. 어떤 점수나 백업 파일도 변경/생성하지 않았습니다."}
    if confirmation != CONFIRMATION:
        raise ResetRefused(f"실제 적용에는 --confirm {CONFIRMATION}이 필요합니다.")
    if backup_path is None:
        raise ResetRefused("실제 적용에는 새 고유 백업 경로 --backup이 필요합니다.")
    backup = Path(backup_path).expanduser().absolute()
    parent = backup.parent.resolve(strict=True)
    backup = parent / backup.name
    if backup == source:
        raise ResetRefused("백업 경로와 원본 DB 경로가 같습니다.")
    if backup.exists() or backup.is_symlink():
        raise ResetRefused("백업 파일이 이미 존재합니다. 새 이름을 지정하세요.")
    conn = _connect(source, "rw")
    digest: str | None = None
    try:
        # 외부 writer가 사전 점검 뒤 끼어드는 것을 막고 모든 조건을 잠금 안에서 재확인합니다.
        conn.execute("BEGIN IMMEDIATE")
        _assert_schema(conn)
        _assert_quiet(conn, int(time.time() * 1000) if now_ms is None else at_ms)
        before = _summary(conn)
        _integrity_ok(conn)
        digest = _backup_locked_source(source, backup)
        # 업데이트 중 덤프/네트워크 호출/애플리케이션 로직을 실행하지 않습니다.
        conn.execute("UPDATE Player SET rankPoint=0")
        conn.execute("""UPDATE RankedProfile SET tierIndex=0, lp=0,
            promotionActive=0, promotionWins=0, promotionLosses=0,
            version=version+1, updatedAt=?""", (at_ms,))
        after = _summary(conn)
        if after["players_with_nonzero_points"] or after["profiles_with_nonzero_rank_state"]:
            raise ResetRefused("초기화 결과가 기대한 0이 아닙니다. 전체 변경을 되돌립니다.")
        if (after["players"], after["ranked_profiles"]) != (before["players"], before["ranked_profiles"]):
            raise ResetRefused("행 개수가 바뀌었습니다. 전체 변경을 되돌립니다.")
        conn.execute("COMMIT")
        return {"applied": True, "database": str(source), "backup": str(backup),
                "backup_sha256": digest, "before": before, "after": after,
                "warning": "전체 백업 복원은 이후 쓰기가 전혀 없을 때만 가능합니다. 복원 절차는 README를 확인하세요."}
    except BaseException:
        if conn.in_transaction:
            conn.execute("ROLLBACK")
        raise
    finally:
        conn.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--db", required=True, help="존재하는 SQLite DB 파일의 절대 경로. DATABASE_URL은 자동 사용하지 않음")
    parser.add_argument("--apply", action="store_true", help="점검 후 백업하고 점수를 초기화")
    parser.add_argument("--confirm", help=f"정확히 {CONFIRMATION}")
    parser.add_argument("--backup", help="기존 파일이 없는 새로운 백업 파일 경로")
    args = parser.parse_args()
    try:
        result = reset_points(args.db, backup_path=args.backup, apply=args.apply, confirmation=args.confirm)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (ResetRefused, sqlite3.Error, OSError, ValueError) as exc:
        print(f"초기화 실패/거절: {exc}", file=sys.stderr)
        print("COMMIT 결과가 불명확한 장애라면 재실행/복원 전에 점수와 백업을 먼저 확인하세요.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
