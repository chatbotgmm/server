-- Review only. No current account is implicitly assigned to either history pool.
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "matchPoolPolicyVersion" TEXT;
CREATE INDEX "Roster60PvpQueue_mode_matchPoolPolicyVersion_reincarnationPool_normalXpCatalogVersion_leaseUntil_queuedAt_idx"
 ON "Roster60PvpQueue"("mode","matchPoolPolicyVersion","reincarnationPool","normalXpCatalogVersion","leaseUntil","queuedAt");
