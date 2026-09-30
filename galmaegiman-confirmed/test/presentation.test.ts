import {describe,it,expect} from 'vitest';
import {loadContent} from '../src/content.js';
import {matchCharacters,shortName} from '../src/presentation.js';
import {kakaoResponse,parseKakao} from '../src/kakao.js';
const content=loadContent();
describe('이름 선택과 카카오 목록',()=>{
  it('75종 전체 이름·짧은 이름·띄어쓰기 차이로 같은 종류 찾기',()=>{
    for(const c of content.characters){
      for(const input of [c.name,shortName(c.name),c.name.replaceAll(' ',''),c.name.split('').join(' ')]){
        expect(matchCharacters(content.characters,input).map(x=>x.id)).toEqual([c.id]);
      }
    }
  });
  it('짧은 이름이 겹치면 임의 선택하지 않고 후보 반환',()=>{
    const matches=matchCharacters(content.characters,'새우깡');
    expect(matches.length).toBeGreaterThan(1);
    expect(matchCharacters(content.characters,'비닐봉지')[0].id).toBe('C003F');
    expect(matchCharacters(content.characters,'존재하지않음')).toEqual([]);
    expect(matchCharacters(content.characters,'')).toEqual([]);
  });
  it('기존 ID 입력도 호환',()=>expect(matchCharacters(content.characters,'u001m')[0].name).toBe('브라자 갈매미맨'));
  it('목록은 이름으로 선택하며 중복 텍스트·내부번호 노출 없음',()=>{
    const response=kakaoResponse({text:'중복 표시하지 않을 본문',list:{title:'조합목록 · 1 / 14',items:[{title:'브라자 갈매미맨',description:'안흔함 · 조합 가능',message:'조합 브라자 갈매미맨',imageId:'U001M'}]}},'https://example.com');
    expect(response.template.outputs).toEqual([{listCard:{header:{title:'조합목록 · 1 / 14'},items:[{title:'브라자 갈매미맨',description:'안흔함 · 조합 가능',action:'message',messageText:'조합 브라자 갈매미맨',imageUrl:'https://example.com/images/U001M.png'}]}}]);
  });
  it('확인 토큰은 ListItem.extra에서 action.clientExtra로 전달',()=>{
    const token='1234567890abcdef';
    const response=kakaoResponse({text:'재료 2마리를 사용합니다.',imageId:'U001M',imageName:'브라자 갈매미맨',list:{title:'진행할까요?',showText:true,items:[{title:'조합하기',description:'내용 확인',message:'조합 확정',button:{action:'confirm',token}}]}},'https://example.com');
    expect(response.template.outputs).toHaveLength(3);
    const list=response.template.outputs[2] as {listCard:{items:{messageText:string;extra:object}[]}};
    const item=list.listCard.items[0];
    expect(item.messageText).toBe('조합 확정');
    const input=parseKakao({bot:{id:'bot'},userRequest:{utterance:item.messageText,user:{id:'user'}},action:{clientExtra:item.extra}});
    expect(input.button).toEqual({action:'confirm',token});
    expect(JSON.stringify(response.template.outputs.slice(0,2))).not.toContain(token);
  });
  it('다른 clientExtra는 허용하고 잘못된 확인 토큰은 거부',()=>{
    const base={bot:{id:'bot'},userRequest:{utterance:'내갈매미',user:{id:'user'}}};
    expect(parseKakao({...base,action:{clientExtra:{unrelated:true}}}).button).toBeUndefined();
    expect(()=>parseKakao({...base,action:{clientExtra:{gmAction:'confirm',gmToken:'wrong'}}})).toThrow();
  });
  it('카카오 목록 크기 제한 준수',()=>{
    expect(()=>kakaoResponse({text:'',list:{title:'빈 목록',items:[]}})).toThrow();
    expect(()=>kakaoResponse({text:'',list:{title:'긴 목록',items:Array.from({length:6},()=>({title:'이름',description:'설명',message:'도감'}))}})).toThrow();
  });
});
