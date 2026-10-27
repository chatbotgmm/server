-- Local-review additive migration. No account backfill or unit-XP inference.
-- Production initialization and curve require explicit policy approval.
CREATE TABLE "CommanderAccount" (
 "playerId" TEXT NOT NULL PRIMARY KEY,
 "totalXp" BIGINT NOT NULL DEFAULT 0,
 "policyVersion" TEXT NOT NULL,
 "policyFingerprint" TEXT NOT NULL,
 "revision" INTEGER NOT NULL DEFAULT 0,
 "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT "CommanderAccount_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE TABLE "CommanderXpLedger" (
 "playerId" TEXT NOT NULL,
 "eventKind" TEXT NOT NULL,
 "eventId" TEXT NOT NULL,
 "fingerprint" TEXT NOT NULL,
 "policyVersion" TEXT NOT NULL,
 "policyFingerprint" TEXT NOT NULL,
 "outcome" TEXT NOT NULL,
 "reason" TEXT NOT NULL,
 "status" TEXT NOT NULL,
 "requestedXp" INTEGER NOT NULL,
 "appliedXp" INTEGER NOT NULL,
 "startingXp" BIGINT NOT NULL,
 "endingXp" BIGINT NOT NULL,
 "result" TEXT NOT NULL,
 "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY ("playerId","eventKind","eventId"),
 CONSTRAINT "CommanderXpLedger_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "CommanderXpLedger_playerId_createdAt_idx" ON "CommanderXpLedger"("playerId","createdAt");

-- Null preserves an unconfigured or already queued game without retroactive rewards.
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "commanderXpSnapshot" TEXT;
