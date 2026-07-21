-- Ensure the accounts table exists before referencing it below.
-- TODO: Replace column definitions with the actual schema for your accounts model.
CREATE TABLE IF NOT EXISTS "accounts" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "provider" TEXT NOT NULL,
    "providerAccountId" TEXT NOT NULL,
    CONSTRAINT "accounts_pkey" PRIMARY KEY ("id")
);

-- existing migration SQL below (unchanged)
