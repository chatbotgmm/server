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
  it('여러 번 뽑기: 연출 문구 → 카드 넘기기 → 요약, 이미지 주소가 없으면 글로만',()=>{
    const reply={text:'전체 글',imageId:'L2',cards:{intro:'🥚 고급 뽑기 ×2',outro:'🎟 3장',items:[
      {imageId:'L2',title:'★ 쌍패성왕',description:'전설 · 🆕 새 발견!',buttons:[{label:'도감 보기',message:'도감 쌍패성왕'}]},
      {imageId:'S1',title:'✦ 쌍패수호자 ×2',description:'특별'}]}};
    const out=kakaoResponse(reply,'https://example.com').template.outputs as any[];
    expect(out).toHaveLength(3);
    expect(out[0]).toEqual({simpleText:{text:'🥚 고급 뽑기 ×2'}});
    expect(out[1].carousel.type).toBe('basicCard');
    expect(out[1].carousel.items[0]).toEqual({title:'★ 쌍패성왕',description:'전설 · 🆕 새 발견!',
      thumbnail:{imageUrl:'https://example.com/thumbs/L2.jpg',altText:'★ 쌍패성왕',fixedRatio:true},buttons:[{action:'message',label:'도감 보기',messageText:'도감 쌍패성왕'}]});
    expect(out[2]).toEqual({simpleText:{text:'🎟 3장'}});
    expect(kakaoResponse(reply).template.outputs).toEqual([{simpleText:{text:'전체 글'}}]);
  });
  it('카드 1장은 큰 카드, 확인 목록이 있으면 말풍선 3개 안에서 목록을 꼭 넣음',()=>{
    const one=kakaoResponse({text:'글',cards:{items:[{imageId:'unknown',title:'???',description:'미발견'}],outro:'아래'}},'https://e.com').template.outputs as any[];
    expect(one[0].basicCard.thumbnail.imageUrl).toBe('https://e.com/thumbs/unknown.jpg');
    expect(one[1]).toEqual({simpleText:{text:'아래'}});
    const withList=kakaoResponse({text:'글',cards:{intro:'머리',items:[{imageId:'U1',title:'금갑',description:'재료'}],outro:'재료 목록'},
      list:{title:'만들까요?',showText:true,items:[{title:'조합하기',description:'d',message:'조합 확정',button:{action:'confirm',token:'0123456789abcdef'}}]}},'https://e.com').template.outputs as any[];
    expect(withList).toHaveLength(3);
    expect(withList.map(o=>Object.keys(o)[0])).toEqual(['basicCard','simpleText','listCard']);
    expect(withList[2].listCard.items[0].extra).toEqual({gmAction:'confirm',gmToken:'0123456789abcdef'});
  });
});
