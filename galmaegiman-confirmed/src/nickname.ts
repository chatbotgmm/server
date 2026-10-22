/** Shared chatbot/native rules; the database remains the uniqueness authority. */
export function normalizeNickname(value: string): string {
  return value.normalize('NFKC').trim().replace(/\s+/g, ' ');
}
export function nicknameError(name: string): string | null {
  if (!/^[가-힣a-zA-Z0-9 ]{2,10}$/u.test(name)) return '닉네임은 2~10자, 한글·영문·숫자(띄어쓰기 가능)만 쓸 수 있어요.';
  const plain = name.replace(/\s+/g, '').toLowerCase();
  if (['관리자','운영자','운영진','운영팀','관리팀','개발자','공식','갈매미맨','admin','official','staff'].some(w => plain.includes(w)) || /^(gm|dev)\d*$/.test(plain))
    return `「${name}」은(는) 운영진과 헷갈릴 수 있어서 쓸 수 없어요.`;
  return null;
}
// Already-started tutorials and established accounts must never be forced to rename.
export function needsInitialNickname(p: {nickname: string | null; gameTutorial: number; gameSeen: string}): boolean {
  return !p.nickname && p.gameTutorial === 1 && !p.gameSeen.split(',').includes('tut');
}
