-- Additive local-review migration. Apply only with separate live-schema authorization.
-- Both modes require a newly verified pool; NULL old queues must cancel/re-enter.
ALTER TABLE "Roster60PvpQueue" ADD COLUMN "matchPoolPolicyVersion" TEXT;
CREATE INDEX "Roster60PvpQueue_mode_matchPoolPolicyVersion_reincarnationPool_normalXpCatalogVersion_leaseUntil_queuedAt_idx"
 ON "Roster60PvpQueue"("mode","matchPoolPolicyVersion","reincarnationPool","normalXpCatalogVersion","leaseUntil","queuedAt");
