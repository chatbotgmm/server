import {describe, expect, it} from 'vitest';
import {plainExcerpt, sanitizeNoticeHtml} from '../src/notices/sanitize.js';

const attacks = [
  '<script>alert(1)</script>',
  '<img src=x onerror=alert(1)>',
  '<svg onload=alert(1)><circle/></svg>',
  '<a href="javascript:alert(1)">x</a>',
  '<a href="JaVaScRiPt:alert(1)">x</a>',
  '<a href="java&#x09;script:alert(1)">x</a>',
  '<a href="&#106;avascript:alert(1)">x</a>',
  '<a href="data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==">x</a>',
  '<a href="vbscript:msgbox(1)">x</a>',
  '<a href="//evil.example/x">x</a>',
  '<p onclick="alert(1)" style="background:url(javascript:alert(1))">x</p>',
  '<iframe src="https://evil.example"></iframe>',
  '<object data="x.swf"></object><embed src="x">',
  '<form action="https://evil.example"><input name=a><button>go</button></form>',
  '<style>body{display:none}</style><p>ok</p>',
  '<math><mtext><table><mglyph><style><img src=x onerror=alert(1)>',
  '<noscript><p title="</noscript><img src=x onerror=alert(1)>">',
  '<template><script>alert(1)</script></template>',
  '<meta http-equiv="refresh" content="0;url=https://evil.example">',
  '<base href="https://evil.example/">',
  '<p data-x="1" class="c" id="i">x</p>',
  '<<script>script>alert(1)<</script>/script>',
];

describe('공지 본문 정화(sanitize-html 허용 목록)', () => {
  it.each(attacks)('위험 요소 제거: %s', input => {
    const out = sanitizeNoticeHtml(input).toLowerCase();
    expect(out).not.toMatch(/<script|<iframe|<object|<embed|<svg|<math|<style|<form|<input|<meta|<base|<img|<template/);
    expect(out).not.toMatch(/\son[a-z]+\s*=/);
    expect(out).not.toMatch(/javascript:|vbscript:|data:text/);
    expect(out).not.toMatch(/\sstyle=|\sclass=|\sid=|\sdata-/);
    expect(out).not.toContain('href="//');
  });

  it('허용한 기본 서식은 남기고 링크에 안전한 rel/target 을 붙인다', () => {
    const out = sanitizeNoticeHtml('<h2>점검</h2><p><strong>굵게</strong> <em>기울임</em> <a href="https://gmm.dpdns.org/a?b=1">공식</a></p><ul><li>하나</li></ul>');
    expect(out).toContain('<h2>점검</h2>');
    expect(out).toContain('<strong>굵게</strong>');
    expect(out).toContain('<a href="https://gmm.dpdns.org/a?b=1" rel="noopener noreferrer nofollow" target="_blank">공식</a>');
    expect(out).toContain('<li>하나</li>');
  });

  it('스크립트 내용은 글자로도 남기지 않고, 일반 글자는 이스케이프한다', () => {
    expect(sanitizeNoticeHtml('<script>alert("x")</script>안녕')).toBe('안녕');
    expect(sanitizeNoticeHtml('1 < 2 & 3 > 2')).toBe('1 &lt; 2 &amp; 3 &gt; 2');
  });

  it('요약은 태그 없는 글자만', () => {
    expect(plainExcerpt('<p>공지 <strong>본문</strong></p>')).toBe('공지 본문');
  });
});
