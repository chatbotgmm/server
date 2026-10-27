-- Additive review migration only; no backfill invents lifetime rebirth counts.
-- New queues pin a revised history policy; old queues must cancel and re-enter.
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "reincarnationCount" INTEGER;
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "reincarnationCountSource" TEXT;
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "reincarnationHistoryStamp" TEXT;
