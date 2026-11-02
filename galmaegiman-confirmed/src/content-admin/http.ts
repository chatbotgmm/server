import type {FastifyReply, FastifyRequest} from 'fastify';
import {requireAdmin, type Principal, type PrincipalResolver} from './access.js';
import type {CsrfPolicy} from './csrf.js';
import {ContentError} from './errors.js';

/** JSON 응답 공통 헤더: 캐시 금지, MIME 추측 금지, 이 응답 안에서 어떤 것도 실행되지 않게. */
export function jsonHeaders(reply: FastifyReply) {
  reply.header('Cache-Control', 'no-store');
  reply.header('X-Content-Type-Options', 'nosniff');
  reply.header('Content-Security-Policy', "default-src 'none'; frame-ancestors 'none'");
}

export type AdminGate = {principals: PrincipalResolver; csrf?: CsrfPolicy};

/** 관리자 읽기: 인증·권한만 */
export function adminRead(gate: AdminGate, req: FastifyRequest): Promise<Principal> {
  return requireAdmin(gate.principals, req);
}

/**
 * 관리자 쓰기: CSRF → 인증·권한 순서.
 * CSRF 정책이 연결되지 않았으면 쓰기는 모두 거부한다(403 CSRF_POLICY_MISSING).
 */
export async function adminWrite(gate: AdminGate, req: FastifyRequest): Promise<Principal> {
  if (!gate.csrf) throw new ContentError(403, 'CSRF_POLICY_MISSING');
  if (gate.csrf !== 'bearer-only') {
    let ok = false;
    try {
      ok = await gate.csrf.verify(req);
    } catch {
      throw new ContentError(503, 'AUTH_UNAVAILABLE');
    }
    if (!ok) throw new ContentError(403, 'CSRF_REJECTED');
  }
  return requireAdmin(gate.principals, req);
}

export function sendError(reply: FastifyReply, error: unknown) {
  if (error instanceof ContentError) {
    if (error.status === 503) reply.header('Retry-After', '5');
    return reply.code(error.status).send({error: error.code, ...error.extra});
  }
  // 내부 오류 내용·스택은 응답에 넣지 않는다.
  reply.log.error({err: error instanceof Error ? error.name : 'unknown'}, 'content-admin internal error');
  return reply.code(500).send({error: 'INTERNAL'});
}
