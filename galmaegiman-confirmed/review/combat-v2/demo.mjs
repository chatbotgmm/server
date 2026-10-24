import {exampleBattle} from './fixture.mjs';
const battle=exampleBattle(),auth={playerId:'review-a'};
// This DTO is consumed by CombatV2ReviewPanel.bind; no live API is called.
process.stdout.write(JSON.stringify(battle.snapshot(auth),null,2)+'\n');
