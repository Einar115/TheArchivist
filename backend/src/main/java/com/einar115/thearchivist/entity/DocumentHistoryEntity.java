package com.einar115.thearchivist.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_history")
public class DocumentHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Integer id;

    @Column(name = "document_Id", nullable = false)
    private UUID documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private DocumentStatusEnum status = DocumentStatusEnum.PENDING;

    public DocumentHistoryEntity() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public DocumentStatusEnum getStatus() {
        return status;
    }

    public void setStatus(DocumentStatusEnum status) {
        this.status = status;
    }

    public enum DocumentStatusEnum {
        PENDING,
        APPROVED,
        REJECTED
    }

}
