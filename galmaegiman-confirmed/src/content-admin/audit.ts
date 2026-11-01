/**
 * 관리자 작업 감사 기록.
 * 기록하는 값은 아래 허용 목록 필드뿐이다. 본문·비밀번호·토큰·요청 헤더·IP·이메일은 남기지 않는다.
 */
export type AuditAction =
  | 'notice.create' | 'notice.update' | 'notice.publish' | 'notice.hide' | 'notice.pin' | 'notice.restore'
  | 'media.upload' | 'media.retire';

export type AuditDetail = {
  version?: number;
  status?: string;
  pinned?: boolean;
  restoredFrom?: number;
  imageCount?: number;
  bytes?: number;
  mime?: string;
  width?: number;
  height?: number;
};

export type AuditEntry = {
  id: string;
  at: Date;
  actorId: string;
  action: AuditAction;
  targetType: 'notice' | 'media';
  targetId: string;
  detail: AuditDetail;
};

const ALLOWED_DETAIL = new Set<keyof AuditDetail>(['version', 'status', 'pinned', 'restoredFrom', 'imageCount', 'bytes', 'mime', 'width', 'height']);

/** 허용 목록 밖의 필드는 버린다(호출부 실수로 본문 같은 값이 섞여도 남지 않게). */
export function safeDetail(detail: Record<string, unknown>): AuditDetail {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(detail)) {
    if (!ALLOWED_DETAIL.has(key as keyof AuditDetail)) continue;
    if (typeof value === 'number' && Number.isFinite(value)) out[key] = value;
    else if (typeof value === 'boolean') out[key] = value;
    else if (typeof value === 'string' && value.length <= 40 && /^[a-z0-9./_-]+$/i.test(value)) out[key] = value;
  }
  return out as AuditDetail;
}
