-- Review migration only. Do not apply to production without deployment approval.
CREATE TABLE "Roster60RecruitSession" (
 "id" TEXT NOT NULL PRIMARY KEY,
 "playerId" TEXT NOT NULL,
 "revision" INTEGER NOT NULL DEFAULT 0,
 "status" TEXT NOT NULL DEFAULT 'PENDING',
 "snapshot" TEXT NOT NULL,
 "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "Roster60RecruitSession_playerId_status_idx" ON "Roster60RecruitSession"("playerId", "status");
