import {describe,it,expect} from 'vitest';
import {parseKakao,kakaoResponse} from '../src/kakao.js';
const payload={bot:{id:'bot'},userRequest:{utterance:'뽑기',lang:null,user:{id:'user',type:'accountId',properties:{isFriend:true,nickname:null}}},action:{clientExtra:null}};
describe('카카오 공식 요청·응답',()=>{
  it('isFriend boolean/null 선택 필드 허용',()=>expect(parseKakao(payload).message).toBe('뽑기'));
  it('다른 봇 사용자 분리',()=>expect(parseKakao(payload).identity).not.toBe(parseKakao({...payload,bot:{id:'another'}}).identity));
  it('테스트 계정과 실제 계정 식별형 분리',()=>expect(parseKakao(payload).identity).not.toBe(parseKakao({...payload,userRequest:{...payload.userRequest,user:{id:'user',type:'botUserKey'}}}).identity));
  it('설정된 봇만 허용',()=>expect(()=>parseKakao(payload,'other')).toThrow());
  it('필수 발화 누락 거절',()=>expect(()=>parseKakao({bot:{id:'bot'},userRequest:{user:{id:'1'}}})).toThrow());
  it('이미지+긴 텍스트도 outputs3 이하',()=>{
    const response=kakaoResponse({text:'가'.repeat(1800),imageId:'C1',choices:[{label:'확정',message:'확정 1234567890abcdef'}]},'https://example.com');
    expect(response.version).toBe('2.0');expect(response.template.outputs).toHaveLength(3);
    expect(response.template.outputs[0]).toEqual({simpleImage:{imageUrl:'https://example.com/images/C1.png',altText:'갈매미맨'}});
    expect(response.template.quickReplies[0].messageText).toBe('확정 1234567890abcdef');
  });
  it('공개 HTTPS 미설정 시 이미지 URL을 조작해서 만들지 않음',()=>expect(kakaoResponse({text:'가',imageId:'C1'}).template.outputs).toEqual([{simpleText:{text:'가'}}]));
  it('이미지 대체 텍스트에도 캐릭터 이름 표시',()=>{
    const response=kakaoResponse({text:'암흑 갈매미맨 등장!',imageId:'C3',imageName:'암흑 갈매미맨'},'https://example.com');
    expect(response.template.outputs[0]).toEqual({simpleImage:{imageUrl:'https://example.com/images/C3.png',altText:'암흑 갈매미맨'}});
  });
});
