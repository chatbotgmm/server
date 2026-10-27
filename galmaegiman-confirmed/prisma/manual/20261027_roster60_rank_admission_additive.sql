-- Review only. No account-level curve or existing account values are created here.
-- Apply only with separate live-schema authorization; old queues remain unverified.
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "rankPolicyVersion" TEXT;
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "reincarnationPool" TEXT;
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "rankAdmissionSnapshot" TEXT;
CREATE INDEX "Roster60PvpQueue_mode_rankPolicyVersion_reincarnationPool_leaseUntil_queuedAt_idx"
 ON "Roster60PvpQueue"("mode","rankPolicyVersion","reincarnationPool","leaseUntil","queuedAt");
