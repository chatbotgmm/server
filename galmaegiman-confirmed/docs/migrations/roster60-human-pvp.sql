-- Review-only SQLite migration; apply only to approved deployment target after backup.
CREATE TABLE "Roster60PvpParty" ("playerId" TEXT NOT NULL PRIMARY KEY, "ownedIds" TEXT NOT NULL);
CREATE TABLE "Roster60PvpQueue" (
 "playerId" TEXT NOT NULL PRIMARY KEY, "mode" TEXT NOT NULL, "rating" INTEGER NOT NULL,
 "tierIndex" INTEGER NOT NULL, "ownedIds" TEXT NOT NULL, "queuedAt" DATETIME NOT NULL, "leaseUntil" DATETIME NOT NULL
);
CREATE TABLE "Roster60PvpMatch" (
 "id" TEXT NOT NULL PRIMARY KEY, "player0Id" TEXT NOT NULL, "player1Id" TEXT NOT NULL,
 "mode" TEXT NOT NULL, "status" TEXT NOT NULL DEFAULT 'ACCEPTING', "revision" INTEGER NOT NULL DEFAULT 0,
 "seed" TEXT NOT NULL, "snapshot" TEXT NOT NULL, "state" TEXT NOT NULL,
 "deadline" DATETIME NOT NULL, "settlement" TEXT, "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX "Roster60PvpMatch_player0Id_status_idx" ON "Roster60PvpMatch"("player0Id","status");
CREATE INDEX "Roster60PvpMatch_player1Id_status_idx" ON "Roster60PvpMatch"("player1Id","status");
