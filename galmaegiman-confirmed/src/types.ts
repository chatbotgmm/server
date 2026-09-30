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
}
export class GameError extends Error {}
export const choice = (label: string, message = label): Choice => ({label, message});
