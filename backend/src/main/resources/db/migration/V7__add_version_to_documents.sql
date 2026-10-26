-- Optimistic locking for DocumentEntity (@Version): existing rows start at version 0.
ALTER TABLE documents
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
