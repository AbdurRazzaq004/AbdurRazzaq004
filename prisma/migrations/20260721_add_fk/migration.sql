-- Ensure the 'accounts' table exists before adding the foreign key.
-- IMPORTANT: Verify this DDL matches your actual accounts schema before merging.
CREATE TABLE IF NOT EXISTS "accounts" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    CONSTRAINT "accounts_pkey" PRIMARY KEY ("id")
);

-- Add foreign key referencing accounts
