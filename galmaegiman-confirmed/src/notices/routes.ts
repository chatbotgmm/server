import type {FastifyInstance, FastifyReply, FastifyRequest} from 'fastify';
import {denyAllPrincipals, type Principal, type PrincipalResolver} from '../content-admin/access.js';
import {jsonHeaders, requireAdmin, sendError} from '../content-admin/http.js';
import type {ContentStore} from '../content-admin/store.js';
import type {NoticeService} from './service.js';

export type NoticeRouteOptions = {
  service: NoticeService;
  store: ContentStore;
  /** 인증 담당 모듈이 넘긴다. 생략하면 관리자 API 는 모두 401. */
  principals?: PrincipalResolver;
  prefix?: string;
};

type Params = {id: string};

/** /api/v1 아래 공지 공개·관리 API. 기존 /my/api/native/news 와 겹치지 않는다. */
export async function registerNoticeRoutes(app: FastifyInstance, options: NoticeRouteOptions) {
  const {service, store} = options;
  const principals = options.principals ?? denyAllPrincipals;
  const base = options.prefix ?? '/api/v1';
  const limit = {rateLimit: {max: 120, timeWindow: '1 minute'}};
  const adminLimit = {rateLimit: {max: 60, timeWindow: '1 minute'}};

  const open = (handler: (req: FastifyRequest) => Promise<unknown>) => async (req: FastifyRequest, reply: FastifyReply) => {
    jsonHeaders(reply);
    try {
      return await handler(req);
    } catch (error) {
      return sendError(reply, error);
    }
  };
  const admin = (handler: (actor: Principal, req: FastifyRequest) => Promise<unknown>) => async (req: FastifyRequest, reply: FastifyReply) => {
    jsonHeaders(reply);
    try {
      const actor = await requireAdmin(principals, req);
      const result = await handler(actor, req);
      if (result && typeof result === 'object' && 'status' in result && 'body' in result) {
        const r = result as {status: number; body: unknown; replayed?: boolean};
        if (r.replayed) reply.header('Idempotent-Replayed', 'true');
        return reply.code(r.status).send(r.body);
      }
      return result;
    } catch (error) {
      return sendError(reply, error);
    }
  };
  const id = (req: FastifyRequest) => (req.params as Params).id;

  // 공개
  app.get(`${base}/notices`, {config: limit}, open(async req => ({items: await service.listPublished(Number((req.query as Record<string, string>)?.limit ?? 50) || 50)})));
  app.get(`${base}/notices/:id`, {config: limit}, open(async req => service.getPublished(id(req))));

  // 관리자
  const json = {config: adminLimit, bodyLimit: 128 * 1024};
  app.get(`${base}/admin/notices`, {config: adminLimit}, admin(async (_a, req) => ({items: await service.adminList((req.query as Record<string, unknown>)?.status)})));
  app.get(`${base}/admin/notices/:id`, {config: adminLimit}, admin(async (_a, req) => service.adminGet(id(req))));
  app.get(`${base}/admin/notices/:id/revisions`, {config: adminLimit}, admin(async (_a, req) => ({items: await service.revisions(id(req))})));
  app.post(`${base}/admin/notices`, json, admin((a, req) => service.create(a, req.body)));
  app.put(`${base}/admin/notices/:id`, json, admin((a, req) => service.update(a, id(req), req.body)));
  app.post(`${base}/admin/notices/:id/publish`, json, admin((a, req) => service.publish(a, id(req), req.body)));
  app.post(`${base}/admin/notices/:id/hide`, json, admin((a, req) => service.hide(a, id(req), req.body)));
  app.post(`${base}/admin/notices/:id/pin`, json, admin((a, req) => service.pin(a, id(req), req.body)));
  app.post(`${base}/admin/notices/:id/restore`, json, admin((a, req) => service.restore(a, id(req), req.body)));
  app.get(`${base}/admin/audit`, {config: adminLimit}, admin(async (_a, req) => {
    const n = Math.max(1, Math.min(200, Number((req.query as Record<string, string>)?.limit ?? 50) || 50));
    return {items: (await store.listAudit(n)).map(e => ({...e, at: e.at.toISOString()}))};
  }));
}
