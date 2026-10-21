import {registerHooks} from 'node:module';
import {existsSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
// Node 24의 타입 제거로 저장소 TS를 실행한다. 컴파일러/스키마 검증 대체가 아니다.
registerHooks({resolve(specifier,context,nextResolve){
 if(specifier.endsWith('.js')&&specifier.startsWith('.')){
   const u=new URL(specifier.slice(0,-3)+'.ts',context.parentURL);
   if(existsSync(fileURLToPath(u)))return nextResolve(u.href,context);
 }
 return nextResolve(specifier,context);
}});
