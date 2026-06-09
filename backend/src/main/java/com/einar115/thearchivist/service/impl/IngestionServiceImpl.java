package com.einar115.thearchivist.service.impl;

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

    public IngestionServiceImpl(VectorStore vectorStore, TokenTextSplitter splitter) {
        this.vectorStore = vectorStore;
        this.splitter = splitter;
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
    public void delete(String documentId) {
        var filter = new FilterExpressionBuilder()
                .eq("documentId", documentId)
                .build();
        vectorStore.delete(filter);
    }
}
