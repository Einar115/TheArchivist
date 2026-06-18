package com.einar115.thearchivist.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface IngestionService {

    String ingest(MultipartFile file, String game) throws IOException;
    String uploadPending(MultipartFile file, Integer userId) throws IOException;
    void approveDocument(UUID documentId) throws IOException;
    void delete(String documentId);

}
