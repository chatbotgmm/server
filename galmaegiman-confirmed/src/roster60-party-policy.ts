/** New single-active battles accept one starter plus at most two reserves.
 * Existing battle/adventure snapshots keep their pinned parties. */
export const roster60PartyPolicy={version:'single-active-party-3-v1',minParty:1,maxParty:3,activeSlots:1,sizeRule:'up-to-max'} as const;

export function isCurrentRoster60Party(ids:unknown):boolean{
 return Array.isArray(ids)&&ids.length>=roster60PartyPolicy.minParty&&ids.length<=roster60PartyPolicy.maxParty&&ids.every(id=>typeof id==='string'&&id.length>0)&&new Set(ids).size===ids.length;
}

export function roster60PartySelection(ids:unknown){
 const partySelectionRequired=!isCurrentRoster60Party(ids);
 return {partyPolicy:roster60PartyPolicy,minParty:roster60PartyPolicy.minParty,maxParty:roster60PartyPolicy.maxParty,partySelectionRequired,partyCondition:partySelectionRequired?'PARTY_RESELECTION_REQUIRED' as const:null};
}
