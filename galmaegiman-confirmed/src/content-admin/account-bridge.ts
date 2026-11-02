import type {FastifyRequest} from 'fastify';
import {NOTICE_ADMIN_ROLE, type Principal, type PrincipalResolver} from './access.js';

/**
 * 계정 모듈 → 공지 관리 권한 변환 계층.
 * 계정 모듈(다른 작업)이 아래 두 인터페이스를 구현해 넘긴다. 이 파일은 계정·세션 저장 모델을 직접 읽지 않는다.
 */
export interface VerifiedAccountReader {
  /**
   * 서버가 검증한 자체 계정 ID. 세션 쿠키/토큰 검증은 계정 모듈이 한다.
   * 로그인 안 됨·만료·취소된 세션은 null. 저장소 장애는 예외(→ 503).
   */
  verifiedAccountId(req: FastifyRequest): Promise<string | null>;
}

export interface AccountPermissionReader {
  /** 서버 저장소 기준 권한 보유 여부. 장애는 예외(→ 503). */
  hasPermission(accountId: string, permission: typeof NOTICE_WRITE_PERMISSION): Promise<boolean>;
}

/** 자체 계정의 공지 쓰기 권한 이름 */
export const NOTICE_WRITE_PERMISSION = 'notice:write';

const ACCOUNT_ID = /^[A-Za-z0-9_-]{1,90}$/;

/** 기본 변환: 계정 ID 앞에 접두사를 붙인 감사 기록용 userId. 이메일·전화번호·카카오 ID 를 쓰지 않는다. */
export function defaultUserIdFor(accountId: string): string {
  if (!ACCOUNT_ID.test(accountId)) throw new Error('invalid verified account id');
  return `acct_${accountId}`;
}

/**
 * 검증된 계정 → Principal.
 * - notice:write 권한이 확인된 경우에만 content.notice.admin 역할을 준다.
 * - 클라이언트가 보낸 역할·권한 값(헤더·본문·쿼리)은 읽지 않는다. 카카오 연동 여부도 보지 않는다.
 * - 계정 확인·권한 확인이 예외를 던지면 그대로 올려 503 으로 처리되게 한다.
 */
export function accountPrincipalResolver(options: {accounts: VerifiedAccountReader; permissions: AccountPermissionReader; toUserId?: (accountId: string) => string}): PrincipalResolver {
  const toUserId = options.toUserId ?? defaultUserIdFor;
  return {
    async resolve(req: FastifyRequest): Promise<Principal | null> {
      const accountId = await options.accounts.verifiedAccountId(req);
      if (accountId === null) return null;
      if (typeof accountId !== 'string' || !ACCOUNT_ID.test(accountId)) throw new Error('invalid verified account id');
      const canWrite = await options.permissions.hasPermission(accountId, NOTICE_WRITE_PERMISSION);
      return {userId: toUserId(accountId), roles: new Set(canWrite === true ? [NOTICE_ADMIN_ROLE] : [])};
    },
  };
}
