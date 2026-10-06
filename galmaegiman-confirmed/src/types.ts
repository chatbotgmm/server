// kind: 'share' = 카카오 말풍선 공유 버튼(카드 버튼에서만 동작, 바로가기에는 못 씀)
export interface Choice { label: string; message: string; kind?: 'share' }
export interface ButtonAction { action: 'confirm'|'cancel'; token: string }
export interface ListEntry {
  title: string;
  description: string;
  message: string;
  imageId?: string;
  button?: ButtonAction;
}
export interface GameReply {
  text: string;
  imageId?: string;
  imageName?: string;
  choices?: Choice[];
  list?: {title: string; items: ListEntry[]; showText?: boolean};
  // 그림 카드. 1장이면 큰 카드, 여러 장이면 카드 넘기기(캐러셀).
  // 이미지 주소를 만들 수 있을 때만 쓰고, 아니면 text로 응답합니다.
  // keepIntro: intro를 카드 앞 말풍선으로 꼭 남김(전설 이상 등장 연출). 그 밖의 intro·outro는 카드 설명에 합치거나 말풍선 1개로 줄입니다.
  cards?: {intro?: string; items: CardEntry[]; outro?: string; keepIntro?: boolean};
}
// imageId: 유닛 ID 또는 'unknown'(미발견 실루엣). buttons 최대 3개.
export interface CardEntry { imageId: string; title: string; description: string; buttons?: Choice[] }
export class GameError extends Error {}
export const choice = (label: string, message = label): Choice => ({label, message});
export const shareChoice = (label = '📤 공유하기'): Choice => ({label, message: '', kind: 'share'});
