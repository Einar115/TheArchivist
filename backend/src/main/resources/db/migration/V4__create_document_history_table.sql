CREATE TABLE document_history (
    history_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id UUID NOT NULL UNIQUE,
    user_id INT,
    filename VARCHAR(255) NOT NULL,
    file_size BIGINT,
    uploaded_at TIMESTAMP NOT NULL,
    chunk_count INT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    content TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE INDEX idx_document_history_user_id ON document_history(user_id);
CREATE INDEX idx_document_history_status ON document_history(status);