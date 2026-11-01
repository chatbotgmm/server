import sanitizeHtml from 'sanitize-html';

/**
 * 공지 본문 정화. 유지관리되는 sanitize-html(htmlparser2 기반 파서)로 허용 목록만 남긴다.
 * - 허용 태그 밖의 태그는 버리고, script/style/iframe/object 등은 내용까지 지운다.
 * - 속성은 a[href] 만 허용. on* 이벤트 속성, style, class, id, data-* 는 모두 제거.
 * - 링크는 http/https/mailto 만. javascript:, data:, vbscript:, 상대 경로 스킴 우회는 제거된다.
 * - 이미지는 본문에 넣지 않는다. 게시글에 연결한 이미지 목록으로 따로 그린다.
 * 화면 쪽에서도 이 결과만 innerHTML 로 그리고, 원문(bodySource)은 글자로만 다룬다.
 */
const OPTIONS: sanitizeHtml.IOptions = {
  allowedTags: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'blockquote', 'ul', 'ol', 'li', 'h2', 'h3', 'h4', 'a', 'code', 'pre', 'hr'],
  allowedAttributes: {a: ['href']},
  allowedSchemes: ['http', 'https', 'mailto'],
  allowedSchemesByTag: {},
  allowedSchemesAppliedToAttributes: ['href'],
  allowProtocolRelative: false,
  disallowedTagsMode: 'discard',
  nonTextTags: ['script', 'style', 'textarea', 'option', 'noscript', 'iframe', 'object', 'embed', 'template', 'svg', 'math'],
  enforceHtmlBoundary: false,
  parseStyleAttributes: false,
  transformTags: {
    a: (tagName, attribs) => ({tagName, attribs: {...attribs, rel: 'noopener noreferrer nofollow', target: '_blank'}}),
  },
};
// transformTags 가 붙인 rel/target 은 허용 목록에 있어야 남는다.
OPTIONS.allowedAttributes = {a: ['href', 'rel', 'target']};

export const MAX_BODY_SOURCE = 50_000;

export function sanitizeNoticeHtml(source: string): string {
  return sanitizeHtml(source, OPTIONS).trim();
}

/** 목록용 짧은 글자 요약(태그 없음). */
export function plainExcerpt(html: string, max = 120): string {
  const text = sanitizeHtml(html, {allowedTags: [], allowedAttributes: {}}).replace(/\s+/g, ' ').trim();
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}
