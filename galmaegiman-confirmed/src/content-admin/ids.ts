import {randomUUID} from 'node:crypto';

/** 서버가 만든 UUID v4 만 이미지·공지 식별자로 받는다. */
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export function isMediaId(value: unknown): value is string {
  return typeof value === 'string' && UUID_V4.test(value);
}

export function newMediaId(): string {
  return randomUUID();
}
