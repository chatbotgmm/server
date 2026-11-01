import {createHash} from 'node:crypto';
import {ContentError} from './errors.js';
import type {ContentStore} from './store.js';

const REQUEST_ID = /^[A-Za-z0-9_-]{8,80}$/;

export function requireRequestId(value: unknown): string {
  if (typeof value !== 'string' || !REQUEST_ID.test(value)) throw new ContentError(400, 'REQUEST_ID_REQUIRED');
  return value;
}

export function payloadHash(operation: string, payload: unknown): string {
  return createHash('sha256').update(operation).update('\u0000').update(JSON.stringify(payload)).digest('hex');
}

/**
 * 같은 사용자·같은 requestId 의 재전송은 첫 결과를 그대로 돌려준다.
 * 같은 requestId 로 다른 내용을 보내면 409. 실패한 요청은 기록하지 않아 다시 시도할 수 있다.
 * 반드시 SerialQueue 안에서 부른다.
 */
export async function once<T>(store: ContentStore, actorId: string, requestId: string, operation: string, payload: unknown, run: () => Promise<{status: number; body: T}>, now: () => Date): Promise<{status: number; body: T; replayed: boolean}> {
  const hash = payloadHash(operation, payload);
  const previous = await store.findRequest(actorId, requestId);
  if (previous) {
    if (previous.operation !== operation || previous.payloadHash !== hash) throw new ContentError(409, 'REQUEST_ID_REUSED');
    return {status: previous.status, body: JSON.parse(previous.response) as T, replayed: true};
  }
  const result = await run();
  await store.saveRequest({actorId, requestId, operation, payloadHash: hash, response: JSON.stringify(result.body), status: result.status, createdAt: now()});
  return {...result, replayed: false};
}
