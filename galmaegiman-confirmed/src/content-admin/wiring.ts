import type {PrismaClient} from '@prisma/client';
import type {FastifyInstance} from 'fastify';
import {resolve, sep} from 'node:path';
import {registerMediaRoutes} from '../media/routes.js';
import {MediaService} from '../media/service.js';
import {FileMediaStorage} from '../media/storage.js';
import {registerNoticeRoutes} from '../notices/routes.js';
import {NoticeService} from '../notices/service.js';
import {denyAllPrincipals, type PrincipalResolver} from './access.js';
import {PrismaContentStore} from './prisma-store.js';
import {SerialQueue} from './serial.js';

export type ContentAdminWiring = {
  db: PrismaClient;
  /** 이미지 저장 폴더. public/ 등 정적 제공 폴더 안이면 거부한다. */
  mediaRoot: string;
  /** 정적 제공 폴더(있으면). mediaRoot 가 이 안에 있으면 시작을 멈춘다. */
  staticRoots?: string[];
  /** 인증 담당이 연결. 없으면 관리자 API 는 모두 거부. */
  principals?: PrincipalResolver;
};

/** 통합용 한 줄 연결 함수. 서버 진입 파일(app.ts)에서 부른다(별도 패치 참고). */
export async function registerContentAdmin(app: FastifyInstance, options: ContentAdminWiring) {
  const mediaRoot = resolve(options.mediaRoot);
  for (const root of options.staticRoots ?? []) {
    const r = resolve(root);
    if (mediaRoot === r || mediaRoot.startsWith(r + sep)) throw new Error('mediaRoot must not be inside a static web root');
  }
  const store = new PrismaContentStore(options.db);
  const queue = new SerialQueue();
  const principals = options.principals ?? denyAllPrincipals;
  await registerNoticeRoutes(app, {service: new NoticeService(store, queue), store, principals});
  await registerMediaRoutes(app, {service: new MediaService(store, new FileMediaStorage(mediaRoot), queue), principals});
}
