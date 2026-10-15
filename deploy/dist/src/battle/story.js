// 스토리 모드 데이터(data/story.yaml) 검사. 서버 전용(zod). 앱은 서버가 검사한 결과를 받음
// 줄거리 근거: design/STORY-BIBLE.md
import { readFileSync } from 'node:fs';
import { parse } from 'yaml';
import { z } from 'zod';
// who: N = 해설, ME = 내 선두 갈매미, V1 같은 빌런 번호 = 그 빌런, D2·N1 같은 유닛 번호 = 그 갈매미(그림과 이름), 그 밖의 글자 = 그 이름 그대로(예: 마을 회관 방송)
// 대사 화면 배경: harbor 항구(맑음) / ink 먹물 항구 / deep 바다 밑 동굴 / hall 마을 회관 / market 금빛 시장 / strait 해협 / laundry 빨래터 언덕 / sky 끼룩단 하늘 / nest 끼룩단 섬
export const scenes = ['harbor', 'ink', 'deep', 'hall', 'market', 'strait', 'laundry', 'sky', 'nest'];
const line = z.object({ who: z.string().min(1).max(20), say: z.string().min(1).max(200) }).strict();
const stage = z.object({
    id: z.string().regex(/^[A-Z0-9]+-\d+$/), title: z.string().min(1).max(30),
    scene: z.enum(scenes).optional(),
    before: z.array(line).min(1).max(30),
    hint: z.string().max(120).optional(),
    // stage: 상대의 승진 단계(없으면 그 장의 needStage). 승진한 내 팀과 맞추려고 씀
    enemy: z.array(z.object({ id: z.string().regex(/^V\d+$/), level: z.number().int().min(1).max(60), stage: z.number().int().min(0).max(3).optional() }).strict()).min(1).max(3),
    after: z.array(line).min(1).max(30),
    lose: z.array(line).max(10).optional(),
}).strict();
export const storySchema = z.object({
    version: z.literal('STORY_V1'),
    start: z.object({ level: z.number().int().min(1).max(60) }).strict(),
    chapters: z.array(z.object({
        id: z.string().regex(/^[A-Z0-9]+$/), title: z.string().min(1).max(30), intro: z.string().max(200).optional(),
        needStage: z.number().int().min(0).max(3).optional(), // 이 장에 들어가려면 팀 전원이 이 승진 단계 이상
        stages: z.array(stage).min(1),
        clear: z.array(line).max(10).optional(),
    }).strict()).min(1),
}).strict();
export function checkStory(raw, R) {
    const s = storySchema.parse(raw);
    const ids = new Set();
    for (const c of s.chapters)
        for (const st of c.stages) {
            if (ids.has(st.id))
                throw new Error(`스테이지 번호가 겹쳐요: ${st.id}`);
            ids.add(st.id);
            for (const e of st.enemy)
                if (!R.villains.has(e.id))
                    throw new Error(`${st.id}: battle.yaml villains에 없는 빌런 ${e.id}`);
            for (const l of [...st.before, ...st.after, ...(st.lose ?? [])])
                if (/^V\d+$/.test(l.who) && !R.villains.has(l.who))
                    throw new Error(`${st.id}: 대사의 빌런 ${l.who}가 battle.yaml villains에 없어요`);
            for (const l of [...st.before, ...st.after, ...(st.lose ?? [])])
                if (/^[DTEI]\d+$/.test(l.who) && !R.units.has(l.who))
                    throw new Error(`${st.id}: 대사의 유닛 ${l.who}가 없어요`);
        }
    return s;
}
export function loadStory(root, R) {
    let raw;
    try {
        raw = parse(readFileSync(`${root}/data/story.yaml`, 'utf8'));
    }
    catch (e) {
        throw new Error(`data/story.yaml을 읽지 못했어요(들여쓰기·콜론 확인):\n  - ${e.message}`);
    }
    try {
        return checkStory(raw, R);
    }
    catch (e) {
        if (e instanceof z.ZodError)
            throw new Error(`data/story.yaml 오류:\n${e.issues.slice(0, 8).map(i => `  - ${i.path.join('.')}: ${i.message}`).join('\n')}`);
        throw new Error(`data/story.yaml 오류:\n  - ${e.message}`);
    }
}
