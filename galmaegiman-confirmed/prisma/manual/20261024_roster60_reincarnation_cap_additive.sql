-- REVIEW ONLY. Not executed against production. No reincarnation grant/reset policy.
ALTER TABLE Roster60Owned ADD COLUMN reincarnation INTEGER NOT NULL DEFAULT 0;
