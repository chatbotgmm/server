-- Additive review artifact. Apply only through a separately authorized deployment.
CREATE TABLE "Roster60ObservedDefense" (
 "playerId" TEXT NOT NULL,
 "speciesId" TEXT NOT NULL,
 "profileKey" TEXT NOT NULL,
 "element" TEXT NOT NULL CHECK ("element" IN ('NORMAL','FIRE','WATER','LIGHTNING','ICE','NATURE','HOLY','DARK')),
 "reaction" TEXT NOT NULL CHECK ("reaction" IN ('weak','normal','resist','immune','reflect')),
 "firstObservedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 "lastObservedAt" DATETIME NOT NULL,
 PRIMARY KEY ("playerId","speciesId","profileKey","element"),
 FOREIGN KEY ("playerId") REFERENCES "Player"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "Roster60ObservedDefense_playerId_speciesId_idx" ON "Roster60ObservedDefense"("playerId","speciesId");
