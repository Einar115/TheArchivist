package com.einar115.thearchivist.controller;

import com.einar115.thearchivist.dto.IngestResponse;
import com.einar115.thearchivist.service.IngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final IngestionService ingestionService;

    public DocumentController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestParam("file") MultipartFile file, @RequestParam("game") String game) throws IOException {
        String documentId = ingestionService.ingest(file, game);
        return ResponseEntity.ok(new IngestResponse(documentId, file.getOriginalFilename(), game));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        ingestionService.delete(documentId);
        return ResponseEntity.noContent().build();
    }
}
