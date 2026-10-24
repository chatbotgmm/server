-- CreateTable
CREATE TABLE "Roster60CraftReceipt" (
    "playerId" TEXT NOT NULL,
    "requestId" TEXT NOT NULL,
    "fingerprint" TEXT NOT NULL,
    "result" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY ("playerId", "requestId"),
    CONSTRAINT "Roster60CraftReceipt_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- Add fields in place; no table rewrite, drop, or data reset. Review before deployment.
ALTER TABLE "Roster60Owned" ADD COLUMN "status" TEXT NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE "Roster60Owned" ADD COLUMN "locked" BOOLEAN NOT NULL DEFAULT false;
