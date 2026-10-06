import { defineConfig } from 'vitest/config';
// 테스트는 test/ 폴더의 원본만(빌드 결과 dist/ 안의 복사본은 제외)
export default defineConfig({test:{include:['test/**/*.test.ts']}});
