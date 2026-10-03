export interface Choice { label: string; message: string }
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
  cards?: {intro?: string; items: CardEntry[]; outro?: string};
}
// imageId: 유닛 ID 또는 'unknown'(미발견 실루엣). buttons 최대 3개.
export interface CardEntry { imageId: string; title: string; description: string; buttons?: Choice[] }
export class GameError extends Error {}
export const choice = (label: string, message = label): Choice => ({label, message});
