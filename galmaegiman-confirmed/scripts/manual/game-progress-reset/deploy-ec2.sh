#!/usr/bin/env bash
# Explicitly approved 2026-10-25 game-progress reset. Does not run setup/seed/npm ci.
set -Eeuo pipefail
umask 077
[ "$(id -u)" = 0 ] || { echo 'Run from ssm-user with sudo -n bash.'; exit 1; }
commit=${1:?Pinned deployment commit required}
[[ "$commit" =~ ^[0-9a-f]{40}$ ]]
[ "${2:-}" = 'RESET GAME PROGRESS KEEP ALL LOGIN IDENTITIES' ]
ack=$2
repo=/home/ubuntu/gmm
app=/home/ubuntu/gmm-releases/recruit-eb541ce-20261025T071013Z-265062/galmaegiman-confirmed
base=d562b5dbd1d626828adf1cbaa9f2e7f00b9ec7a4
policy=eb541cedf5c84887f26ce33250f5ae13e8cf25e8
db=/home/ubuntu/gmm/galmaegiman-confirmed/storage/confirmed.db
service=gmm-server.service
tunnel=gmm-named-tunnel.service
stamp=$(date -u +%Y%m%dT%H%M%SZ)-$$
backup=/home/ubuntu/gmm-deploy-backups/game-progress-reset-$stamp
stage=/home/ubuntu/gmm-releases/game-reset-build-$stamp
stopped=0; dbChanged=0; codeChanged=0; committed=0
exec 9>/run/lock/gmm-game-progress-reset.lock
flock -n 9 || { echo 'Another reset is running'; exit 1; }
healthy(){
 for i in $(seq 1 30); do
  if curl --max-time 3 -fsS http://127.0.0.1:3000/health 2>/dev/null | python3 -c 'import json,sys;sys.exit(json.load(sys.stdin).get("ok") is not True)' 2>/dev/null && curl --max-time 3 -fsS http://127.0.0.1:3000/ready 2>/dev/null | python3 -c 'import json,sys;sys.exit(json.load(sys.stdin).get("ready") is not True)' 2>/dev/null; then return 0; fi
  sleep 1
 done; return 1
}
space(){ python3 - "$1" <<'PY'
import shutil,sys
assert shutil.disk_usage('/home/ubuntu').free>=int(sys.argv[1]),'INSUFFICIENT_DISK_SPACE'
PY
}
rollback(){
 code=$?; [ "$code" -ne 0 ] || code=1; trap - ERR INT TERM; set +e
 echo "Reset stopped. Logs/backup: $backup"
 if [ "$committed" = 1 ]; then echo 'RESET ALREADY COMMITTED AND VERIFIED. Ingress restart needs attention; do not rerun reset. No DB rollback after reopening ingress.'; exit "$code"; fi
 if [ "$stopped" = 1 ]; then
  if ! systemctl stop "$tunnel" "$service"; then echo 'Cannot confirm services stopped; do not restore database manually while running.'; exit "$code"; fi
  ok=1
  if [ "$dbChanged" = 1 ]; then python3 "$stage/scripts/manual/game-progress-reset/db-ops.py" restore "$db" "$backup/before.db" || ok=0; fi
  if [ "$codeChanged" = 1 ]; then
   python3 - "$app" "$backup" <<'PY'
import pathlib,json,shutil
app,backup=map(pathlib.Path,__import__('sys').argv[1:])
for rel,present in json.loads((backup/'code-files.json').read_text()).items():
 p=app/rel
 if present:shutil.copy2(backup/'code'/rel,p)
 else:
  if p.exists():p.unlink()
PY
   [ "$?" = 0 ] || ok=0
  fi
  if [ "$ok" = 1 ] && systemctl start "$service" && healthy; then
   systemctl start "$tunnel" && echo 'Rollback/previous service restored; login data preserved.'
  else echo 'ROLLBACK VERIFICATION FAILED: services left without public ingress; keep backup and logs.'; systemctl stop "$tunnel"; fi
 fi
 exit "$code"
}
trap rollback ERR INT TERM
[ "$(systemctl show "$service" -p WorkingDirectory --value)" = "$app" ]
systemctl is-active --quiet "$service"
systemctl is-active --quiet "$tunnel"
python3 - "$app" "$db" <<'RUNTIME'
import sys,pathlib,subprocess
pid=subprocess.check_output(['systemctl','show','gmm-server.service','-p','MainPID','--value'],text=True).strip()
assert pathlib.Path('/proc/'+pid+'/cwd').resolve()==pathlib.Path(sys.argv[1]),'ACTUAL_RUNTIME_CHANGED'
env=dict(x.split(b'=',1) for x in pathlib.Path('/proc/'+pid+'/environ').read_bytes().split(b'\0') if b'=' in x)
assert env.get(b'DATABASE_URL')==('file:'+sys.argv[2]).encode(),'RUNTIME_DATABASE_CHANGED'
assert env.get(b'ROSTER60_PROFILE')==b'five-stat-device-test-v1','RUNTIME_PROFILE_CHANGED'
RUNTIME
[ -f "$db" ] && [ ! -L "$db" ]
space 536870912
install -d -m 700 -o ubuntu -g ubuntu "$backup" "$stage"
runuser -u ubuntu -- git -C "$repo" fetch origin manual/home-assembly-20261019
runuser -u ubuntu -- git -C "$repo" merge-base --is-ancestor "$commit" FETCH_HEAD
# Verify this script itself matches the pinned reviewed commit.
runuser -u ubuntu -- git -C "$repo" show "$commit:galmaegiman-confirmed/scripts/manual/game-progress-reset/deploy-ec2.sh" > "$backup/pinned-deploy.sh"
cmp -s "$0" "$backup/pinned-deploy.sh"
runuser -u ubuntu -- python3 - "$repo" "$app" "$stage" "$base" "$policy" "$commit" <<'PREP'
import pathlib,subprocess,shutil,sys,hashlib
repo,app,stage,base,policy,commit=sys.argv[1:];app=pathlib.Path(app);stage=pathlib.Path(stage)
def blob(ref,f):return subprocess.check_output(['git','-C',repo,'show',ref+':galmaegiman-confirmed/'+f])
for f in ['src/app.ts','src/my.ts','src/server.ts','package.json','package-lock.json','prisma/schema.prisma','tsconfig.json']:
 assert (app/f).read_bytes()==blob(base,f),'BASE_CHANGED:'+f
for f in ['data/roster60-recruit-policy.json','src/roster60-recruit-session.ts','src/roster60-library.ts']:
 assert (app/f).read_bytes()==blob(policy,f),'POLICY_CHANGED:'+f
assert not (app/'src/game-reset-session.ts').exists(),'ALREADY_PATCHED'
parts=['src','scripts','test','prisma','data','design','package.json','package-lock.json','tsconfig.json','vitest.config.ts']
size=sum(f.stat().st_size for part in parts for f in ([app/part] if (app/part).is_file() else (app/part).rglob('*')) if f.is_file())
assert size<128*1024*1024,'SOURCE_COPY_TOO_LARGE'
for part in parts:
 src=app/part
 if src.is_dir():shutil.copytree(src,stage/part)
 else:shutil.copy2(src,stage/part)
for part in ['node_modules','public','app']:
 assert (app/part).is_dir(),'REQUIRED_BUILD_PATH_MISSING:'+part
 (stage/part).symlink_to((app/part).resolve())
files=['src/app.ts','src/my.ts','src/server.ts','src/game-reset-session.ts','test/game-progress-reset.test.ts','test/game-reset-session.test.ts']
files+=subprocess.check_output(['git','-C',repo,'ls-tree','-r','--name-only',commit,'galmaegiman-confirmed/scripts/manual/game-progress-reset'],text=True).splitlines()
for f in files:
 f=f.removeprefix('galmaegiman-confirmed/');dest=stage/f;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(blob(commit,f))
print('Bounded server staging bytes:',size,'; shared dependencies will not be installed or generated.')
PREP
chown ubuntu:ubuntu "$backup/pinned-deploy.sh"
echo 'Compile + isolated tests; running service remains unchanged.'
runuser -u ubuntu -- bash -c 'set -e; cd "$1"; unset TEST_DB_URL NODE_OPTIONS ROSTER60_PROFILE; export DATABASE_URL="file:$1/build-only.db" TOKIO_WORKER_THREADS=4; node node_modules/typescript/bin/tsc -p tsconfig.json > "$2/build.log" 2>&1; node node_modules/vitest/vitest.mjs run test/game-progress-reset.test.ts test/game-reset-session.test.ts test/review-auth.test.ts --maxWorkers=1 > "$2/test.log" 2>&1' _ "$stage" "$backup"
tail -12 "$backup/test.log"
space 268435456
[ "$(systemctl show "$service" -p WorkingDirectory --value)" = "$app" ]
# Keep ingress stopped until new state, code and read-only authenticated API checks succeed.
stopped=1
systemctl stop "$tunnel"
systemctl stop "$service"
python3 "$stage/scripts/manual/game-progress-reset/db-ops.py" holders "$db" "$backup/before.db"
runuser -u ubuntu -- bash -c 'cd "$1"; node --import tsx scripts/manual/game-progress-reset/production.ts plan > "$2/plan.json"' _ "$stage" "$backup"
cat "$backup/plan.json"
python3 - "$backup/plan.json" <<'PY'
import json,sys
p=json.load(open(sys.argv[1]));assert not p['blockers'],','.join(p['blockers']);assert not p['alreadyReset'],'RESET_ALREADY_APPLIED';assert p['initial']['credits']==200,'WELCOME_CHANGED'
PY
python3 "$stage/scripts/manual/game-progress-reset/db-ops.py" backup "$db" "$backup/before.db"
chown ubuntu:ubuntu "$backup/before.db" "$backup/restore-check.db"
fingerprint=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["fingerprint"])' "$backup/plan.json")
# Preserve exactly the source and generated modules being replaced, including absence of new files.
python3 - "$app" "$backup" <<'PY'
import pathlib,shutil,json,sys
app,backup=map(pathlib.Path,sys.argv[1:]);files={}
for prefix,ext in [('src/','.ts'),('dist/src/','.js')]:
 for name in ['app','my','server','game-reset-session']:
  rel=prefix+name+ext;p=app/rel;files[rel]=p.exists()
  if p.exists():
   assert p.is_file() and not p.is_symlink(),'CODE_PATH_CHANGED'
   q=backup/'code'/rel;q.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,q)
(backup/'code-files.json').write_text(json.dumps(files))
PY
codeChanged=1
for name in app my server game-reset-session; do
 install -o ubuntu -g ubuntu -m 600 "$stage/src/$name.ts" "$app/src/$name.ts"
 install -o ubuntu -g ubuntu -m 600 "$stage/dist/src/$name.js" "$app/dist/src/$name.js"
done
python3 "$stage/scripts/manual/game-progress-reset/db-ops.py" holders "$db" "$backup/before.db"
dbChanged=1
runuser -u ubuntu -- bash -c 'cd "$1"; node --import tsx scripts/manual/game-progress-reset/production.ts apply "$2/before.db" "$3" "$4" > "$2/apply.json"' _ "$stage" "$backup" "$fingerprint" "$ack"
cat "$backup/apply.json"
# Read-only SQLite connection; in-process HTTP injection only, no external requests or gameplay.
runuser -u ubuntu -- env ROSTER60_PROFILE=five-stat-device-test-v1 bash -c 'cd "$1"; node --env-file="$3/.env" --import tsx scripts/manual/game-progress-reset/verify-api.ts > "$2/api-check.log" 2>&1' _ "$stage" "$backup" "$app"
cat "$backup/api-check.log"
space 134217728
systemctl start "$service"
healthy
[ "$(systemctl show "$service" -p WorkingDirectory --value)" = "$app" ]
committed=1
printf 'reset-code=%s\nbackup=%s\n' "$commit" "$backup" > "$backup/completed.txt"
systemctl start "$tunnel"
systemctl is-active --quiet "$tunnel"
trap - ERR INT TERM
systemctl show "$service" -p ActiveState -p WorkingDirectory -p MainPID
echo "Game progress reset verified. Login identities preserved. Please log in again. Backup: $backup"
