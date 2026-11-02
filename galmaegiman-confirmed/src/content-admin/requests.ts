import {createHash} from 'node:crypto';
import {ContentError} from './errors.js';
import {TransientStoreError, type ContentStore, type ContentTx} from './store.js';

const REQUEST_ID = /^[A-Za-z0-9_-]{8,80}$/;

export function requireRequestId(value: unknown): string {
  if (typeof value !== 'string' || !REQUEST_ID.test(value)) throw new ContentError(400, 'REQUEST_ID_REQUIRED');
  return value;
}

export function payloadHash(operation: string, payload: unknown): string {
  return createHash('sha256').update(operation).update('\u0000').update(JSON.stringify(payload)).digest('hex');
}

export type Outcome<T> = {status: number; body: T; replayed: boolean};

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

/**
 * 관리자 쓰기 한 건을 DB 트랜잭션 하나로 처리한다.
 * 요청 예약 → 변경(공지·판·이미지) → 감사 기록 → 결과 기록이 모두 함께 반영되거나 모두 취소된다.
 * - 같은 사용자·같은 requestId 가 이미 끝났으면 그 결과를 그대로 돌려준다(서버 인스턴스가 달라도 DB 기본키로 판정).
 * - 같은 requestId 로 다른 내용을 보내면 409.
 * - 중간에 실패하면 예약까지 취소되므로 같은 requestId 로 다시 시도할 수 있다.
 * - 잠금 충돌(TransientStoreError)은 트랜잭션 전체를 처음부터 다시 실행한다.
 */
export async function once<T>(store: ContentStore, actorId: string, requestId: string, operation: string, payload: unknown, run: (tx: ContentTx) => Promise<{status: number; body: T}>, now: () => Date, attempts = 40): Promise<Outcome<T>> {
  const hash = payloadHash(operation, payload);
  for (let attempt = 1; ; attempt++) {
    try {
      return await store.transaction(async tx => {
        const reserved = await tx.reserveRequest({actorId, requestId, operation, payloadHash: hash, createdAt: now()});
        if (reserved !== 'reserved') {
          if (reserved.operation !== operation || reserved.payloadHash !== hash) throw new ContentError(409, 'REQUEST_ID_REUSED');
          return {status: reserved.status, body: JSON.parse(reserved.response) as T, replayed: true};
        }
        const result = await run(tx);
        await tx.completeRequest(actorId, requestId, result.status, JSON.stringify(result.body));
        return {...result, replayed: false};
      });
    } catch (error) {
      if (error instanceof TransientStoreError && attempt < attempts) {
        await sleep(Math.min(200, 5 * attempt) + Math.floor(Math.random() * 20));
        continue;
      }
      if (error instanceof TransientStoreError) throw new ContentError(503, 'STORE_BUSY');
      throw error;
    }
  }
}
