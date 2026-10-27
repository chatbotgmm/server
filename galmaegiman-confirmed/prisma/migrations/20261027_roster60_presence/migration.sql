-- Compact server observations; no past player presence is fabricated.
ALTER TABLE "Roster60PvpMatch" ADD COLUMN "presence" TEXT;
