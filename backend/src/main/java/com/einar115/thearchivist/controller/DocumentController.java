package com.einar115.thearchivist.controller;

import com.einar115.thearchivist.dto.request.DocumentMetadataRequest;
import com.einar115.thearchivist.dto.request.RejectRequest;
import com.einar115.thearchivist.dto.response.DocumentContentResponse;
import com.einar115.thearchivist.dto.response.DocumentSummaryResponse;
import com.einar115.thearchivist.dto.response.SubmitResponse;
import com.einar115.thearchivist.entity.RoleEntity;
import com.einar115.thearchivist.model.MainUser;
import com.einar115.thearchivist.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<List<DocumentSummaryResponse>> getDocuments(@AuthenticationPrincipal MainUser user) {
        // The hierarchy makes every admin an UPLOADER too, so only the admin role widens the list.
        boolean isAdmin = user.getRoles().contains(RoleEntity.RoleEnum.ADMIN_DOCUMENTS.name());
        return ResponseEntity.ok(documentService.list(user.getId(), isAdmin));
    }

    // Multipart: the metadata travels as form fields next to the file, so it is bound with @ModelAttribute.
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitResponse> submit(@RequestParam("file") MultipartFile file, @Valid @ModelAttribute DocumentMetadataRequest metadata, @AuthenticationPrincipal MainUser user) throws IOException {
        SubmitResponse response = documentService.submit(file, metadata, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<DocumentContentResponse> getDocumentContent(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getContent(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approveDocument(@PathVariable UUID id, @AuthenticationPrincipal MainUser user) {
        documentService.approve(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request, @AuthenticationPrincipal MainUser user) {
        documentService.reject(id, request.reason(), user.getId());
        return ResponseEntity.noContent().build();
    }

}
