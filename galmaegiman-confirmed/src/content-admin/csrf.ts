import type {FastifyRequest} from 'fastify';

/**
 * 관리자 쓰기 요청(POST/PUT)의 CSRF 방어.
 * 쿠키처럼 브라우저가 자동으로 붙이는 인증을 연결하면 반드시 verify 를 갖춘 정책을 넘겨야 한다(통합 필수 항목).
 * 'bearer-only' 는 Authorization 헤더 같은 명시적 자격만 쓰는 연결에서만 쓴다.
 */
export type CsrfPolicy = {verify(req: FastifyRequest): Promise<boolean>} | 'bearer-only';

/**
 * Origin 기반 기본 정책.
 * - Origin 이 허용 목록과 정확히 같아야 통과.
 * - Origin 이 없으면 Sec-Fetch-Site 가 same-origin 일 때만 통과(그 외·둘 다 없음은 거부).
 * 토큰 방식(이중 제출 등)이 필요하면 인증 담당이 verify 를 직접 구현해 넘긴다.
 */
export function originCsrfPolicy(allowedOrigins: readonly string[]): CsrfPolicy {
  const allowed = new Set(allowedOrigins.map(o => new URL(o).origin));
  if (!allowed.size) throw new Error('originCsrfPolicy needs at least one allowed origin');
  return {
    async verify(req: FastifyRequest) {
      const origin = req.headers.origin;
      if (typeof origin === 'string') {
        try {
          return allowed.has(new URL(origin).origin) && new URL(origin).origin === origin;
        } catch {
          return false;
        }
      }
      return req.headers['sec-fetch-site'] === 'same-origin';
    },
  };
}
