ALTER TABLE documents
    ADD COLUMN rejection_reason VARCHAR(500),
    ADD COLUMN reviewed_at TIMESTAMP,
    ADD COLUMN reviewed_by INT,
    ADD CONSTRAINT fk_documents_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(user_id) ON DELETE SET NULL;
