import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizeNickname, nicknameError, needsInitialNickname} from '../src/nickname.ts';
test('normalizes fullwidth letters and whitespace before validation', () => {
 assert.equal(normalizeNickname('  ＡＢ   대원  '), 'AB 대원');
 assert.equal(nicknameError(normalizeNickname('  ＡＢ   대원  ')), null);
});
test('preserves chatbot name and impersonation policy', () => {
 for (const value of ['', 'a', '열한글자초과닉네임이름', '닉!네임', '😀😀', '관리자1', 'aDmIn', 'GM12', 'dev99', '공식 대원']) assert.ok(nicknameError(value), value);
 for (const value of ['모험가', 'ab', '대원 81', '갈매미왕']) assert.equal(nicknameError(value), null, value);
});
test('gates only fresh nameless users, not legacy or started tutorials', () => {
 const p={nickname:null,gameTutorial:1,gameSeen:''};
 assert.equal(needsInitialNickname(p),true);
 for (const extra of [{nickname:'대원'},{gameTutorial:9},{gameTutorial:2},{gameTutorial:0},{gameSeen:'tut'},{gameSeen:'shop,tut'}]) assert.equal(needsInitialNickname({...p,...extra}), false);
 assert.equal(needsInitialNickname({...p, gameSeen:'shop'}), true);
});
