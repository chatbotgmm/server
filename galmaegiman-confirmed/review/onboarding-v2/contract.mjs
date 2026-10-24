// Isolated review contract. Not imported by src/, Android, or production routes.
// store.transaction(authenticatedPlayerId, callback) MUST provide a durable,
// serializable transaction over this player's new-system onboarding aggregate.
// Authentication and server-verified tutorial progress belong to the adapter.
export const CHOICES = Object.freeze(['u02', 'u03', 'u04', 'u05', 'u06', 'u07', 'u08']);
export const FLOW = 'roster60-onboarding-v1';

function fail(code) { throw new Error(code); }
function player(auth) {
  if (!auth || typeof auth.playerId !== 'string' || !auth.playerId) fail('UNAUTHENTICATED');
  return auth.playerId; // Trusted server principal, never an HTTP body field.
}
function fields(value, keys) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
      || Object.keys(value).sort().join() !== [...keys].sort().join()
      || keys.some(key => typeof value[key] !== 'string' || !value[key])) fail('INVALID_REQUEST');
}
function catalogSnapshot(getCatalog) {
  const c = structuredClone(getCatalog());
  if (!c || c.approved !== true || typeof c.version !== 'string' || !c.version
      || !Array.isArray(c.characters)) fail('CATALOG_PENDING');
  const selected = CHOICES.map(id => {
    const matches = c.characters.filter(x => x.id === id);
    if (matches.length !== 1) fail('CATALOG_PENDING');
    const entry = matches[0];
    if (entry.rarity !== 'COMMON' || !entry.name
        || ['stats', 'skills', 'affinity', 'defense'].some(k => entry[k] == null)) fail('CATALOG_PENDING');
    return entry;
  });
  return {version: c.version, characters: selected};
}
function ownedTicket(state, owner, ticketId) {
  const t = state.ticket;
  if (!t || t.id !== ticketId || t.owner !== owner || t.flow !== FLOW) fail('TICKET_NOT_FOUND');
  return t;
}

export function createOnboarding({store, getCatalog}) {
  return {
    // No legacy D1 IDs or 15-day loan path. New-system u01 only.
    start(auth) {
      const owner = player(auth);
      return store.transaction(owner, state => {
        if (!state.started) {
          state.started = true;
          state.units.push({id: `${FLOW}:starter:${owner}`, speciesId: 'u01',
            name: '갈매미맨', owner, permanent: true, source: FLOW});
        }
        return {flow: FLOW, started: true, starterId: `${FLOW}:starter:${owner}`};
      });
    },
    complete(auth) {
      const owner = player(auth);
      return store.transaction(owner, state => {
        // Only server gameplay can set this persisted proof. No completion flag
        // or client-supplied tutorial step is accepted by this method.
        if (!state.started || state.tutorialCompleteVerified !== true) fail('TUTORIAL_NOT_COMPLETE');
        if (!state.ticket) {
          const id = `${FLOW}:ticket:${owner}`;
          state.ticket = {id, owner, flow: FLOW, status: 'unused', quantity: 1};
          state.mail = {id: `${FLOW}:mail:${owner}`, owner, flow: FLOW,
            title: '커먼 선택지급권', ticketId: id};
        }
        return {mail: structuredClone(state.mail), ticket: structuredClone(state.ticket)};
      });
    },
    // Opening/closing the UI has no mutation or reservation. Cancel needs no API.
    preview(auth, request) {
      fields(request, ['ticketId']);
      const owner = player(auth);
      return store.transaction(owner, state => {
        const ticket = ownedTicket(state, owner, request.ticketId);
        if (ticket.status !== 'unused') return {status: 'used', receipt: structuredClone(ticket.receipt)};
        return {status: 'unused', ticketId: ticket.id, ...catalogSnapshot(getCatalog)};
      });
    },
    confirm(auth, request) {
      fields(request, ['ticketId', 'characterId', 'catalogVersion']);
      const owner = player(auth);
      return store.transaction(owner, state => {
        const ticket = ownedTicket(state, owner, request.ticketId);
        if (!CHOICES.includes(request.characterId)) fail('INVALID_CHOICE');
        if (ticket.status === 'used') {
          if (ticket.receipt.characterId !== request.characterId) fail('ALREADY_REDEEMED');
          return structuredClone(ticket.receipt); // reconnect/retry: same grant
        }
        if (ticket.status !== 'unused' || ticket.quantity !== 1) fail('TICKET_UNAVAILABLE');
        const catalog = catalogSnapshot(getCatalog);
        if (request.catalogVersion !== catalog.version) fail('CATALOG_CHANGED');
        const unitId = `${FLOW}:reward:${owner}`;
        const receipt = {ticketId: ticket.id, unitId, characterId: request.characterId,
          catalogVersion: catalog.version};
        // The adapter must commit ALL changes together or roll ALL back.
        state.units.push({id: unitId, speciesId: request.characterId, owner,
          permanent: true, source: ticket.id});
        ticket.status = 'used';
        ticket.quantity = 0;
        ticket.receipt = receipt;
        return structuredClone(receipt);
      });
    },
  };
}
