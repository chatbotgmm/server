-- 수동 검토용 SQLite 추가 스키마. 운영 DB 실행은 별도 승인·백업 후 1회만 합니다.
ALTER TABLE "OwnedCharacter" ADD COLUMN "reincarnation" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "OwnedCharacter" ADD COLUMN "inheritedFrom" TEXT;
ALTER TABLE "OwnedCharacter" ADD COLUMN "statBonusPct" INTEGER NOT NULL DEFAULT 0;
CREATE TABLE "ProgressionState" (
 "playerId" TEXT NOT NULL PRIMARY KEY,
 "points" INTEGER NOT NULL DEFAULT 0,
 "pointDay" TEXT NOT NULL DEFAULT '',
 "dailyPoints" INTEGER NOT NULL DEFAULT 0,
 "rerollAvailable" BOOLEAN NOT NULL DEFAULT false,
 "rerollExpiresAt" DATETIME,
 "rerollNextClaimAt" DATETIME,
 "reassessNextAt" DATETIME
);
CREATE TABLE "ProgressionReceipt" (
 "playerId" TEXT NOT NULL,
 "requestId" TEXT NOT NULL,
 "fingerprint" TEXT NOT NULL,
 "result" TEXT NOT NULL,
 "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY ("playerId", "requestId")
);
CREATE TABLE "ProgressionMilestone" (
 "playerId" TEXT NOT NULL,
 "milestoneId" TEXT NOT NULL,
 "claimedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY ("playerId", "milestoneId")
);
