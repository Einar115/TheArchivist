-- Computed by the server on submit.
ALTER TABLE documents
    ADD COLUMN checksum VARCHAR(64) NOT NULL,
    ADD COLUMN mime_type VARCHAR(100),
    ADD COLUMN char_count INT;

-- A rejected document may be submitted again; any other status counts as a duplicate.
CREATE UNIQUE INDEX uq_documents_checksum_active ON documents(checksum) WHERE status <> 'REJECTED';

-- Provided by the uploader in DocumentMetadataRequest.
ALTER TABLE documents
    ADD COLUMN universe VARCHAR(100) NOT NULL,
    ADD COLUMN work VARCHAR(150),
    ADD COLUMN lang VARCHAR(2) NOT NULL,
    ADD COLUMN title VARCHAR(200),
    ADD COLUMN canonicity VARCHAR(20) NOT NULL,
    ADD COLUMN document_type VARCHAR(20),
    ADD COLUMN author VARCHAR(150),
    ADD COLUMN origin_url VARCHAR(500);
