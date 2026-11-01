import type {FastifyRequest} from 'fastify';

/**
 * 공지·이미지 관리 API의 인증 연결 지점.
 *
 * 인증 담당 모듈이 서버 쪽에서 확인한 사용자 식별자와 역할만 넘긴다.
 * - 요청 본문·헤더·쿼리에 담긴 role 값은 절대 읽지 않는다.
 * - 카카오 연동 여부로 관리자를 판정하지 않는다. 관리자 여부는 오직 roles로만 판단한다.
 * - 연결 전 기본값은 모든 요청을 거부하는 denyAllPrincipals 이다.
 * 시험용 가짜 인증은 src 아래에 두지 않는다(시험 파일 안에서만 이 인터페이스를 구현한다).
 */
export type Principal = {
  /** 인증 모듈이 발급한 내부 사용자 식별자(자체 계정 기준). 이메일·전화번호 같은 개인정보를 넣지 않는다. */
  readonly userId: string;
  /** 서버가 저장한 역할 목록. 클라이언트 입력에서 만들면 안 된다. */
  readonly roles: ReadonlySet<string>;
};

export interface PrincipalResolver {
  /** 확인된 사용자가 없으면 null. 예외를 던지면 요청은 거부된다. */
  resolve(req: FastifyRequest): Promise<Principal | null>;
}

export const NOTICE_ADMIN_ROLE = 'content.notice.admin';

/** 인증 연결 전 기본값: 누구도 관리자 API를 쓸 수 없다. */
export const denyAllPrincipals: PrincipalResolver = Object.freeze({resolve: async () => null});

export function isNoticeAdmin(principal: Principal | null): principal is Principal {
  return !!principal && typeof principal.userId === 'string' && principal.userId.length > 0 && principal.roles.has(NOTICE_ADMIN_ROLE);
}

const ID = /^[A-Za-z0-9_-]{1,100}$/;

/** resolver 결과를 한 번 더 검사한다. 형식이 이상하면 인증되지 않은 것으로 본다. */
export async function resolvePrincipal(resolver: PrincipalResolver, req: FastifyRequest): Promise<Principal | null> {
  try {
    const principal = await resolver.resolve(req);
    if (!principal || typeof principal.userId !== 'string' || !ID.test(principal.userId) || !(principal.roles instanceof Set)) return null;
    return principal;
  } catch {
    return null;
  }
}
