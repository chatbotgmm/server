import type {FastifyReply, FastifyRequest} from 'fastify';
import {isNoticeAdmin, resolvePrincipal, type Principal, type PrincipalResolver} from './access.js';
import {ContentError} from './errors.js';

/** JSON 응답 공통 헤더: 캐시 금지, MIME 추측 금지, 이 응답 안에서 어떤 것도 실행되지 않게. */
export function jsonHeaders(reply: FastifyReply) {
  reply.header('Cache-Control', 'no-store');
  reply.header('X-Content-Type-Options', 'nosniff');
  reply.header('Content-Security-Policy', "default-src 'none'; frame-ancestors 'none'");
}

/** 관리자만 통과. 인증 없음 401, 권한 없음 403. 요청 안의 role 값은 보지 않는다. */
export async function requireAdmin(resolver: PrincipalResolver, req: FastifyRequest): Promise<Principal> {
  const principal = await resolvePrincipal(resolver, req);
  if (!principal) throw new ContentError(401, 'LOGIN_REQUIRED');
  if (!isNoticeAdmin(principal)) throw new ContentError(403, 'ADMIN_REQUIRED');
  return principal;
}

export function sendError(reply: FastifyReply, error: unknown) {
  if (error instanceof ContentError) return reply.code(error.status).send({error: error.code, ...error.extra});
  // 내부 오류 내용·스택은 응답에 넣지 않는다.
  reply.log.error({err: error instanceof Error ? error.name : 'unknown'}, 'content-admin internal error');
  return reply.code(500).send({error: 'INTERNAL'});
}
