import type {PrismaClient} from '@prisma/client';
import type {FastifyInstance} from 'fastify';
import {resolve, sep} from 'node:path';
import {registerMediaRoutes} from '../media/routes.js';
import {MediaService} from '../media/service.js';
import {FileMediaStorage} from '../media/storage.js';
import {registerNoticeRoutes} from '../notices/routes.js';
import {NoticeService} from '../notices/service.js';
import type {PrincipalResolver} from './access.js';
import type {CsrfPolicy} from './csrf.js';
import {SqlContentStore} from './sql-store.js';

export type ContentAdminWiring = {
  db: PrismaClient;
  /** 이미지 저장 폴더. 정적 제공 폴더 안이면 시작을 거부한다. */
  mediaRoot: string;
  /** 정적 제공 폴더 목록(public/, dist/ 등) */
  staticRoots: string[];
  /**
   * 관리자 인증 연결. 생략하면 관리자 API 는 모두 거부(401).
   * 연결할 때는 CSRF 정책을 반드시 함께 넘긴다(쿠키 인증이면 originCsrfPolicy 또는 토큰 정책).
   */
  admin?: {principals: PrincipalResolver; csrf: CsrfPolicy};
};

/** 통합용 연결 함수. 서버 진입 파일(app.ts)에서 부른다(docs/content-admin/integration-app-ts.patch). */
export async function registerContentAdmin(app: FastifyInstance, options: ContentAdminWiring) {
  const mediaRoot = resolve(options.mediaRoot);
  for (const root of options.staticRoots) {
    const r = resolve(root);
    if (mediaRoot === r || mediaRoot.startsWith(r + sep)) throw new Error('mediaRoot must not be inside a static web root');
  }
  if (options.admin && !options.admin.csrf) throw new Error('content-admin: CSRF policy is required when admin authentication is connected');
  const store = new SqlContentStore(options.db);
  const principals = options.admin?.principals, csrf = options.admin?.csrf;
  await registerNoticeRoutes(app, {service: new NoticeService(store), store, principals, csrf});
  await registerMediaRoutes(app, {service: new MediaService(store, new FileMediaStorage(mediaRoot)), principals, csrf});
}
