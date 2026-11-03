// 외부 랜딩(덱 6·39·44·45): 응답만으로 충분한 정보를 준 뒤에만 링크를 붙이고,
// 앱으로 보낼 때는 설치된 경우(응답과 연관된 앱 안 페이지)와 미설치(앱스토어)를 둘 다 받아요.
// TODO(verify): 설치 여부에 따라 나뉘는 링크를 버튼 JSON으로 만드는 방법(OS별 링크 지원 여부)은 그룹챗봇 개발 가이드 확인 후 추가
export class LandingError extends Error {constructor(message:string){super(message);this.name='LandingError';}}
export interface AppLanding {summary:string;installed:string;notInstalled:string}
const MIN_SUMMARY=10; // [우리] 링크만 던지지 않도록 요구하는 최소 안내 글자 수
export function appLanding(input:{summary:string;installed:string;notInstalled:string}):AppLanding{
  const summary=input.summary.trim();
  if([...summary].length<MIN_SUMMARY)throw new LandingError('링크 앞에 응답만으로 이해되는 안내가 필요해요');
  let installed:URL,store:URL;
  try{installed=new URL(input.installed);store=new URL(input.notInstalled);}catch{throw new LandingError('링크 형식 오류');}
  if(store.protocol!=='https:')throw new LandingError('앱스토어 링크는 https만');
  // 설치된 경우의 링크는 응답과 연관된 앱 안 페이지여야 해요(어느 페이지인지는 부르는 쪽 책임). 위험한 형식만 막아요
  if(['http:','javascript:','data:','file:'].includes(installed.protocol))throw new LandingError('앱 링크 형식 오류');
  return {summary,installed:installed.href,notInstalled:store.href};
}
