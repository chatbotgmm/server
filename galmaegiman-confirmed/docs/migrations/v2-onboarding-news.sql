-- ONE-TIME reviewed upgrade from pinned 02a46 schema. Not idempotent.
-- Stop the service and take a verified backup first. No reward/backfill writes.
PRAGMA foreign_keys=ON;
BEGIN IMMEDIATE;
-- AlterTable
ALTER TABLE "OwnedCharacter" ADD COLUMN "loanExpiresAt" DATETIME;

-- CreateTable
CREATE TABLE "StarterLoanGrant" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "playerId" TEXT NOT NULL,
    "selectedIds" JSONB NOT NULL,
    "ownedIds" JSONB NOT NULL,
    "grantedAt" DATETIME NOT NULL,
    "expiresAt" DATETIME NOT NULL,
    CONSTRAINT "StarterLoanGrant_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "NativeInboxMessage" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "playerId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "bodyText" TEXT NOT NULL,
    "publishedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" DATETIME,
    CONSTRAINT "NativeInboxMessage_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "NativeNewsRead" (
    "playerId" TEXT NOT NULL,
    "kind" TEXT NOT NULL,
    "itemId" TEXT NOT NULL,
    "readAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY ("playerId", "kind", "itemId"),
    CONSTRAINT "NativeNewsRead_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "NativeNewsRequest" (
    "playerId" TEXT NOT NULL,
    "requestId" TEXT NOT NULL,
    "kind" TEXT NOT NULL,
    "itemId" TEXT NOT NULL,
    "readAt" DATETIME NOT NULL,

    PRIMARY KEY ("playerId", "requestId"),
    CONSTRAINT "NativeNewsRequest_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "StarterLoanLevel" (
    "loanOwnedId" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "playerId" TEXT NOT NULL,
    "characterId" TEXT NOT NULL,
    "grantId" TEXT NOT NULL,
    "highestLevel" INTEGER NOT NULL DEFAULT 1,
    "recipientOwnedId" INTEGER,
    "lastSession" TEXT,
    "createdAt" DATETIME NOT NULL,
    "updatedAt" DATETIME NOT NULL,
    "boundAt" DATETIME
);

-- Additive Player columns: avoid Prisma table rebuild and preserve existing FKs.
ALTER TABLE "Player" ADD COLUMN "starterLoanEnrolled" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "Player" ADD COLUMN "starterLoanEligibleAt" DATETIME;

-- CreateIndex
CREATE UNIQUE INDEX "StarterLoanGrant_playerId_key" ON "StarterLoanGrant"("playerId");

-- CreateIndex
CREATE INDEX "NativeInboxMessage_playerId_publishedAt_id_idx" ON "NativeInboxMessage"("playerId", "publishedAt", "id");

-- CreateIndex
CREATE UNIQUE INDEX "StarterLoanLevel_recipientOwnedId_key" ON "StarterLoanLevel"("recipientOwnedId");

-- CreateIndex
CREATE INDEX "StarterLoanLevel_playerId_characterId_idx" ON "StarterLoanLevel"("playerId", "characterId");


COMMIT;
