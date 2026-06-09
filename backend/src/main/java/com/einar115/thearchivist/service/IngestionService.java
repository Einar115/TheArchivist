package com.einar115.thearchivist.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface IngestionService {

    String ingest(MultipartFile file, String game) throws IOException;
    void delete(String documentId);

}
