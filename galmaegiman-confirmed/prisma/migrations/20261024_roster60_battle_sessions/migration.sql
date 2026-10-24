-- Additive only. Review artifact; not applied to production.
CREATE TABLE "Roster60Battle" (
 "id" TEXT NOT NULL PRIMARY KEY, "playerId" TEXT NOT NULL, "ownedId" TEXT NOT NULL,
 "revision" INTEGER NOT NULL DEFAULT 0, "status" TEXT NOT NULL DEFAULT 'ACTIVE',
 "seed" TEXT NOT NULL, "snapshot" TEXT NOT NULL, "state" TEXT NOT NULL,
 "settlement" TEXT, "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY ("playerId") REFERENCES "Player"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "Roster60Battle_playerId_status_idx" ON "Roster60Battle"("playerId", "status");
CREATE TABLE "Roster60BattleReceipt" (
 "playerId" TEXT NOT NULL, "requestId" TEXT NOT NULL, "fingerprint" TEXT NOT NULL,
 "result" TEXT NOT NULL, "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY ("playerId", "requestId"),
 FOREIGN KEY ("playerId") REFERENCES "Player"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
