-- 공지·이미지 관리(content-admin) 새 표 5개. Additive only: 기존 표·데이터는 바꾸지 않는다.
-- 검토용 파일이다. 이 브랜치 작업에서는 어떤 실제 DB 에도 실행하지 않았다(임시 시험 DB 에만 적용).
-- 기준 스키마: 0b0fb8b. 생성: prisma migrate diff --from-url <기준 스키마 임시 DB> --to-schema-datamodel prisma/schema.prisma

-- CreateTable
CREATE TABLE "ContentNotice" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "status" TEXT NOT NULL,
    "pinned" BOOLEAN NOT NULL DEFAULT false,
    "title" TEXT NOT NULL,
    "bodySource" TEXT NOT NULL,
    "bodyHtml" TEXT NOT NULL,
    "imagesJson" TEXT NOT NULL DEFAULT '[]',
    "version" INTEGER NOT NULL,
    "createdAt" DATETIME NOT NULL,
    "updatedAt" DATETIME NOT NULL,
    "publishedAt" DATETIME,
    "createdBy" TEXT NOT NULL,
    "updatedBy" TEXT NOT NULL
);

-- CreateTable
CREATE TABLE "ContentNoticeRevision" (
    "noticeId" TEXT NOT NULL,
    "version" INTEGER NOT NULL,
    "action" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "pinned" BOOLEAN NOT NULL,
    "title" TEXT NOT NULL,
    "bodySource" TEXT NOT NULL,
    "bodyHtml" TEXT NOT NULL,
    "imagesJson" TEXT NOT NULL,
    "editedBy" TEXT NOT NULL,
    "editedAt" DATETIME NOT NULL,

    PRIMARY KEY ("noticeId", "version"),
    CONSTRAINT "ContentNoticeRevision_noticeId_fkey" FOREIGN KEY ("noticeId") REFERENCES "ContentNotice" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ContentMedia" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "format" TEXT NOT NULL,
    "mime" TEXT NOT NULL,
    "width" INTEGER NOT NULL,
    "height" INTEGER NOT NULL,
    "bytes" INTEGER NOT NULL,
    "sha256" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "createdBy" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "ContentAdminRequest" (
    "actorId" TEXT NOT NULL,
    "requestId" TEXT NOT NULL,
    "operation" TEXT NOT NULL,
    "payloadHash" TEXT NOT NULL,
    "response" TEXT NOT NULL,
    "status" INTEGER NOT NULL,
    "createdAt" DATETIME NOT NULL,

    PRIMARY KEY ("actorId", "requestId")
);

-- CreateTable
CREATE TABLE "ContentAdminAudit" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "at" DATETIME NOT NULL,
    "actorId" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "targetType" TEXT NOT NULL,
    "targetId" TEXT NOT NULL,
    "detailJson" TEXT NOT NULL
);

-- CreateIndex
CREATE INDEX "ContentNotice_status_pinned_publishedAt_idx" ON "ContentNotice"("status", "pinned", "publishedAt");

-- CreateIndex
CREATE INDEX "ContentMedia_sha256_idx" ON "ContentMedia"("sha256");

-- CreateIndex
CREATE INDEX "ContentAdminAudit_at_idx" ON "ContentAdminAudit"("at");

