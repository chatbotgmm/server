-- Reviewed manual operator step only; no automatic production migration.
BEGIN IMMEDIATE;
-- 추가 테이블만 생성합니다. 기존 Player 값/과거 전적은 수정하거나 이관하지 않습니다.
CREATE TABLE "RankedProfile" (
  "playerId" TEXT NOT NULL PRIMARY KEY,
  "tierIndex" INTEGER NOT NULL DEFAULT 0,
  "lp" INTEGER NOT NULL DEFAULT 0,
  "promotionActive" BOOLEAN NOT NULL DEFAULT false,
  "promotionWins" INTEGER NOT NULL DEFAULT 0,
  "promotionLosses" INTEGER NOT NULL DEFAULT 0,
  "rankedMmr" REAL NOT NULL DEFAULT 1000,
  "casualPriorOffset" REAL NOT NULL DEFAULT 0,
  "games" INTEGER NOT NULL DEFAULT 0,
  "wins" INTEGER NOT NULL DEFAULT 0,
  "losses" INTEGER NOT NULL DEFAULT 0,
  "draws" INTEGER NOT NULL DEFAULT 0,
  "version" INTEGER NOT NULL DEFAULT 0,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" DATETIME NOT NULL
);
CREATE INDEX "RankedProfile_tierIndex_lp_idx" ON "RankedProfile"("tierIndex", "lp");

CREATE TABLE "CasualHumanRating" (
  "playerId" TEXT NOT NULL PRIMARY KEY,
  "mmr" REAL NOT NULL DEFAULT 1000,
  "games" INTEGER NOT NULL DEFAULT 0,
  "wins" INTEGER NOT NULL DEFAULT 0,
  "losses" INTEGER NOT NULL DEFAULT 0,
  "draws" INTEGER NOT NULL DEFAULT 0,
  "version" INTEGER NOT NULL DEFAULT 0,
  "updatedAt" DATETIME NOT NULL
);

CREATE TABLE "RankedMatch" (
  "roomId" TEXT NOT NULL PRIMARY KEY,
  "player0Id" TEXT NOT NULL,
  "player1Id" TEXT NOT NULL,
  "participant0Kind" TEXT NOT NULL,
  "participant1Kind" TEXT NOT NULL,
  "state" TEXT NOT NULL DEFAULT 'ACTIVE',
  "winner" INTEGER,
  "reason" TEXT,
  "configJson" TEXT NOT NULL,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "finishedAt" DATETIME,
  "version" INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX "RankedMatch_state_createdAt_idx" ON "RankedMatch"("state", "createdAt");
CREATE INDEX "RankedMatch_player0Id_createdAt_idx" ON "RankedMatch"("player0Id", "createdAt");
CREATE INDEX "RankedMatch_player1Id_createdAt_idx" ON "RankedMatch"("player1Id", "createdAt");

CREATE TABLE "RankedSettlement" (
  "roomId" TEXT NOT NULL PRIMARY KEY,
  "receiptJson" TEXT NOT NULL,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "CasualHumanSettlement" (
  "roomId" TEXT NOT NULL PRIMARY KEY,
  "player0Id" TEXT NOT NULL,
  "player1Id" TEXT NOT NULL,
  "winner" INTEGER,
  "receiptJson" TEXT NOT NULL,
  "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX "CasualHumanSettlement_player0Id_createdAt_idx" ON "CasualHumanSettlement"("player0Id", "createdAt");
CREATE INDEX "CasualHumanSettlement_player1Id_createdAt_idx" ON "CasualHumanSettlement"("player1Id", "createdAt");

-- Additive transport tables only. Apply together with ranked-table SQL in one reviewed transaction.
CREATE TABLE "RealtimeAttempt" (
 "id" TEXT NOT NULL PRIMARY KEY, "playerId" TEXT NOT NULL, "mode" TEXT NOT NULL,
 "state" TEXT NOT NULL DEFAULT 'ISSUED', "issuedAt" DATETIME NOT NULL, "joinNotAfter" DATETIME NOT NULL,
 "queuedAt" DATETIME, "leaseUntil" DATETIME, "rosterJson" TEXT, "playerName" TEXT,
 "rating" INTEGER NOT NULL DEFAULT 1000, "tierIndex" INTEGER NOT NULL DEFAULT 0, "roomId" TEXT
);
CREATE INDEX "RealtimeAttempt_mode_state_queuedAt_idx" ON "RealtimeAttempt"("mode","state","queuedAt");
CREATE INDEX "RealtimeAttempt_playerId_state_idx" ON "RealtimeAttempt"("playerId","state");
CREATE TABLE "RealtimeActivity" (
 "playerId" TEXT NOT NULL PRIMARY KEY, "kind" TEXT NOT NULL, "targetId" TEXT NOT NULL, "expiresAt" DATETIME NOT NULL
);
CREATE TABLE "RealtimeRoom" (
 "id" TEXT NOT NULL PRIMARY KEY, "mode" TEXT NOT NULL, "player0Id" TEXT NOT NULL, "player1Id" TEXT NOT NULL,
 "namesJson" TEXT NOT NULL, "state" TEXT NOT NULL DEFAULT 'ACTIVE', "stateJson" TEXT NOT NULL,
 "resultJson" TEXT, "revision" INTEGER NOT NULL DEFAULT 0, "deadline" DATETIME,
 "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "updatedAt" DATETIME NOT NULL
);
CREATE INDEX "RealtimeRoom_state_deadline_idx" ON "RealtimeRoom"("state","deadline");
CREATE INDEX "RealtimeRoom_player0Id_state_idx" ON "RealtimeRoom"("player0Id","state");
CREATE INDEX "RealtimeRoom_player1Id_state_idx" ON "RealtimeRoom"("player1Id","state");
CREATE TABLE "RealtimeRequest" (
 "id" TEXT NOT NULL PRIMARY KEY, "roomId" TEXT NOT NULL, "playerId" TEXT NOT NULL, "requestId" TEXT NOT NULL,
 "fingerprint" TEXT NOT NULL, "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX "RealtimeRequest_roomId_playerId_requestId_key" ON "RealtimeRequest"("roomId","playerId","requestId");

CREATE TABLE "RealtimeLineup" (
  "playerId" TEXT NOT NULL PRIMARY KEY,
  "teamJson" TEXT NOT NULL,
  "updatedAt" DATETIME NOT NULL
);

CREATE TABLE "RealtimeItemEscrow" (
  "roomId" TEXT NOT NULL, "phaseId" TEXT NOT NULL, "side" INTEGER NOT NULL,
  "playerId" TEXT NOT NULL, "itemId" TEXT NOT NULL,
  "state" TEXT NOT NULL DEFAULT 'HELD', "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY ("roomId", "phaseId", "side")
);
CREATE INDEX "RealtimeItemEscrow_playerId_itemId_state_idx" ON "RealtimeItemEscrow"("playerId","itemId","state");
CREATE INDEX "RealtimeItemEscrow_roomId_state_idx" ON "RealtimeItemEscrow"("roomId","state");

COMMIT;
