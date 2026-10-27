-- Additive local-review migration. No live DB application is authorized.
-- Old queues intentionally remain NULL: never infer their eligibility or history pool.
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "rankPolicyVersion" TEXT;
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "reincarnationPool" TEXT;
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "rankAdmissionSnapshot" TEXT;
CREATE INDEX "Roster60PvpQueue_mode_rankPolicyVersion_reincarnationPool_leaseUntil_queuedAt_idx"
 ON "Roster60PvpQueue"("mode","rankPolicyVersion","reincarnationPool","leaseUntil","queuedAt");
