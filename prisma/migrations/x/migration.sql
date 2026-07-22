-- Create the accounts table before any statements that reference it
CREATE TABLE "accounts" (
    "id"         TEXT         NOT NULL,
    "userId"     TEXT         NOT NULL,
    "type"       TEXT         NOT NULL,
    "provider"   TEXT         NOT NULL,
    "providerAccountId" TEXT  NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "accounts_pkey" PRIMARY KEY ("id")
);

-- existing statements below (foreign keys, indexes, etc. that reference accounts)
