-- CreateTable: ensure 'accounts' relation exists before any references to it
CREATE TABLE IF NOT EXISTS "accounts" (
    "id"         TEXT        NOT NULL,
    "userId"     TEXT        NOT NULL,
    "type"       TEXT        NOT NULL,
    "provider"   TEXT        NOT NULL,
    "providerAccountId" TEXT NOT NULL,
    "createdAt"  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "accounts_pkey" PRIMARY KEY ("id")
);

-- (existing migration SQL continues below)
