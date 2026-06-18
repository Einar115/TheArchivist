package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.entity.DocumentHistoryEntity;
import com.einar115.thearchivist.repository.DocumentHistoryRepository;
import com.einar115.thearchivist.repository.UserRepository;
import com.einar115.thearchivist.service.IngestionService;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IngestionServiceImpl implements IngestionService {
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final UserRepository  userRepository;
    private final DocumentHistoryRepository documentHistoryRepository;

    public IngestionServiceImpl(VectorStore vectorStore, TokenTextSplitter splitter, UserRepository userRepository, DocumentHistoryRepository documentHistoryRepository) {
        this.vectorStore = vectorStore;
        this.splitter = splitter;
        this.userRepository = userRepository;
        this.documentHistoryRepository = documentHistoryRepository;
    }

    @Override
    public String ingest(MultipartFile file, String game) throws IOException {
        String documentId = UUID.randomUUID().toString();
        String source = file.getOriginalFilename();
        String uploadedAt = Instant.now().toString();

        var resource = new InputStreamResource(file.getInputStream(), source);
        List<Document> rawDocs = new TikaDocumentReader(resource).get();

        rawDocs.forEach(doc -> {
            doc.getMetadata().put("documentId", documentId);
            doc.getMetadata().put("source", source);
            doc.getMetadata().put("game", game);
            doc.getMetadata().put("uploadedAt", uploadedAt);
        });

        List<Document> chunks = splitter.apply(rawDocs);
        vectorStore.add(chunks);

        return documentId;
    }

    @Override
    public String uploadPending(MultipartFile file, Integer userId) throws IOException {
        UUID documentId = UUID.randomUUID();
        String filename = file.getOriginalFilename();

        // Procesar archivo con Tika
        var resource = new InputStreamResource(file.getInputStream(), filename);
        List<Document> rawDocs = new TikaDocumentReader(resource).get();

        // Extraer contenido combinado
        String content = rawDocs.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n" + b)
                .strip();

        // Guardar en BD con status PENDING
        DocumentHistoryEntity history = new DocumentHistoryEntity();
        history.setDocumentId(documentId);
        history.setFilename(filename);
        history.setFileSize(file.getSize());
        history.setUser(userRepository.findById(userId).orElse(null));
        history.setUploadedAt(Instant.now());
        history.setContent(content);
        history.setStatus(DocumentHistoryEntity.DocumentStatusEnum.PENDING);

        documentHistoryRepository.save(history);
        return documentId.toString();
    }

    @Override
    public void approveDocument(UUID documentId) throws IOException {
        DocumentHistoryEntity history = documentHistoryRepository
                .findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentId));

        // Ingesta a Qdrant desde el contenido guardado
        List<Document> rawDocs = List.of(
                new Document(history.getContent())
        );

        rawDocs.forEach(doc -> {
            doc.getMetadata().put("documentId", documentId);
            doc.getMetadata().put("source", history.getFilename());
            doc.getMetadata().put("uploadedAt", history.getUploadedAt().toString());
        });

        List<Document> chunks = splitter.apply(rawDocs);
        vectorStore.add(chunks);

        // Actualizar estado y contar chunks
        history.setStatus(DocumentHistoryEntity.DocumentStatusEnum.APPROVED);
        history.setChunkCount(chunks.size());
        documentHistoryRepository.save(history);
    }

    @Override
    public void delete(String documentId) {
        var filter = new FilterExpressionBuilder()
                .eq("documentId", documentId)
                .build();
        vectorStore.delete(filter);

        documentHistoryRepository.findByDocumentId(UUID.fromString(documentId))
                .ifPresent(history -> {
                    history.setStatus(DocumentHistoryEntity.DocumentStatusEnum.REJECTED);
                    documentHistoryRepository.save(history);
                });
    }

}
