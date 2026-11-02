import type {FastifyRequest} from 'fastify';
import {ContentError} from './errors.js';

/**
 * 공지·이미지 관리 API의 인증 연결 지점.
 * 인증 쪽이 서버에서 확인한 사용자 식별자와 역할만 넘긴다(요청 본문·헤더의 role 은 읽지 않는다).
 * 계정 모듈과의 실제 연결은 account-bridge.ts 의 accountPrincipalResolver 로 한다.
 */
export type Principal = {
  readonly userId: string;
  readonly roles: ReadonlySet<string>;
};

export interface PrincipalResolver {
  /**
   * 확인된 사용자가 없으면 null(→ 401).
   * 인증 저장소·세션 확인 장애는 예외를 던진다(→ 503). 장애를 null 로 바꾸면 안 된다.
   */
  resolve(req: FastifyRequest): Promise<Principal | null>;
}

export const NOTICE_ADMIN_ROLE = 'content.notice.admin';

/** 인증 연결 전 기본값: 누구도 관리자 API 를 쓸 수 없다(401). */
export const denyAllPrincipals: PrincipalResolver = Object.freeze({resolve: async () => null});

const USER_ID = /^[A-Za-z0-9_-]{1,100}$/;

export function isNoticeAdmin(principal: Principal): boolean {
  return principal.roles.has(NOTICE_ADMIN_ROLE);
}

/**
 * 관리자만 통과시킨다.
 * - 확인된 사용자 없음: 401 LOGIN_REQUIRED
 * - 사용자는 있으나 역할 없음: 403 ADMIN_REQUIRED
 * - 인증 확인 중 장애, 형식이 잘못된 사용자 정보: 503 AUTH_UNAVAILABLE (로그인 문제로 오인하지 않게)
 */
export async function requireAdmin(resolver: PrincipalResolver, req: FastifyRequest): Promise<Principal> {
  let principal: Principal | null;
  try {
    principal = await resolver.resolve(req);
  } catch {
    throw new ContentError(503, 'AUTH_UNAVAILABLE');
  }
  if (principal === null) throw new ContentError(401, 'LOGIN_REQUIRED');
  if (!principal || typeof principal.userId !== 'string' || !USER_ID.test(principal.userId) || !(principal.roles instanceof Set)) throw new ContentError(503, 'AUTH_UNAVAILABLE');
  if (!isNoticeAdmin(principal)) throw new ContentError(403, 'ADMIN_REQUIRED');
  return principal;
}
