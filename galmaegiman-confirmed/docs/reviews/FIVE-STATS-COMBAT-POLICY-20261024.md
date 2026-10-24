# Confirmed principles and injectable candidate calculations

Confirmed: consumable MP; common level power scaling; all attack damage receives diminishing-return proportional durability mitigation; status resistance reduces application chance only, not duration; final speed includes future supplied synergy/buff adjustments, affects ordering only, ties randomized by server. No additional action, no inherited legacy priority mechanics.

Implemented candidate family (NOT approved coefficients/formula):
- `power * (1 + levelSlope*(level-1)) / (1 + durability/durabilityScale)` before confirmed aptitude/element reaction.
- `baseStatusChance / (1 + statusResistance/resistanceScale)`; duration is absent from this calculation, and no element defense profile is involved.
- Explicit model identifier `linear-level-hyperbolic-v1`, version and positive scales required. No coefficient default. Product catalog reader validates an optional supplied combatPolicy; absence is not permission to run new combat.
- Reflection is rejected unless a separate server reflection strategy is supplied; existing resolution returns attacker damage with canReflectAgain=false. No reflection formula approval is inferred.
- Negative/NaN/infinite durability and resistance rejected; finite extremes approach zero; status chance requires0..1; negative aptitude cannot heal; immune is0.

Level/durability interaction experiment, not roster balance: power100, g=.04, K100, baseline durability50. With durability held50, levels1/20/60/99 yield pre-aptitude damage66.67/117.33/224/328. If durability grows by the same linear factor as level power, those become66.67/93.62/125.37/142.20. Thus growing both at the same rate flattens high-level offense. Recommendation: approve the model direction first, then jointly tune durability growth and levelSlope instead of multiplying an old attack stat or selecting coefficients independently. These numbers are illustrative fixtures only; no60-unit simulation was resumed.

Turn contract: decideTurnOrder takes server-owned persisted seed, battle ID, turn and canonical fighter identities; it computes supplied final speeds (finite sum, negative resulting speed clamped0), hashes ties deterministically, returns one action position per fighter. Retry uses the saved decision without reroll; inputs are canonically ordered so request array order cannot flip the result. Negative-final-speed clamping is an explicit implementation convention for review, not a newly approved buff design.

Actual current integration: optional policy validation at the product roster catalog loader, plus the preceding five-stat DTO/UI checkpoint. Not yet integrated: battle seed/decision persistence, action receipts, ownership enforcement for new combat, real new combat route/Android transport. The pure turn helper cannot by itself claim persisted replay/reconnect correctness. No production battle execution should call it with a client seed. Existing product battle is still legacy. Five-stat rebirth bonuses remain gated.

Tests: injected coefficients, diminishing mitigation, level growth, invalid/extreme inputs, chance bounds, immunity, explicit reflection/no repeat, final speed modifiers, tie reproducibility/input-order independence, saved turn identity. Device and authenticated durable new-battle tests not run because that connection is unfinished.
