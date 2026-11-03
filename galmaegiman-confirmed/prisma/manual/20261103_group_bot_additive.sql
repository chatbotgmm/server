-- 운영자가 검토한 뒤 직접 적용하는 수동 단계입니다(자동 운영 이전 없음). 아직 검토 전입니다.
-- 단체방(그룹) 챗봇 표 추가(src/group/). 기존 표와 값은 바꾸지 않습니다.
-- 적용 전 백업: npm run backup. 적용 후 Prisma client 재생성(npm run db:generate).
BEGIN IMMEDIATE;
-- CreateTable
CREATE TABLE "GroupUserKey" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "env" TEXT NOT NULL,
    "kind" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "playerId" TEXT NOT NULL,
    "firstSeen" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastSeen" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "GroupRoom" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "env" TEXT NOT NULL,
    "groupKey" TEXT NOT NULL,
    "settings" TEXT NOT NULL DEFAULT '{}',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "RoomMember" (
    "roomId" TEXT NOT NULL,
    "playerId" TEXT NOT NULL,
    "joinedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastActiveAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY ("roomId", "playerId")
);

-- CreateTable
CREATE TABLE "RoomDex" (
    "roomId" TEXT NOT NULL,
    "characterId" TEXT NOT NULL,
    "firstPlayerId" TEXT,
    "firstAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY ("roomId", "characterId")
);

-- CreateTable
CREATE TABLE "RoomWeek" (
    "roomId" TEXT NOT NULL,
    "playerId" TEXT NOT NULL,
    "week" TEXT NOT NULL,
    "finds" INTEGER NOT NULL DEFAULT 0,
    "crafts" INTEGER NOT NULL DEFAULT 0,
    "recruits" INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY ("roomId", "playerId", "week")
);

-- CreateTable
CREATE TABLE "ProactiveLog" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "env" TEXT NOT NULL,
    "month" TEXT NOT NULL,
    "summary" TEXT NOT NULL,
    "sentAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateIndex
CREATE INDEX "GroupUserKey_playerId_idx" ON "GroupUserKey"("playerId");

-- CreateIndex
CREATE UNIQUE INDEX "GroupUserKey_env_kind_value_key" ON "GroupUserKey"("env", "kind", "value");

-- CreateIndex
CREATE UNIQUE INDEX "GroupRoom_env_groupKey_key" ON "GroupRoom"("env", "groupKey");

-- CreateIndex
CREATE INDEX "RoomMember_playerId_idx" ON "RoomMember"("playerId");

-- CreateIndex
CREATE INDEX "RoomDex_firstPlayerId_idx" ON "RoomDex"("firstPlayerId");

-- CreateIndex
CREATE INDEX "RoomWeek_playerId_idx" ON "RoomWeek"("playerId");

-- CreateIndex
CREATE INDEX "ProactiveLog_env_month_idx" ON "ProactiveLog"("env", "month");

COMMIT;
