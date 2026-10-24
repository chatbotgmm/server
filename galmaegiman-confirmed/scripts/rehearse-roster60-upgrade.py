#!/usr/bin/env python3
"""Offline rehearsal only: creates its own temporary DB, never reads .env or live DB."""
import json, pathlib, sqlite3, subprocess, tempfile, os, hashlib
root=pathlib.Path(__file__).resolve().parents[1]
out=pathlib.Path(tempfile.mkdtemp(prefix='gmm-roster-upgrade-'))
previous='cde215207210d4eed8dde80fe5c7b3df629337eb'
old=out/'previous.prisma'
old.write_bytes(subprocess.check_output(['git','show',previous+':galmaegiman-confirmed/prisma/schema.prisma'],cwd=root))
env={**os.environ,'DATABASE_URL':'file:'+str(out/'unused.db')}
def diff(args):
 return subprocess.check_output(['node','node_modules/prisma/build/index.js','migrate','diff',*args,'--script'],cwd=root,env=env).decode()
base=diff(['--from-empty','--to-schema-datamodel',str(old)])
upgrade=diff(['--from-schema-datamodel',str(old),'--to-schema-datamodel','prisma/schema.prisma'])
statements=[line.strip() for line in upgrade.splitlines() if line.strip() and not line.strip().startswith('--')]
if any(line.upper().startswith(('DROP ','ALTER ','DELETE ','UPDATE ','INSERT ','PRAGMA ')) for line in statements):
 raise RuntimeError('Not an additive-only upgrade; review required')
(out/'upgrade-review-only.sql').write_text(upgrade)
db=sqlite3.connect(out/'fixture.db');db.execute('PRAGMA foreign_keys=ON');db.executescript(base)
db.execute('INSERT INTO Player(id,identity,credits,peanut) VALUES(?,?,?,?)',('fixture-account','isolated-only',73,4));db.commit()
before=db.execute('SELECT * FROM Player').fetchall()
backup=sqlite3.connect(out/'backup.db');db.backup(backup);backup.close()
old_tables={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
db.executescript('BEGIN;\n'+upgrade+'\nCOMMIT;')
assert db.execute('SELECT * FROM Player').fetchall()==before
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
assert db.execute('PRAGMA foreign_key_check').fetchall()==[]
new_tables={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}-old_tables
assert len(new_tables)==9
# A deliberately invalid owner cannot create a new-system unit.
try: db.execute("INSERT INTO Roster60Owned(id,playerId,speciesId,source) VALUES('bad','other','u01','test')")
except sqlite3.IntegrityError: db.rollback()
else: raise AssertionError('foreign owner accepted')
# Restore into a separate disposable DB, never overwrite even the fixture.
restored=sqlite3.connect(out/'restored.db');backup=sqlite3.connect(out/'backup.db');backup.backup(restored);backup.close()
assert restored.execute('SELECT * FROM Player').fetchall()==before
assert restored.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
result={'previousSchemaCommit':previous,'targetCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root).decode().strip(),'targetSchemaSha256':hashlib.sha256((root/'prisma/schema.prisma').read_bytes()).hexdigest(),'newTables':sorted(new_tables),'accountAndCurrencyPreserved':True,'foreignKeysVerified':True,'backupRestoreVerified':True,'liveDatabaseAccessed':False,'artifactDirectory':str(out)}
(out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
