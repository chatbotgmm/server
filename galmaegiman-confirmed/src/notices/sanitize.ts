import sanitizeHtml from 'sanitize-html';
import {isMediaId} from '../content-admin/ids.js';

/**
 * 공지 본문 정화. 유지관리되는 sanitize-html(htmlparser2 기반 파서)로 허용 목록만 남긴다.
 * - 허용 태그 밖은 버리고, script/style/iframe/object/svg/math 등은 내용까지 지운다.
 * - 속성: a[href](http/https/mailto), img[data-media-id, alt] 만. on* 이벤트·style·class·id·src 는 모두 제거.
 * - 본문 이미지는 src 없이 data-media-id 자리표시로만 저장하고, 그 글에 연결된 이미지만 남긴다.
 *   실제 주소는 응답할 때 서버가 붙인다(renderNoticeHtml).
 * 화면은 bodyHtml(렌더 결과)만 innerHTML 로 그리고, 원문(bodySource)·스크립트 원문(scriptSource)은 글자로만 다룬다.
 */
const TAGS = ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'blockquote', 'ul', 'ol', 'li', 'h2', 'h3', 'h4', 'a', 'code', 'pre', 'hr', 'img'];
const DROP_WITH_CONTENT = ['script', 'style', 'textarea', 'option', 'noscript', 'iframe', 'object', 'embed', 'template', 'svg', 'math'];

export const MAX_BODY_SOURCE = 50_000;
export const MAX_SCRIPT_SOURCE = 50_000;

/** 저장용 정화. allowedMediaIds 에 없는 이미지 자리표시는 지운다. */
export function sanitizeNoticeHtml(source: string, allowedMediaIds: ReadonlySet<string> = new Set()): string {
  return sanitizeHtml(source, {
    allowedTags: TAGS,
    allowedAttributes: {a: ['href', 'rel', 'target'], img: ['data-media-id', 'alt']},
    allowedSchemes: ['http', 'https', 'mailto'],
    allowedSchemesByTag: {},
    allowedSchemesAppliedToAttributes: ['href'],
    allowProtocolRelative: false,
    disallowedTagsMode: 'discard',
    nonTextTags: DROP_WITH_CONTENT,
    parseStyleAttributes: false,
    transformTags: {
      a: (tagName, attribs) => ({tagName, attribs: {...(attribs.href ? {href: attribs.href} : {}), rel: 'noopener noreferrer nofollow', target: '_blank'}}),
    },
    exclusiveFilter: frame => frame.tag === 'img' && !(isMediaId(frame.attribs['data-media-id']) && allowedMediaIds.has(frame.attribs['data-media-id'])),
  }).trim();
}

/**
 * 응답용 렌더. 저장된 정화 결과를 다시 정화하면서 이미지 자리표시에 서버가 정한 주소만 붙인다.
 * mediaUrl 이 null 을 주면(공개 불가 등) 그 이미지는 지운다.
 */
export function renderNoticeHtml(storedHtml: string, mediaUrl: (mediaId: string) => string | null): string {
  return sanitizeHtml(storedHtml, {
    allowedTags: TAGS,
    allowedAttributes: {a: ['href', 'rel', 'target'], img: ['src', 'alt', 'loading', 'data-media-id']},
    allowedSchemes: ['http', 'https', 'mailto'],
    allowedSchemesByTag: {img: []},
    allowedSchemesAppliedToAttributes: ['href', 'src'],
    allowProtocolRelative: false,
    disallowedTagsMode: 'discard',
    nonTextTags: DROP_WITH_CONTENT,
    parseStyleAttributes: false,
    transformTags: {
      img: (tagName, attribs): sanitizeHtml.Tag => {
        const id = attribs['data-media-id'];
        const url = isMediaId(id) ? mediaUrl(id) : null;
        if (!url) return {tagName: 'removed-img', attribs: {}};
        return {tagName, attribs: {'data-media-id': id, src: url, alt: attribs.alt ?? '', loading: 'lazy'}};
      },
    },
    exclusiveFilter: frame => frame.tag === 'removed-img',
  }).trim();
}

/** 목록용 짧은 요약(태그 없는 글자, HTML 이스케이프됨). */
export function plainExcerpt(html: string, max = 120): string {
  const text = sanitizeHtml(html, {allowedTags: [], allowedAttributes: {}}).replace(/\s+/g, ' ').trim();
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}
