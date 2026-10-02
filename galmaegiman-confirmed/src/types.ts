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
  // 카드 넘기기(캐러셀). 이미지 주소를 만들 수 있을 때만 쓰고, 아니면 text로 응답합니다.
  cards?: {intro: string; items: CardEntry[]; outro: string};
}
export interface CardEntry { imageId: string; title: string; description: string; message: string; label: string }
export class GameError extends Error {}
export const choice = (label: string, message = label): Choice => ({label, message});
