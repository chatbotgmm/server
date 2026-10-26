// 읽기 전용 명단 추출. 배포 호스트에서 검증된 테스트 DB 연결로만 실행한다.
import {writeFileSync} from 'node:fs';
const [output,confirm,...extra]=process.argv.slice(2);
if(!output||confirm!=='--confirm-test-server-all-players'||extra.length||process.env.ROSTER60_PROFILE!=='five-stat-device-test-v1'||!process.env.DATABASE_URL){
 console.error('테스트 프로필·검증된 DATABASE_URL·출력 파일·--confirm-test-server-all-players 필요');process.exit(1);
}
let db;
try{
 const {PrismaClient}=await import('@prisma/client');db=new PrismaClient();
 const rows=await db.player.findMany({select:{id:true},orderBy:{id:'asc'}});
 if(!rows.length)throw Error('empty');
 writeFileSync(output,JSON.stringify(rows.map(r=>r.id),null,2)+'\n',{flag:'wx',mode:0o600});
 console.log(JSON.stringify({accounts:rows.length,scope:'confirmed-test-server-all-players',applied:false}));
}catch{console.error('명단 추출 실패: 연결·프로필·출력 파일을 확인하세요. DB 변경 없음.');process.exitCode=1;}
finally{await db?.$disconnect();}
