#!/usr/bin/env python3
"""Exercise the exact shell rollback function with fake service commands and temporary files."""
import unittest,tempfile,pathlib,subprocess,json,shlex
SCRIPT=pathlib.Path(__file__).with_name('deploy-ec2.sh').read_text()
ROLLBACK='rollback(){'+SCRIPT.split('rollback(){',1)[1].split('\ntrap rollback ERR INT TERM',1)[0]
class Rollback(unittest.TestCase):
 def run_case(self,committed=False,health=True,stop_ok=True):
  with tempfile.TemporaryDirectory(prefix='gmm-reset-shell-') as tmp:
   root=pathlib.Path(tmp);app=root/'app';backup=root/'backup';stage=root/'stage';log=root/'commands'
   (app/'src').mkdir(parents=True);(backup/'code/src').mkdir(parents=True)
   (app/'src/app.ts').write_text('new');(app/'src/game-reset-session.ts').write_text('newhelper')
   (backup/'code/src/app.ts').write_text('old');(backup/'code-files.json').write_text(json.dumps({'src/app.ts':True,'src/game-reset-session.ts':False}))
   env='\n'.join(f'{k}={shlex.quote(str(v))}' for k,v in dict(app=app,backup=backup,stage=stage,db=root/'copy.db',service='fake-game',tunnel='fake-tunnel',log=log).items())
   shell=env+f'''\ncommitted={int(committed)};stopped=1;dbChanged=1;codeChanged=1
systemctl() {{ echo "$*" >> "$log"; {'if [ "$1" = stop ]; then return 1; fi;' if not stop_ok else ''} return 0; }}
healthy() {{ return {0 if health else 1}; }}
python3() {{ if [[ "$1" = */db-ops.py ]]; then echo DB_RESTORE >> "$log"; return 0; fi; command python3 "$@"; }}
'''+ROLLBACK+'\nfalse\nrollback\n'
   run=subprocess.run(['bash','-c',shell],capture_output=True,text=True);self.assertEqual(run.returncode,1,run.stderr)
   commands=log.read_text() if log.exists() else ''
   if committed:self.assertEqual(commands,'');self.assertEqual((app/'src/app.ts').read_text(),'new')
   elif not stop_ok:self.assertNotIn('DB_RESTORE',commands);self.assertEqual((app/'src/app.ts').read_text(),'new')
   else:
    self.assertLess(commands.index('stop fake-tunnel fake-game'),commands.index('DB_RESTORE'))
    self.assertEqual((app/'src/app.ts').read_text(),'old');self.assertFalse((app/'src/game-reset-session.ts').exists())
    if health:self.assertIn('start fake-tunnel',commands)
    else:self.assertNotIn('start fake-tunnel',commands)
 def test_restore_before_restart(self):self.run_case()
 def test_failed_health_keeps_ingress_closed(self):self.run_case(health=False)
 def test_stop_failure_never_restores_live_db(self):self.run_case(stop_ok=False)
 def test_committed_reset_never_rolled_back_after_ingress(self):self.run_case(committed=True)
if __name__=='__main__':unittest.main()
