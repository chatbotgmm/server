-- CreateTable
CREATE TABLE "Roster60State" (
    "playerId" TEXT NOT NULL PRIMARY KEY,
    "starterId" TEXT NOT NULL,
    "tutorialProof" TEXT,
    "completedAt" DATETIME,
    "mailId" TEXT,
    "selectedId" TEXT,
    "rewardOwnedId" TEXT,
    "catalogVersion" TEXT,
    CONSTRAINT "Roster60State_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Roster60Owned" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "playerId" TEXT NOT NULL,
    "speciesId" TEXT NOT NULL,
    "totalXp" BIGINT NOT NULL DEFAULT 0,
    "source" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "Roster60Owned_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateIndex
CREATE UNIQUE INDEX "Roster60State_starterId_key" ON "Roster60State"("starterId");

-- CreateIndex
CREATE UNIQUE INDEX "Roster60State_mailId_key" ON "Roster60State"("mailId");

-- CreateIndex
CREATE UNIQUE INDEX "Roster60State_rewardOwnedId_key" ON "Roster60State"("rewardOwnedId");

-- CreateIndex
CREATE INDEX "Roster60Owned_playerId_speciesId_idx" ON "Roster60Owned"("playerId", "speciesId");

-- CreateIndex
CREATE UNIQUE INDEX "Roster60Owned_playerId_source_key" ON "Roster60Owned"("playerId", "source");

