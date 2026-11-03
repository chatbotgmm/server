import { AsyncLocalStorage } from 'node:async_hooks';
import { createHash, randomUUID } from 'node:crypto';
import { RosterError } from './roster60-onboarding.js';
export const CURRENT_AUGMENT_CAPABILITY_HEADER = 'x-gmm-roster60-augment-capability';
export const CURRENT_AUGMENT_MODEL_HEADER = 'x-gmm-roster60-augment-model';
export const CURRENT_AUGMENT_CAPABILITY = 'current-v3';
export const CURRENT_AUGMENT_MODEL = 'roster60-current-v1';
export const CURRENT_AUGMENT_UPDATE_REQUIRED = 'ROSTER60_AUGMENT_CLIENT_UPDATE_REQUIRED';
export const currentAugmentClient = Object.freeze({ capability: CURRENT_AUGMENT_CAPABILITY, modelVersion: CURRENT_AUGMENT_MODEL });
const digest = (v) => createHash('sha256').update(v).digest('hex');
const uuid4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export function clientFromHeaders(headers) {
    return headers[CURRENT_AUGMENT_CAPABILITY_HEADER] === CURRENT_AUGMENT_CAPABILITY && headers[CURRENT_AUGMENT_MODEL_HEADER] === CURRENT_AUGMENT_MODEL ? { ...currentAugmentClient } : undefined;
}
export function augmentClientUpdateRequired() { throw new RosterError(409, CURRENT_AUGMENT_UPDATE_REQUIRED); }
export function augmentCompatibilityError(error) { return error instanceof RosterError && error.code === CURRENT_AUGMENT_UPDATE_REQUIRED ? { error: '이 전투를 계속하려면 최신 앱으로 업데이트해 주세요.', code: error.code, message: '이 전투를 계속하려면 최신 앱으로 업데이트해 주세요.', requiredCapability: CURRENT_AUGMENT_CAPABILITY, modelVersion: CURRENT_AUGMENT_MODEL } : undefined; }
export function assertKnownAugmentModel(augments, snapshot) {
    const pin = snapshot?.augmentModelVersion;
    if (augments === undefined) {
        if (pin !== undefined)
            augmentClientUpdateRequired();
        return;
    }
    if (!augments || typeof augments !== 'object' || Array.isArray(augments))
        augmentClientUpdateRequired();
    const state = augments;
    if (state.modelVersion !== undefined && state.modelVersion !== state.version)
        augmentClientUpdateRequired();
    if (state.version === 'roster60-augment-v2') {
        if (pin !== undefined && pin !== state.version)
            augmentClientUpdateRequired();
        return;
    }
    if (state.version !== CURRENT_AUGMENT_MODEL || pin !== undefined && pin !== CURRENT_AUGMENT_MODEL)
        augmentClientUpdateRequired();
}
/** A capability is an authenticated caller's compatibility declaration, never an authorization token.
 * Request-local storage prevents a shared service instance leaking a new client's capability to another request.
 * Explicit defaultClient injection is for local service fixtures; HTTP registration never supplies one. */
export class CurrentAugmentGate {
    options;
    scope = new AsyncLocalStorage();
    constructor(options = {}) {
        this.options = options;
    }
    run(client, run) { return this.scope.run({ client }, run); }
    client() { const scope = this.scope.getStore(); return scope ? scope.client : this.options.defaultClient; }
    enabled() { return this.options.enabled ? this.options.enabled() === true : process.env.ROSTER60_CURRENT_V3_ENABLED === 'true'; }
    compatible() { const c = this.client(); return c?.capability === CURRENT_AUGMENT_CAPABILITY && c.modelVersion === CURRENT_AUGMENT_MODEL; }
    requiresCurrent(policy) { return policy?.augments?.version === 3 && this.enabled(); }
    effectivePolicy(policy, serverNegotiated = false) {
        if (policy?.augments?.version !== 3)
            return policy;
        if (!this.enabled()) {
            const { augments, ...plain } = policy;
            return plain;
        }
        if (!serverNegotiated && !this.compatible())
            augmentClientUpdateRequired();
        return policy;
    }
    assertSupportedState(augments, snapshot) {
        // Pre-gatefix R9 snapshots pin their model in state.version and their stored catalog.
        // They may drain on compatible clients without a snapshot rewrite or a new creation flag.
        assertKnownAugmentModel(augments, snapshot);
    }
    assertState(augments, snapshot) { this.assertSupportedState(augments, snapshot); if (augments?.version === CURRENT_AUGMENT_MODEL && !this.compatible())
        augmentClientUpdateRequired(); }
    assertResult(result) {
        if (!result || typeof result !== 'object')
            return;
        const v = result;
        if (v.state?.augmentChoice !== undefined) {
            const c = v.state.augmentChoice;
            if (!c || typeof c !== 'object' || Array.isArray(c))
                augmentClientUpdateRequired();
            this.assertState(c);
        }
        if (v.battle)
            this.assertResult(v.battle);
        if (Array.isArray(v.battles))
            for (const b of v.battles)
                this.assertResult(b);
    }
    publicResult(result) {
        this.assertResult(result);
        if (!result || typeof result !== 'object')
            return result;
        const v = result;
        let changed = false, out = v;
        if (v.state?.augmentChoice?.version === CURRENT_AUGMENT_MODEL && v.state.augmentChoice.modelVersion === undefined) {
            out = { ...out, state: { ...v.state, augmentChoice: { ...v.state.augmentChoice, modelVersion: CURRENT_AUGMENT_MODEL } } };
            changed = true;
        }
        if (v.battle) {
            const battle = this.publicResult(v.battle);
            if (battle !== v.battle) {
                out = { ...out, battle };
                changed = true;
            }
        }
        if (Array.isArray(v.battles)) {
            const battles = v.battles.map((b) => this.publicResult(b));
            if (battles.some((b, i) => b !== v.battles[i])) {
                out = { ...out, battles };
                changed = true;
            }
        }
        return changed ? out : result;
    }
    queueKey(playerId) { return 'roster60-current-v3-queue:' + digest(playerId); }
    pinQueueGeneration(snapshot) {
        if (!this.compatible())
            augmentClientUpdateRequired();
        const base = snapshot ? JSON.parse(snapshot) : {};
        return JSON.stringify({ ...base, currentAugmentQueue: { generation: randomUUID(), modelVersion: CURRENT_AUGMENT_MODEL } });
    }
    queueGeneration(row) { try {
        const pin = JSON.parse(row.commanderXpSnapshot ?? 'null')?.currentAugmentQueue;
        return pin?.modelVersion === CURRENT_AUGMENT_MODEL && typeof pin.generation === 'string' && uuid4.test(pin.generation) ? pin.generation : null;
    }
    catch {
        return null;
    } }
    makeQueueProof(row) {
        if (!this.compatible())
            augmentClientUpdateRequired();
        const generation = this.queueGeneration(row);
        if (!generation)
            throw new RosterError(409, 'ROSTER60_AUGMENT_QUEUE_REJOIN_REQUIRED');
        return { version: 'roster60-current-queue-v1', generation, playerId: row.playerId, mode: row.mode, queuedAt: row.queuedAt.toISOString(), ownedIdsSha256: digest(row.ownedIds), ...currentAugmentClient };
    }
    matchesQueueProof(row, raw) {
        if (!raw || typeof raw !== 'object')
            return false;
        const p = raw;
        return p.version === 'roster60-current-queue-v1' && typeof p.generation === 'string' && uuid4.test(p.generation) && p.generation === this.queueGeneration(row) && p.playerId === row.playerId && p.mode === row.mode && p.queuedAt === row.queuedAt.toISOString() && p.ownedIdsSha256 === digest(row.ownedIds) && p.capability === CURRENT_AUGMENT_CAPABILITY && p.modelVersion === CURRENT_AUGMENT_MODEL;
    }
    async readQueueProof(tx, row) {
        const saved = await tx.appMeta.findUnique({ where: { key: this.queueKey(row.playerId) } });
        let p;
        try {
            p = saved ? JSON.parse(saved.value) : null;
        }
        catch {
            return null;
        }
        return this.matchesQueueProof(row, p) ? p : null;
    }
    async clearQueueProof(tx, playerId) { await tx.appMeta.deleteMany({ where: { key: this.queueKey(playerId) } }); }
    async captureQueueProof(tx, row) {
        await this.clearQueueProof(tx, row.playerId);
        const proof = this.makeQueueProof(row), key = this.queueKey(row.playerId), value = JSON.stringify(proof);
        await tx.appMeta.create({ data: { key, value } });
        return proof;
    }
}
