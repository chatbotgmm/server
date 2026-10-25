#!/usr/bin/env python3
"""Backup/restore helper: no SQL seed, no unlink of a database or its WAL."""
import sys, os, sqlite3, hashlib, pathlib, subprocess
TARGET='/home/ubuntu/gmm/galmaegiman-confirmed/storage/confirmed.db'
def stopped():
    def prop(p):return subprocess.check_output(['systemctl','show','gmm-server.service','-p',p,'--value'],text=True).strip()
    if prop('ActiveState')!='inactive' or prop('MainPID')!='0':raise RuntimeError('SERVICE_MUST_BE_STOPPED')
def no_holders(path):
    names={str(path),str(path)+'-wal',str(path)+'-shm'};holders=set()
    for process in pathlib.Path('/proc').iterdir():
        if not process.name.isdigit() or int(process.name)==os.getpid():continue
        try:
            for fd in (process/'fd').iterdir():
                try:
                    if os.readlink(fd) in names:holders.add(process.name)
                except FileNotFoundError:pass
        except FileNotFoundError:pass
        except PermissionError:raise RuntimeError('HOLDER_CHECK_REQUIRES_ROOT')
    if holders:raise RuntimeError('DB_STILL_OPEN_BY_PIDS:'+','.join(sorted(holders)))
def ro(p):return sqlite3.connect(p.as_uri()+'?mode=ro',uri=True,timeout=10)
def digest(c):return hashlib.sha256('\n'.join(c.iterdump()).encode()).hexdigest()
def check(c):
    assert c.execute('PRAGMA integrity_check').fetchall()==[('ok',)],'INTEGRITY_FAILED'
    assert c.execute('PRAGMA foreign_key_check').fetchall()==[],'FOREIGN_KEY_CHECK_FAILED'
def create(p):
    fd=os.open(p,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600);os.close(fd)
    return sqlite3.connect(p,timeout=10)
def main():
    mode,db_arg,backup_arg,*args=sys.argv[1:];isolated=args==['--isolated']
    db=pathlib.Path(db_arg).resolve(strict=True);backup=pathlib.Path(backup_arg)
    if isolated:
        assert str(db.parent).startswith('/tmp/gmm-progress-reset-') and db.name=='copy.db' and (db.parent/'ISOLATED_COPY').is_file(),'ISOLATED_COPY_REQUIRED'
        assert backup.parent.resolve()==db.parent,'ISOLATED_BACKUP_REQUIRED'
    else:
        assert str(db)==TARGET and not pathlib.Path(db_arg).is_symlink(),'DB_TARGET_CHANGED'
        assert str(backup.parent.resolve()).startswith('/home/ubuntu/gmm-deploy-backups/game-progress-reset-') and backup.name=='before.db','BACKUP_TARGET_INVALID'
        stopped();no_holders(db)
    if mode=='holders':return
    if mode=='backup':
        a=ro(db);check(a);b=create(backup);a.backup(b);check(b);assert digest(a)==digest(b),'BACKUP_MISMATCH';a.close();b.close()
        # Exercise actual SQLite restore into a new scratch file before destructive work.
        a=ro(backup.resolve());check_path=backup.with_name('restore-check.db');b=create(check_path);a.backup(b);check(b);assert digest(a)==digest(b),'RESTORE_REHEARSAL_MISMATCH';a.close();b.close()
        print('Consistent backup + isolated restore rehearsal: OK')
    elif mode=='restore':
        assert backup.is_file() and not backup.is_symlink(),'BACKUP_MISSING'
        a=ro(backup.resolve());check(a);b=sqlite3.connect(db.as_uri()+'?mode=rw',uri=True,timeout=10);a.backup(b);check(b);assert digest(a)==digest(b),'RESTORE_MISMATCH';b.execute('PRAGMA wal_checkpoint(TRUNCATE)');a.close();b.close();print('Database restored and verified: OK')
    else:raise RuntimeError('MODE_INVALID')
if __name__=='__main__':main()
