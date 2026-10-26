package com.einar115.thearchivist.service;

import com.einar115.thearchivist.dto.request.DocumentMetadataRequest;
import com.einar115.thearchivist.dto.response.DocumentContentResponse;
import com.einar115.thearchivist.dto.response.DocumentSummaryResponse;
import com.einar115.thearchivist.dto.response.SubmitResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    SubmitResponse submit(MultipartFile file, DocumentMetadataRequest metadata, Integer userId) throws IOException;
    List<DocumentSummaryResponse> list(Integer userId, boolean includeAll);
    DocumentContentResponse getContent(UUID id);
    void approve(UUID id, Integer reviewerId);
    void reject(UUID id, String reason, Integer reviewerId);
    void delete(UUID id);
}
