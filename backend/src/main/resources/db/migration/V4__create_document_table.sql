CREATE TABLE documents (
    document_id UUID PRIMARY KEY,
    uploaded_by INT,
    uploaded_at TIMESTAMP NOT NULL,
    filename VARCHAR(255) NOT NULL,
    file_size BIGINT,
    chunk_count INT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    content TEXT NOT NULL,
    FOREIGN KEY (uploaded_by) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE INDEX idx_documents_upload_by ON documents(uploaded_by);
CREATE INDEX idx_documents_status ON documents(status);