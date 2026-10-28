package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.dto.request.DocumentMetadataRequest;
import com.einar115.thearchivist.dto.response.DocumentContentResponse;
import com.einar115.thearchivist.dto.response.DocumentSummaryResponse;
import com.einar115.thearchivist.dto.response.SubmitResponse;
import com.einar115.thearchivist.entity.DocumentEntity;
import com.einar115.thearchivist.exception.DocumentNotFoundException;
import com.einar115.thearchivist.exception.DuplicateDocumentException;
import com.einar115.thearchivist.exception.InvalidDocumentFileException;
import com.einar115.thearchivist.exception.InvalidDocumentStateException;
import com.einar115.thearchivist.exception.UnreadableDocumentException;
import com.einar115.thearchivist.exception.UnsupportedDocumentTypeException;
import com.einar115.thearchivist.repository.DocumentRepository;
import com.einar115.thearchivist.repository.UserRepository;
import com.einar115.thearchivist.service.DocumentService;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentServiceImpl.class);

    // Mirrors the filename column, VARCHAR(255).
    private static final int MAX_FILENAME_LENGTH = 255;

    // What Tika reports for the formats the frontend accepts: pdf, doc, docx, txt and md.
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown");

    // Thread-safe and costly to build, so one instance is shared.
    private static final Tika TIKA = new Tika();

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final UserRepository  userRepository;
    private final DocumentRepository documentRepository;

    public DocumentServiceImpl(VectorStore vectorStore, TokenTextSplitter splitter, UserRepository userRepository, DocumentRepository documentRepository) {
        this.vectorStore = vectorStore;
        this.splitter = splitter;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public SubmitResponse submit(MultipartFile file, DocumentMetadataRequest metadata, Integer userId) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidDocumentFileException("The file is empty");
        }
        String filename = baseName(file.getOriginalFilename());
        if (filename.isBlank()) {
            throw new InvalidDocumentFileException("The file has no name");
        }
        if (filename.length() > MAX_FILENAME_LENGTH) {
            throw new InvalidDocumentFileException("The file name exceeds " + MAX_FILENAME_LENGTH + " characters");
        }

        byte[] bytes = file.getBytes();

        // Detected from the bytes, with the name as a hint: the Content-Type sent by the browser is not trusted.
        String mimeType = TIKA.detect(bytes, filename);
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new UnsupportedDocumentTypeException(filename, mimeType);
        }

        String checksum = sha256Hex(bytes);
        if (documentRepository.existsByChecksumAndStatusNot(checksum, DocumentEntity.DocumentStatusEnum.REJECTED)) {
            throw new DuplicateDocumentException(checksum);
        }

        String content = extractText(bytes, filename);
        // A scanned PDF has no text layer: storing it would leave a document that can never be approved.
        if (content.isBlank()) {
            throw new UnreadableDocumentException(filename);
        }

        // Store in the DB with PENDING status
        DocumentEntity document = new DocumentEntity();
        document.setFilename(filename);
        document.setFileSize(file.getSize());
        // The uploader is the authenticated user, so it exists: a reference avoids loading it.
        document.setUploadedBy(userRepository.getReferenceById(userId));
        document.setUploadedAt(Instant.now());
        document.setContent(content);
        document.setStatus(DocumentEntity.DocumentStatusEnum.PENDING);
        document.setChecksum(checksum);
        document.setMimeType(mimeType);
        document.setCharCount(content.length());

        // New metadata fields
        document.setUniverse(metadata.universe().strip());
        document.setWork(blankToNull(metadata.work()));
        document.setLang(metadata.lang());
        document.setTitle(blankToNull(metadata.title()));
        document.setCanonicity(metadata.canonicity());
        document.setDocumentType(metadata.documentType());
        document.setAuthor(blankToNull(metadata.author()));
        document.setOriginUrl(blankToNull(metadata.originUrl()));

        DocumentEntity saved;
        try {
            saved = documentRepository.save(document);
        } catch (DataIntegrityViolationException e) {
            // Two uploads of the same file at once both pass the check above; the partial unique index stops the second.
            if (documentRepository.existsByChecksumAndStatusNot(checksum, DocumentEntity.DocumentStatusEnum.REJECTED)) {
                throw new DuplicateDocumentException(checksum);
            }
            throw e;
        }
        // Built from the saved entity so the response carries the normalised values, not the raw form input.
        return new SubmitResponse(saved.getId(), saved.getFilename(), saved.getStatus().name(), saved.getUniverse(), saved.getLang());
    }

    @Override
    public List<DocumentSummaryResponse> list(Integer userId, boolean includeAll) {
        return includeAll
                ? documentRepository.findAllSummaries()
                : documentRepository.findSummariesByUploader(userId);
    }

    @Override
    public DocumentContentResponse getContent(UUID id) {
        return documentRepository.findContentById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Override
    public void approve(UUID id, Integer reviewerId) {
        DocumentEntity document = documentRepository
                .findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        if (document.getStatus() != DocumentEntity.DocumentStatusEnum.PENDING) {
            throw new InvalidDocumentStateException(id, document.getStatus());
        }

        List<Document> chunks = splitter.apply(List.of(new Document(document.getContent(), chunkMetadata(document))));
        if (chunks.isEmpty()) {
            throw new InvalidDocumentStateException(id, document.getStatus());
        }

        // Claims the review before the slow indexing: the save bumps the version, so a concurrent approve or reject
        // that loaded the same version fails here, before anything reaches Qdrant. If indexing then fails, the
        // document stays PENDING with this reviewer recorded until the next attempt overwrites it.
        document.setReviewedBy(userRepository.getReferenceById(reviewerId));
        document.setReviewedAt(Instant.now());
        document = documentRepository.saveAndFlush(document);

        // Clears vectors left by an attempt that crashed between indexing and saving, so a retry does not index them twice.
        vectorStore.delete(new FilterExpressionBuilder().eq("documentId", id.toString()).build());

        try {
            vectorStore.add(chunks);

            document.setStatus(DocumentEntity.DocumentStatusEnum.APPROVED);
            document.setChunkCount(chunks.size());
            // Fails with a version conflict if another admin rejected, approved or deleted it while it was indexing.
            documentRepository.save(document);
        } catch (RuntimeException e) {
            discardChunks(chunks);
            throw e;
        }
    }

    @Override
    public void reject(UUID id, String reason, Integer reviewerId) {
        DocumentEntity document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        // Only a pending document can be rejected; an approved one is removed with delete.
        if (document.getStatus() != DocumentEntity.DocumentStatusEnum.PENDING) {
            throw new InvalidDocumentStateException(id, document.getStatus());
        }

        document.setStatus(DocumentEntity.DocumentStatusEnum.REJECTED);
        document.setRejectionReason(blankToNull(reason));
        document.setReviewedBy(userRepository.getReferenceById(reviewerId));
        document.setReviewedAt(Instant.now());
        documentRepository.save(document);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        DocumentEntity document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        // The row first, flushed so the version check runs now: a document changed in the meantime fails here,
        // before its vectors are touched. If Qdrant then fails, the transaction rolls the row back and the delete
        // can be retried from the UI.
        documentRepository.delete(document);
        documentRepository.flush();

        // Done for every status, so it also cleans up vectors left behind by an approve that failed halfway.
        var filter = new FilterExpressionBuilder()
                .eq("documentId", id.toString())
                .build();
        vectorStore.delete(filter);
    }

    // Removes exactly the chunks this attempt added. Deleting by documentId instead could wipe the vectors of a
    // concurrent approve that won. Best effort: the original failure is the one worth reporting.
    private void discardChunks(List<Document> chunks) {
        try {
            vectorStore.delete(chunks.stream().map(Document::getId).toList());
        } catch (RuntimeException e) {
            LOGGER.warn("Could not discard {} chunks after a failed approve", chunks.size(), e);
        }
    }

    private static Map<String, Object> chunkMetadata(DocumentEntity document) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", document.getId().toString());
        metadata.put("filename", document.getFilename());
        metadata.put("universe", document.getUniverse());
        metadata.put("lang", document.getLang());
        metadata.put("canonicity", document.getCanonicity().name());
        metadata.put("uploadedAt", document.getUploadedAt().toString());
        if (document.getWork() != null) metadata.put("work", document.getWork());
        if (document.getTitle() != null) metadata.put("title", document.getTitle());
        if (document.getDocumentType() != null) metadata.put("documentType", document.getDocumentType().name());
        return metadata;
    }

    // Some browsers send the full client path, with either separator: keep only the name.
    private static String baseName(String originalFilename) {
        if (originalFilename == null) return "";
        int lastSeparator = Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        return originalFilename.substring(lastSeparator + 1).strip();
    }

    private static String extractText(byte[] bytes, String filename) {
        try {
            var resource = new InputStreamResource(new ByteArrayInputStream(bytes), filename);
            return new TikaDocumentReader(resource).get().stream()
                    .map(Document::getText)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"))
                    .strip();
        } catch (RuntimeException e) {
            // TikaDocumentReader wraps parser failures (a corrupt or password-protected PDF) in a RuntimeException.
            throw new UnreadableDocumentException(filename, e);
        }
    }

    // An optional field left empty in the form arrives as "" rather than being omitted.
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to support SHA-256, so this only happens on a broken JVM.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

}
