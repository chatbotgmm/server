import type {PrismaClient} from '@prisma/client';

/** Read existing encounter/acquisition records only; no new discovery storage or defense disclosure. */
export async function discoveredRosterSpecies(db:PrismaClient,playerId:string):Promise<Set<string>> {
 const [owned,observed,encountered]=await Promise.all([
  db.roster60Owned.findMany({where:{playerId},select:{speciesId:true},distinct:['speciesId']}),
  db.roster60ObservedDefense.findMany({where:{playerId},select:{speciesId:true},distinct:['speciesId']}),
  // Project identifiers in SQLite instead of transferring/parsing full historical battle JSON
  // on every menu entry. No count/age cutoff: an old encounter remains discovered.
  db.$queryRaw<{speciesId:string}[]>`
   WITH encounters AS (
    SELECT CASE WHEN json_valid(state) THEN state ELSE '{}' END AS body,1 AS enemy,'single' AS kind
    FROM Roster60Battle WHERE playerId=${playerId}
    UNION ALL
    SELECT CASE WHEN json_valid(state) THEN state ELSE '{}' END AS body,
     CASE WHEN player0Id=${playerId} THEN 1 ELSE 0 END AS enemy,'shared' AS kind
    FROM Roster60PvpMatch
    WHERE (player0Id=${playerId} OR player1Id=${playerId}) AND status<>'ACCEPTING'
   ), visible AS (
    SELECT * FROM encounters WHERE kind='single' OR
     (json_extract(body,'$.accepted[0]')=1 AND json_extract(body,'$.accepted[1]')=1)
   ), steps AS (
    SELECT enemy,CASE WHEN json_valid(step.value) THEN step.value ELSE '{}' END AS event
    FROM visible,
     json_each(CASE WHEN json_type(body,'$.events')='array' THEN json_extract(body,'$.events') ELSE '[]' END) AS round,
     json_each(CASE WHEN json_valid(round.value) THEN CASE WHEN json_type(round.value,'$.events')='array' THEN json_extract(round.value,'$.events') ELSE '[]' END ELSE '[]' END) AS step
   ), frames AS (
    SELECT json_extract(body,'$.fighters['||enemy||']') AS frame FROM visible WHERE kind='single'
    UNION ALL SELECT json_extract(body,'$.parties['||enemy||'][0]') AS frame FROM visible WHERE kind='shared'
    UNION ALL SELECT json_extract(event,'$.presentation.before['||enemy||']') AS frame FROM steps
    UNION ALL SELECT json_extract(event,'$.presentation.after['||enemy||']') AS frame FROM steps
   )
   SELECT DISTINCT json_extract(CASE WHEN json_valid(frame) THEN frame ELSE '{}' END,'$.speciesId') AS speciesId
   FROM frames WHERE json_type(CASE WHEN json_valid(frame) THEN frame ELSE '{}' END,'$.speciesId')='text'
  `
 ]);
 return new Set([...owned,...observed,...encountered].map(row=>row.speciesId));
}
