// 단체방 챗봇 DB 처리: 유저 키 → 플레이어, 채팅방, 방 멤버, 방 공동 도감, 방 주간 순위, 선톡 횟수
import type { PrismaClient } from '@prisma/client';
import { DateTime } from 'luxon';
import type { GameService } from '../game.js';
import type { BotEnv } from './config.js';
import type { GroupRequest } from './request.js';
import type { ProactiveStore } from './proactive.js';
// 단체방에서 새로 만든 플레이어의 identity(1:1 채널 챗봇 identity와 겹치지 않음)
export const groupIdentity=(env:BotEnv,botUserKey:string)=>JSON.stringify(['KAKAO_GROUP',env,botUserKey]);
// 한국 시간 주(월요일 시작)의 월요일 날짜
export const kstWeek=(at:Date)=>DateTime.fromJSDate(at).setZone('Asia/Seoul').startOf('week').toISODate()!;
export class GroupStore {
  constructor(private readonly db:PrismaClient,private readonly game:GameService,private readonly clock:()=>Date=()=>new Date()){}
  // 1) 이 botUserKey로 본 적 있으면 그 플레이어
  // 2) 처음이면: 카카오 로그인 회원번호(appUserId)가 앱 카카오 로그인(KakaoLink)이나 1:1 챗봇 기록(ChatAppUser)과 같으면 그 계정으로 이어요.
  //    appUserId = 카카오 로그인 회원번호로 보는 것은 기존 웹 로그인(game.webKakaoPlayer)과 같은 전제예요. TODO(verify): 그룹 챗봇에서도 같은 값인지
  // 3) 아니면 새 플레이어(가입 보상 포함)
  async resolvePlayer(req:GroupRequest):Promise<{playerId:string;created:boolean}>{
    const now=this.clock();
    const known=await this.db.groupUserKey.findUnique({where:{env_kind_value:{env:req.env,kind:'botUserKey',value:req.botUserKey}}});
    let playerId=known?.playerId,created=false;
    if(playerId&&!await this.db.player.findUnique({where:{id:playerId},select:{id:true}})){
      // 탈퇴 등으로 플레이어가 없어진 키는 버리고 새로 시작
      await this.db.groupUserKey.deleteMany({where:{playerId}});playerId=undefined;
    }
    if(!playerId&&req.appUserId){
      playerId=(await this.db.kakaoLink.findUnique({where:{kakaoId:req.appUserId}}))?.playerId
        ??(await this.db.chatAppUser.findUnique({where:{appUserId:req.appUserId}}))?.playerId;
    }
    if(!playerId){
      const identity=groupIdentity(req.env,req.botUserKey);
      created=!await this.db.player.findUnique({where:{identity},select:{id:true}});
      playerId=await this.game.groupEnsurePlayer(identity);
    }
    const keys:[string,string|undefined][]=[['botUserKey',req.botUserKey],['plusfriendUserKey',req.plusfriendUserKey],['appUserId',req.appUserId]];
    for(const [kind,value] of keys){
      if(!value)continue;
      const where={env_kind_value:{env:req.env,kind,value}};
      const row=await this.db.groupUserKey.findUnique({where});
      if(!row)await this.db.groupUserKey.create({data:{env:req.env,kind,value,playerId,firstSeen:now,lastSeen:now}}).catch(()=>{});
      else if(row.playerId===playerId)await this.db.groupUserKey.update({where,data:{lastSeen:now}});
      // 같은 보조 키가 다른 플레이어에 묶여 있으면 바꾸지 않아요(주 키는 botUserKey)
    }
    return {playerId,created};
  }
  async room(env:BotEnv,groupKey:string){
    return (await this.db.groupRoom.upsert({where:{env_groupKey:{env,groupKey}},create:{env,groupKey,createdAt:this.clock()},update:{}})).id;
  }
  async touchMember(roomId:string,playerId:string){
    const now=this.clock();
    await this.db.roomMember.upsert({where:{roomId_playerId:{roomId,playerId}},create:{roomId,playerId,joinedAt:now,lastActiveAt:now},update:{lastActiveAt:now}});
  }
  async memberIds(roomId:string){return (await this.db.roomMember.findMany({where:{roomId},select:{playerId:true}})).map(m=>m.playerId);}
  // 방 공동 도감 = 방 멤버들의 도감을 합친 것
  async roomCollection(roomId:string){
    const members=await this.memberIds(roomId);
    const rows=await this.db.collectionEntry.findMany({where:{playerId:{in:members}},distinct:['characterId'],select:{characterId:true}});
    return {members:members.length,ids:new Set(rows.map(r=>r.characterId))};
  }
  // 이 사람이 방금 얻은 갈매미 중, 방의 다른 멤버 누구도 가진 적 없던 것을 '우리 방 첫 발견'으로 기록하고 돌려줘요
  async recordRoomFirsts(roomId:string,playerId:string,characterIds:string[]){
    if(!characterIds.length)return [];
    const others=(await this.memberIds(roomId)).filter(id=>id!==playerId);
    const had=new Set((await this.db.collectionEntry.findMany({where:{playerId:{in:others},characterId:{in:characterIds}},select:{characterId:true}})).map(r=>r.characterId));
    const recorded=new Set((await this.db.roomDex.findMany({where:{roomId,characterId:{in:characterIds}},select:{characterId:true}})).map(r=>r.characterId));
    const firsts:string[]=[];
    for(const characterId of new Set(characterIds)){
      if(had.has(characterId)||recorded.has(characterId))continue;
      try{await this.db.roomDex.create({data:{roomId,characterId,firstPlayerId:playerId,firstAt:this.clock()}});firsts.push(characterId);}catch{/* 동시에 다른 멤버가 먼저 기록 */}
    }
    return firsts;
  }
  async recentRoomFirsts(roomId:string,take=3){
    return this.db.roomDex.findMany({where:{roomId},orderBy:{firstAt:'desc'},take});
  }
  async bumpWeek(roomId:string,playerId:string,add:{finds?:number;crafts?:number;recruits?:number}){
    const week=kstWeek(this.clock()),inc=(n?:number)=>({increment:n??0});
    await this.db.roomWeek.upsert({where:{roomId_playerId_week:{roomId,playerId,week}},
      create:{roomId,playerId,week,finds:add.finds??0,crafts:add.crafts??0,recruits:add.recruits??0},
      update:{finds:inc(add.finds),crafts:inc(add.crafts),recruits:inc(add.recruits)}});
  }
  // 이번 주 방 순위: 새 발견 → 조합 → 모집 순
  async weekRanking(roomId:string,take=5){
    const week=kstWeek(this.clock());
    const rows=await this.db.roomWeek.findMany({where:{roomId,week},orderBy:[{finds:'desc'},{crafts:'desc'},{recruits:'desc'}],take});
    const names=await this.nicknames(rows.map(r=>r.playerId));
    return {week,rows:rows.map(r=>({...r,name:names.get(r.playerId)??null}))};
  }
  async nicknames(ids:string[]){
    const rows=await this.db.player.findMany({where:{id:{in:ids}},select:{id:true,nickname:true}});
    return new Map(rows.map(r=>[r.id,r.nickname]));
  }
}
// 선톡 횟수 DB 저장소(덱 42: 월 2회). 세기와 기록을 한 거래로
export class PrismaProactiveStore implements ProactiveStore {
  constructor(private readonly db:PrismaClient){}
  async reserve(env:BotEnv,month:string,max:number,summary:string,at:Date){
    return this.db.$transaction(async tx=>{
      if(await tx.proactiveLog.count({where:{env,month}})>=max)return false;
      await tx.proactiveLog.create({data:{env,month,summary,sentAt:at}});return true;
    });
  }
}
