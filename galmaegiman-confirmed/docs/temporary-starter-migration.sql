-- REFERENCE ONLY — superseded for the V2/onboarding/news combined batch.
-- DO NOT EXECUTE this starter-only draft. Use docs/migrations/v2-onboarding-news.sql ONCE.
-- Never run both scripts: both add the same columns/tables.
-- 검토용 DDL 초안만. 실행하지 않음. 배포/마이그레이션은 별도 승인 및 백업 검증 후.
ALTER TABLE "Player" ADD COLUMN "starterLoanEnrolled" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "Player" ADD COLUMN "starterLoanEligibleAt" DATETIME;
ALTER TABLE "OwnedCharacter" ADD COLUMN "loanExpiresAt" DATETIME;
CREATE TABLE "StarterLoanGrant" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "playerId" TEXT NOT NULL,
  "selectedIds" JSONB NOT NULL,
  "ownedIds" JSONB NOT NULL,
  "grantedAt" DATETIME NOT NULL,
  "expiresAt" DATETIME NOT NULL,
  CONSTRAINT "StarterLoanGrant_playerId_fkey" FOREIGN KEY ("playerId") REFERENCES "Player" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "StarterLoanGrant_playerId_key" ON "StarterLoanGrant"("playerId");
-- 기존 사용자의 enrolled/eligible 값을 채우지 않는다. 소급 지급 없음.
