// 단체방 챗봇 설정(data/group-bot.yaml)과 실행 환경(BOT_ENV)
import { readFileSync } from 'node:fs';
import { parse } from 'yaml';
import { z } from 'zod';
const positive=z.number().int().positive();
export const banReasons=['AD','PURCHASE','MISLEADING','GAMBLING','HARMFUL'] as const;
const schema=z.object({
  version:z.literal('GROUP_BOT_V1'),
  limits:z.object({outputs:positive.max(3),textChars:positive.max(1000),textLines:positive,listItems:positive.max(5),itemCardItems:positive.max(10),
    quickReplies:positive.max(10),buttonLabel:positive.max(14),welcomeChars:positive,welcomeLines:positive}).strict(),
  mentions:z.object({perBubble:positive,maxBubbles:positive}).strict(),
  proactive:z.object({monthlyMax:positive,maxLines:positive}).strict(),
  banned:z.array(z.object({phrase:z.string().trim().min(1),reason:z.enum(banReasons)}).strict()).min(1),
}).strict();
export type GroupBotConfig=z.infer<typeof schema>;
export type BanReason=typeof banReasons[number];
export function loadGroupBotConfig(root=process.cwd()):GroupBotConfig{
  return schema.parse(parse(readFileSync(`${root}/data/group-bot.yaml`,'utf8')));
}
// 개발채널·운영채널은 서로 다른 채널이므로 키 공간·DB·로그를 나눠요(덱 5·14·21·35). 값이 없거나 틀리면 시작하지 않아요.
export type BotEnv='dev'|'prod';
export function botEnv(value=process.env.BOT_ENV):BotEnv{
  if(value==='dev'||value==='prod')return value;
  throw new Error('BOT_ENV를 dev 또는 prod로 설정하세요.');
}
