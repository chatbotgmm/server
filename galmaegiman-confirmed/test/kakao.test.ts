import {describe,it,expect} from 'vitest';
import {parseKakao,kakaoResponse,cardCost,cardsPerPage,CAROUSEL_TEXT_BUDGET} from '../src/kakao.js';
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
  it('여러 번 뽑기: 카드 넘기기 → 요약 말풍선 1개(머리말+요약), 전설 연출(keepIntro)만 카드 앞에, 이미지 주소가 없으면 글로만',()=>{
    const reply={text:'전체 글',imageId:'L2',cards:{intro:'🥚 고급 뽑기 ×2',outro:'🎟 3장',items:[
      {imageId:'L2',title:'★ 쌍패성왕',description:'전설 · 🆕 새 발견!',buttons:[{label:'도감 보기',message:'도감 쌍패성왕'}]},
      {imageId:'S1',title:'✦ 쌍패수호자 ×2',description:'특별'}]}};
    const out=kakaoResponse(reply,'https://example.com').template.outputs as any[];
    expect(out).toHaveLength(2);
    expect(out[0].carousel.type).toBe('basicCard');
    expect(out[1]).toEqual({simpleText:{text:'🥚 고급 뽑기 ×2\n\n🎟 3장'}});
    const drama=kakaoResponse({...reply,cards:{...reply.cards,intro:'……하늘이 어두워진다.',keepIntro:true}},'https://example.com').template.outputs as any[];
    expect(drama.map(o=>Object.keys(o)[0])).toEqual(['simpleText','carousel','simpleText']);
    expect(drama[0]).toEqual({simpleText:{text:'……하늘이 어두워진다.'}});
    expect(out[0].carousel.items[0]).toEqual({title:'★ 쌍패성왕',description:'전설 · 🆕 새 발견!',
      thumbnail:{imageUrl:'https://example.com/thumbs/L2.jpg',altText:'★ 쌍패성왕',fixedRatio:true},buttons:[{action:'message',label:'도감 보기',messageText:'도감 쌍패성왕'}]});
    expect(kakaoResponse(reply).template.outputs).toEqual([{simpleText:{text:'전체 글'}}]);
  });
  it('카드 1장은 큰 카드, 아래 글은 카드 설명에 합침(230자 안), 길면 말풍선 1개, 확인 목록은 꼭 넣음',()=>{
    const one=kakaoResponse({text:'글',cards:{items:[{imageId:'unknown',title:'???',description:'미발견'}],outro:'아래\n\n──────────\n📌 지금'}},'https://e.com').template.outputs as any[];
    expect(one).toHaveLength(1);
    expect(one[0].basicCard.thumbnail.imageUrl).toBe('https://e.com/thumbs/unknown.jpg');
    expect(one[0].basicCard.description).toBe('미발견\n아래\n📌 지금');
    const long=kakaoResponse({text:'글',cards:{items:[{imageId:'unknown',title:'???',description:'미발견'}],outro:'가'.repeat(240)}},'https://e.com').template.outputs as any[];
    expect(long.map(o=>Object.keys(o)[0])).toEqual(['basicCard','simpleText']);expect(long[0].basicCard.description).toBe('미발견');
    const withList=kakaoResponse({text:'글',cards:{intro:'머리',items:[{imageId:'U1',title:'금갑',description:'재료'}],outro:'재료 목록'},
      list:{title:'만들까요?',showText:true,items:[{title:'조합하기',description:'d',message:'조합 확정',button:{action:'confirm',token:'0123456789abcdef'}}]}},'https://e.com').template.outputs as any[];
    expect(withList.map(o=>Object.keys(o)[0])).toEqual(['basicCard','listCard']);
    expect(withList[0].basicCard.description).toBe('머리\n재료\n재료 목록');
    expect(withList[1].listCard.items[0].extra).toEqual({gmAction:'confirm',gmToken:'0123456789abcdef'});
  });
  it('카드 넘기기 안의 버튼은 2개까지, 그림 주소에 버전 값을 붙임',()=>{
    const b=[{label:'a',message:'a'},{label:'b',message:'b'},{label:'c',message:'c'}];
    const out=kakaoResponse({text:'t',cards:{items:[{imageId:'C1',title:'x',description:'d',buttons:b},{imageId:'C2',title:'y',description:'d',buttons:b}]}},'https://e.com',p=>p.length.toString()).template.outputs as any[];
    expect(out[0].carousel.items[0].buttons).toHaveLength(2);
    expect(out[0].carousel.items[0].thumbnail.imageUrl).toBe('https://e.com/thumbs/C1.jpg?v=13');
    const one=kakaoResponse({text:'t',cards:{items:[{imageId:'C1',title:'x',description:'d',buttons:b}]}},'https://e.com').template.outputs as any[];
    expect(one[0].basicCard.buttons).toHaveLength(3);
    expect(one[0].basicCard.thumbnail.imageUrl).toBe('https://e.com/thumbs/C1.jpg');
  });
  it('카드 넘기기 글자 합계가 예산(1150바이트)을 넘으면 뒤 카드부터 두 번째 버튼 → 설명 둘째 줄 → 버튼을 뺌, 카드는 그대로',()=>{
    const long='합치기 '+'가'.repeat(12);// 40바이트
    const items=Array.from({length:10},(_,i)=>({imageId:'C1',title:`카드${i}`,description:'첫 줄\n둘째 줄',buttons:[{label:'a',message:long},{label:'b',message:long}]}));
    const out=(kakaoResponse({text:'t',cards:{items}},'https://e.com').template.outputs as any[]).find(o=>o.carousel).carousel.items;
    expect(out).toHaveLength(10);
    const cost=out.reduce((s:number,c:any)=>s+cardCost({imageId:'C1',title:c.title,description:c.description,buttons:(c.buttons??[]).map((b:any)=>({label:b.label,message:b.messageText}))}),0);
    expect(cost).toBeLessThanOrEqual(CAROUSEL_TEXT_BUDGET);
    expect(out[0].buttons).toHaveLength(2);// 앞 카드는 그대로
    expect(out[9].buttons).toHaveLength(1);
    // 예산 안이면 건드리지 않음
    const short=Array.from({length:10},(_,i)=>({imageId:'C1',title:`카드${i}`,description:'d',buttons:[{label:'a',message:'도감'},{label:'b',message:'조합'}]}));
    const kept=(kakaoResponse({text:'t',cards:{items:short}},'https://e.com').template.outputs as any[]).find(o=>o.carousel).carousel.items;
    expect(kept.every((c:any)=>c.buttons.length===2)).toBe(true);
  });
  it('쪽당 카드 수: 가장 무거운 카드 기준으로 예산 안에 드는 만큼(1~10)',()=>{
    const card=(n:number)=>({imageId:'C1',title:'가'.repeat(n),description:'d'});
    expect(cardsPerPage([card(1)])).toBe(10);
    expect(cardsPerPage([card(1),card(50)])).toBe(Math.floor(CAROUSEL_TEXT_BUDGET/(150*2+1)));
  });
});
