import {spawn} from 'node:child_process';
import {once} from 'node:events';
const env={...process.env,PORT:'3381',LOG_ENABLED:'true'};
const server=spawn(process.execPath,['--env-file=.env','dist/src/server.js'],{env,stdio:['ignore','pipe','pipe']});
let output='';
server.stderr.on('data',d=>{output+=d.toString();});
try{
  await new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>reject(new Error(`서버 시작 시간 초과: ${output.slice(-2000)}`)),10000);
    server.stdout.on('data',d=>{if(d.toString().includes('Server listening')){clearTimeout(timer);resolve();}});
    server.once('exit',code=>{clearTimeout(timer);reject(new Error(`서버 종료 ${code}: ${output.slice(-2000)}`));});
    server.once('error',reject);
  });
  const test=spawn(process.execPath,['scripts/smoke-local.mjs'],{env,stdio:'inherit'});
  const [code]=await once(test,'exit');
  if(code!==0)throw new Error('로컬 HTTP 검증 실패');
}finally{
  const stopped=once(server,'exit');
  server.kill('SIGTERM');
  await stopped;
}
