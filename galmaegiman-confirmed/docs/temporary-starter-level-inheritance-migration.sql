-- 검토용 추가 DDL. 실행하지 않음. 기존 임시 starter schema 위에 추가할 초안.
CREATE TABLE "StarterLoanLevel" (
  "loanOwnedId" INTEGER NOT NULL PRIMARY KEY,
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
CREATE UNIQUE INDEX "StarterLoanLevel_recipientOwnedId_key" ON "StarterLoanLevel"("recipientOwnedId");
CREATE INDEX "StarterLoanLevel_playerId_characterId_idx" ON "StarterLoanLevel"("playerId", "characterId");
-- StorySave로 archive를 채우지 않음. 이 기능은 아직 게시되지 않은 starter grant와 함께 도입.
