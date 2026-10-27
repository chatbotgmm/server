-- Additive, local-review migration. Do not apply to a live player database without approval.
CREATE TABLE "Roster60StoryAccount" (
 "playerId" TEXT NOT NULL PRIMARY KEY,
 "revision" INTEGER NOT NULL DEFAULT 0,
 "contentVersion" TEXT NOT NULL,
 "state" TEXT NOT NULL,
 "darkGangBalance" INTEGER NOT NULL DEFAULT 0,
 "darkGangLifetime" INTEGER NOT NULL DEFAULT 0,
 "updatedAt" DATETIME NOT NULL,
 CONSTRAINT "Roster60StoryAccount_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE TABLE "Roster60StoryReceipt" (
 "playerId" TEXT NOT NULL,
 "requestId" TEXT NOT NULL,
 "fingerprint" TEXT NOT NULL,
 "result" TEXT NOT NULL,
 "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY ("playerId", "requestId"),
 CONSTRAINT "Roster60StoryReceipt_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE TABLE "Roster60StoryBattleAward" (
 "playerId" TEXT NOT NULL,
 "battleId" TEXT NOT NULL,
 "questId" TEXT,
 "amount" INTEGER NOT NULL,
 "outcome" TEXT NOT NULL,
 "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY ("playerId", "battleId"),
 CONSTRAINT "Roster60StoryBattleAward_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
