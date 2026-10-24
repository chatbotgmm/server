import test from 'node:test';
import assert from 'node:assert/strict';
import {createOnboarding, CHOICES} from './contract.mjs';

// TEST ONLY. Serialized copy-on-write aggregate, not a production DB adapter.
class MemoryStore {
  rows = new Map(); tail = Promise.resolve(); failCommit = false;
  transaction(owner, callback) {
    const work = this.tail.then(async () => {
      const draft = structuredClone(this.rows.get(owner) ?? {units: [], started: false, tutorialCompleteVerified: false});
      const result = await callback(draft);
      if (this.failCommit) { this.failCommit = false; throw new Error('COMMIT_FAILED'); }
      this.rows.set(owner, draft);
      return structuredClone(result);
    });
    this.tail = work.catch(() => {});
    return work;
  }
}
// Opaque display fixtures ONLY: no proposed MP, damage, cost or affinity values.
const makeCatalog = () => ({approved: true, version: 'test-fixture-only', characters: CHOICES.map(id => ({
  id, rarity: 'COMMON', name: `fixture-${id}`, stats: {fixture: 'stats'},
  skills: [{fixture: 'skill'}], affinity: {fixture: 'affinity'}, defense: {fixture: 'defense'},
}))});
function fixture() {
  const store = new MemoryStore(); let catalog = makeCatalog();
  const options = {store, getCatalog: () => catalog};
  return {store, options, service: createOnboarding(options), auth: {playerId: 'player-a'},
    changeCatalog: c => {catalog = c;}};
}
async function ready(f) {
  await f.service.start(f.auth);
  await f.store.transaction(f.auth.playerId, s => {s.tutorialCompleteVerified = true;});
  return (await f.service.complete(f.auth)).ticket.id;
}
const request = (ticketId, characterId = 'u02') => ({ticketId, characterId, catalogVersion: 'test-fixture-only'});

test('start and completion retries issue one permanent u01 and one mailbox ticket', async () => {
  const f = fixture();
  await assert.rejects(f.service.complete(f.auth), /TUTORIAL_NOT_COMPLETE/);
  await Promise.all(Array.from({length: 20}, () => f.service.start(f.auth)));
  await assert.rejects(f.service.complete(f.auth), /TUTORIAL_NOT_COMPLETE/);
  const ticketId = await ready(f);
  const results = await Promise.all(Array.from({length: 20}, () => f.service.complete(f.auth)));
  assert.ok(results.every(x => x.ticket.id === ticketId));
  const s = f.store.rows.get(f.auth.playerId);
  assert.equal(s.units.length, 1); assert.equal(s.units[0].speciesId, 'u01');
  assert.equal(s.units[0].permanent, true); assert.equal(s.units[0].name, '갈매미맨');
  assert.equal(s.mail.ticketId, ticketId); assert.equal(s.ticket.quantity, 1);
});
test('preview lists seven profiles, close/cancel/reopen never consumes or grants', async () => {
  const f = fixture(), id = await ready(f), before = structuredClone(f.store.rows.get(f.auth.playerId));
  for (let i = 0; i < 4; i++) {
    const p = await f.service.preview(f.auth, {ticketId: id});
    assert.deepEqual(p.characters.map(x => x.id), CHOICES);
    assert.ok(p.characters.every(x => x.stats && x.skills && x.affinity && x.defense));
  }
  assert.deepEqual(f.store.rows.get(f.auth.playerId), before);
});
test('simultaneous different selections have one winner; reconnect repeats receipt', async () => {
  const f = fixture(), id = await ready(f);
  const results = await Promise.allSettled(CHOICES.map(c => f.service.confirm(f.auth, request(id, c))));
  assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
  const receipt = results.find(r => r.status === 'fulfilled').value;
  const resumed = createOnboarding(f.options);
  const retries = await Promise.all(Array.from({length: 20}, () => resumed.confirm(f.auth, request(id, receipt.characterId))));
  assert.ok(retries.every(x => x.unitId === receipt.unitId));
  const s = f.store.rows.get(f.auth.playerId);
  assert.equal(s.units.length, 2); assert.equal(s.ticket.quantity, 0);
  assert.equal(s.units[1].permanent, true);
  assert.equal((await resumed.complete(f.auth)).ticket.status, 'used');
});
test('server rejects other owner, u01, unknown choice, stale catalog, forged fields', async () => {
  const f = fixture(), id = await ready(f);
  await assert.rejects(f.service.confirm({playerId: 'other'}, request(id)), /TICKET_NOT_FOUND/);
  for (const c of ['u01', 'D1', 'u99']) await assert.rejects(f.service.confirm(f.auth, request(id, c)), /INVALID_CHOICE/);
  await assert.rejects(f.service.confirm(f.auth, {...request(id), catalogVersion: 'old'}), /CATALOG_CHANGED/);
  assert.throws(() => f.service.confirm(f.auth, {...request(id), stats: {}}), /INVALID_REQUEST/);
  assert.equal(f.store.rows.get(f.auth.playerId).ticket.status, 'unused');
});
test('unapproved or incomplete catalog fails closed without inventing character values', async () => {
  const f = fixture(), id = await ready(f);
  for (const c of [null, {...makeCatalog(), approved: false}, {...makeCatalog(), characters: []}]) {
    f.changeCatalog(c);
    await assert.rejects(f.service.preview(f.auth, {ticketId: id}), /CATALOG_PENDING/);
    await assert.rejects(f.service.confirm(f.auth, request(id)), /CATALOG_PENDING/);
  }
  assert.equal(f.store.rows.get(f.auth.playerId).units.length, 1);
});
test('commit failure rolls back both consumption and grant; retry grants once', async () => {
  const f = fixture(), id = await ready(f), before = structuredClone(f.store.rows.get(f.auth.playerId));
  f.store.failCommit = true;
  await assert.rejects(f.service.confirm(f.auth, request(id)), /COMMIT_FAILED/);
  assert.deepEqual(f.store.rows.get(f.auth.playerId), before);
  await f.service.confirm(f.auth, request(id));
  assert.equal(f.store.rows.get(f.auth.playerId).units.length, 2);
});
test('mail transaction rollback creates neither a reward mail nor ticket', async () => {
  const f = fixture(); await f.service.start(f.auth);
  await f.store.transaction(f.auth.playerId, s => {s.tutorialCompleteVerified = true;});
  f.store.failCommit = true;
  await assert.rejects(f.service.complete(f.auth), /COMMIT_FAILED/);
  assert.equal(f.store.rows.get(f.auth.playerId).ticket, undefined);
  assert.equal(f.store.rows.get(f.auth.playerId).mail, undefined);
  await f.service.complete(f.auth);
  assert.equal(f.store.rows.get(f.auth.playerId).ticket.quantity, 1);
});
