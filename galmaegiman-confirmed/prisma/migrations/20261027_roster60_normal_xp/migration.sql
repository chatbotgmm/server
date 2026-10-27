-- Additive local-review migration only. No live DB application is authorized.
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "normalXpSnapshot" TEXT;
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "normalXpCatalogVersion" TEXT;
CREATE INDEX "Roster60PvpQueue_mode_normalXpCatalogVersion_leaseUntil_queuedAt_idx" ON "Roster60PvpQueue"("mode", "normalXpCatalogVersion", "leaseUntil", "queuedAt");
CREATE TABLE "Roster60NormalXpLedger" (
 "playerId" TEXT NOT NULL,
 "roomId" TEXT NOT NULL,
 "ownedId" TEXT NOT NULL,
 "policyVersion" TEXT,
 "outcome" TEXT NOT NULL,
 "reason" TEXT NOT NULL,
 "status" TEXT NOT NULL,
 "requestedXp" INTEGER NOT NULL,
 "appliedXp" INTEGER NOT NULL,
 "deferredXp" INTEGER NOT NULL,
 "startingXp" TEXT NOT NULL,
 "endingXp" TEXT NOT NULL,
 "sourceEncounterId" TEXT,
 "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY ("playerId", "roomId", "ownedId"),
 CONSTRAINT "Roster60NormalXpLedger_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "Roster60NormalXpLedger_playerId_createdAt_idx" ON "Roster60NormalXpLedger"("playerId", "createdAt");
