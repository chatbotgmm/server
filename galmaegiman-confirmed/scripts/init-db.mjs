import {existsSync,openSync,closeSync,mkdirSync} from 'node:fs';
mkdirSync('storage',{recursive:true});
if(!existsSync('storage/confirmed.db'))closeSync(openSync('storage/confirmed.db','wx',0o600));
