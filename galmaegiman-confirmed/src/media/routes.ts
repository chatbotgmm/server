import type {FastifyInstance, FastifyReply, FastifyRequest} from 'fastify';
import {denyAllPrincipals, type PrincipalResolver} from '../content-admin/access.js';
import {jsonHeaders, requireAdmin, sendError} from '../content-admin/http.js';
import {ContentError} from '../content-admin/errors.js';
import type {MediaService} from './service.js';
import {DEFAULT_IMAGE_LIMITS} from './validate.js';

export type MediaRouteOptions = {
  service: MediaService;
  principals?: PrincipalResolver;
  prefix?: string;
  maxBytes?: number;
};

type Params = {id: string};

/**
 * 이미지 업로드·조회 API. 업로드 본문은 이미지 바이트 그대로(한 장).
 * Content-Type 은 받기만 하고 믿지 않는다. 실제 형식은 서버가 서명·디코딩으로 판정한다.
 * 이 함수는 자체 범위(encapsulated plugin) 안에서 본문 해석기를 등록하므로 다른 라우트에 영향을 주지 않는다.
 */
export async function registerMediaRoutes(app: FastifyInstance, options: MediaRouteOptions) {
  const principals = options.principals ?? denyAllPrincipals;
  const base = options.prefix ?? '/api/v1';
  const maxBytes = options.maxBytes ?? DEFAULT_IMAGE_LIMITS.maxBytes;
  const {service} = options;

  await app.register(async scope => {
    scope.addContentTypeParser(['image/png', 'image/jpeg', 'image/webp', 'application/octet-stream'], {parseAs: 'buffer', bodyLimit: maxBytes}, (_req, body, done) => done(null, body));

    const binary = (reply: FastifyReply, file: {mime: string; data: Buffer; sha256: string; name: string}, privateCache: boolean) => {
      reply.header('Content-Type', file.mime);
      reply.header('X-Content-Type-Options', 'nosniff');
      reply.header('Content-Security-Policy', "default-src 'none'; sandbox");
      reply.header('Content-Disposition', `inline; filename="${file.name}"`);
      reply.header('Cross-Origin-Resource-Policy', 'same-site');
      reply.header('ETag', `"${file.sha256}"`);
      reply.header('Cache-Control', privateCache ? 'private, no-store' : 'public, max-age=300');
      return reply.send(file.data);
    };

    scope.post(`${base}/admin/media`, {config: {rateLimit: {max: 30, timeWindow: '1 minute'}}, bodyLimit: maxBytes}, async (req: FastifyRequest, reply: FastifyReply) => {
      jsonHeaders(reply);
      try {
        const actor = await requireAdmin(principals, req);
        if (!Buffer.isBuffer(req.body)) throw new ContentError(415, 'IMAGE_UNSUPPORTED_TYPE');
        const result = await service.upload(actor, req.headers['idempotency-key'], req.body);
        if (result.replayed) reply.header('Idempotent-Replayed', 'true');
        return reply.code(result.status).send(result.body);
      } catch (error) {
        return sendError(reply, error);
      }
    });

    scope.get(`${base}/admin/media/:id`, {config: {rateLimit: {max: 120, timeWindow: '1 minute'}}}, async (req: FastifyRequest, reply: FastifyReply) => {
      try {
        await requireAdmin(principals, req);
        return binary(reply, await service.adminRead((req.params as Params).id), true);
      } catch (error) {
        jsonHeaders(reply);
        return sendError(reply, error);
      }
    });

    scope.get(`${base}/admin/media/:id/info`, {config: {rateLimit: {max: 120, timeWindow: '1 minute'}}}, async (req: FastifyRequest, reply: FastifyReply) => {
      jsonHeaders(reply);
      try {
        await requireAdmin(principals, req);
        return await service.adminInfo((req.params as Params).id);
      } catch (error) {
        return sendError(reply, error);
      }
    });

    scope.post(`${base}/admin/media/:id/retire`, {config: {rateLimit: {max: 30, timeWindow: '1 minute'}}, bodyLimit: 4096}, async (req: FastifyRequest, reply: FastifyReply) => {
      jsonHeaders(reply);
      try {
        const actor = await requireAdmin(principals, req);
        const result = await service.retire(actor, (req.params as Params).id, req.body);
        if (result.replayed) reply.header('Idempotent-Replayed', 'true');
        return reply.code(result.status).send(result.body);
      } catch (error) {
        return sendError(reply, error);
      }
    });

    scope.get(`${base}/media/:id`, {config: {rateLimit: {max: 300, timeWindow: '1 minute'}}}, async (req: FastifyRequest, reply: FastifyReply) => {
      try {
        return binary(reply, await service.publicRead((req.params as Params).id), false);
      } catch (error) {
        jsonHeaders(reply);
        return sendError(reply, error);
      }
    });
  });
}
