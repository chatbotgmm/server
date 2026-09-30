import { randomBytes } from 'node:crypto';
import { existsSync,writeFileSync,readFileSync,chmodSync } from 'node:fs';
if(!existsSync('.env')){
  let text=readFileSync('.env.example','utf8');
  text=text.replace(/^SKILL_SECRET=.*$/m,`SKILL_SECRET=${randomBytes(32).toString('hex')}`);
  writeFileSync('.env',text,{flag:'wx',mode:0o600});
}
chmodSync('.env',0o600);
