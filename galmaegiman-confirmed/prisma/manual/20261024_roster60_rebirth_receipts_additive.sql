-- Review only; no production execution. Existing account/currency values unchanged.
CREATE TABLE "Roster60RebirthAccount" ("playerId" TEXT NOT NULL PRIMARY KEY, "successes" INTEGER NOT NULL DEFAULT 0, FOREIGN KEY ("playerId") REFERENCES "Player"("id") ON DELETE CASCADE ON UPDATE CASCADE);
CREATE TABLE "Roster60RebirthReceipt" ("playerId" TEXT NOT NULL,"requestId" TEXT NOT NULL,"fingerprint" TEXT NOT NULL,"result" TEXT NOT NULL,PRIMARY KEY("playerId","requestId"),FOREIGN KEY("playerId") REFERENCES "Player"("id") ON DELETE CASCADE ON UPDATE CASCADE);
