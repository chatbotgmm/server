-- 공지·이미지 관리(content-admin) 새 표 6개. Additive only: 기존 표·데이터는 바꾸지 않는다.
-- 검토용 파일이다. 이 작업에서는 실제 DB 에 실행하지 않았다(시험용 임시 SQLite 파일에만 적용).
-- 기준 스키마: 0b0fb8b. 생성: prisma migrate diff --from-url <기준 스키마 임시 DB> --to-schema-datamodel <기준 + docs/content-admin/schema-models.prisma>
-- schema.prisma 에도 같은 모델을 넣어야 이후 prisma db push/migrate 가 이 표를 지우려 하지 않는다(docs/content-admin/integration-schema.patch).

-- CreateTable
CREATE TABLE "ContentNotice" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "status" TEXT NOT NULL,
    "version" INTEGER NOT NULL,
    "revision" INTEGER NOT NULL,
    "publishedRevision" INTEGER,
    "publishedAt" DATETIME,
    "createdAt" DATETIME NOT NULL,
    "updatedAt" DATETIME NOT NULL,
    "createdBy" TEXT NOT NULL,
    "updatedBy" TEXT NOT NULL
);

-- CreateTable
CREATE TABLE "ContentNoticeRevision" (
    "noticeId" TEXT NOT NULL,
    "revision" INTEGER NOT NULL,
    "action" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "pinned" BOOLEAN NOT NULL,
    "bodySource" TEXT NOT NULL,
    "bodyHtml" TEXT NOT NULL,
    "scriptSource" TEXT NOT NULL DEFAULT '',
    "restoredFrom" INTEGER,
    "editedBy" TEXT NOT NULL,
    "editedAt" DATETIME NOT NULL,

    PRIMARY KEY ("noticeId", "revision"),
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
CREATE TABLE "ContentNoticeImageRef" (
    "noticeId" TEXT NOT NULL,
    "revision" INTEGER NOT NULL,
    "position" INTEGER NOT NULL,
    "mediaId" TEXT NOT NULL,
    "alt" TEXT NOT NULL,

    PRIMARY KEY ("noticeId", "revision", "position"),
    CONSTRAINT "ContentNoticeImageRef_noticeId_revision_fkey" FOREIGN KEY ("noticeId", "revision") REFERENCES "ContentNoticeRevision" ("noticeId", "revision") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "ContentNoticeImageRef_mediaId_fkey" FOREIGN KEY ("mediaId") REFERENCES "ContentMedia" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ContentAdminRequest" (
    "actorId" TEXT NOT NULL,
    "requestId" TEXT NOT NULL,
    "operation" TEXT NOT NULL,
    "payloadHash" TEXT NOT NULL,
    "status" INTEGER NOT NULL,
    "response" TEXT NOT NULL,
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
CREATE INDEX "ContentNotice_status_publishedAt_idx" ON "ContentNotice"("status", "publishedAt");

-- CreateIndex
CREATE INDEX "ContentMedia_sha256_idx" ON "ContentMedia"("sha256");

-- CreateIndex
CREATE INDEX "ContentNoticeImageRef_mediaId_idx" ON "ContentNoticeImageRef"("mediaId");

-- CreateIndex
CREATE UNIQUE INDEX "ContentNoticeImageRef_noticeId_revision_mediaId_key" ON "ContentNoticeImageRef"("noticeId", "revision", "mediaId");

-- CreateIndex
CREATE INDEX "ContentAdminAudit_at_idx" ON "ContentAdminAudit"("at");

