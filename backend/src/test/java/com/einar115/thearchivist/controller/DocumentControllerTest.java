package com.einar115.thearchivist.controller;

import com.einar115.thearchivist.config.SecurityConfig;
import com.einar115.thearchivist.dto.request.DocumentMetadataRequest;
import com.einar115.thearchivist.dto.response.DocumentContentResponse;
import com.einar115.thearchivist.dto.response.DocumentSummaryResponse;
import com.einar115.thearchivist.dto.response.SubmitResponse;
import com.einar115.thearchivist.entity.DocumentEntity;
import com.einar115.thearchivist.entity.DocumentEntity.CanonicityEnum;
import com.einar115.thearchivist.entity.DocumentEntity.DocumentStatusEnum;
import com.einar115.thearchivist.entity.DocumentEntity.DocumentTypeEnum;
import com.einar115.thearchivist.entity.RoleEntity;
import com.einar115.thearchivist.entity.UserEntity;
import com.einar115.thearchivist.exception.DocumentNotFoundException;
import com.einar115.thearchivist.exception.DuplicateDocumentException;
import com.einar115.thearchivist.exception.InvalidDocumentFileException;
import com.einar115.thearchivist.exception.InvalidDocumentStateException;
import com.einar115.thearchivist.exception.UnreadableDocumentException;
import com.einar115.thearchivist.exception.UnsupportedDocumentTypeException;
import com.einar115.thearchivist.model.MainUser;
import com.einar115.thearchivist.service.DocumentService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real SecurityConfig is imported so these tests also pin down the role rules and the CSRF protection.
@WebMvcTest(controllers = DocumentController.class, properties = "app.cors.allowed-origins=http://localhost:4200")
@Import(SecurityConfig.class)
class DocumentControllerTest {

    private static final String BASE_URL = "/api/v1/documents";
    private static final UUID DOCUMENT_ID = UUID.fromString("7c1e4a52-0d3b-4f6e-9a21-000000000001");
    private static final MainUser UPLOADER = mainUser(7, "uploader", RoleEntity.RoleEnum.UPLOADER);
    private static final MainUser ADMIN = mainUser(1, "admin", RoleEntity.RoleEnum.ADMIN_DOCUMENTS);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Nested
    class ListDocuments {

        @Test
        void uploaderOnlyGetsOwnDocuments() throws Exception {
            when(documentService.list(7, false)).thenReturn(List.of(summary()));

            mockMvc.perform(get(BASE_URL).with(user(UPLOADER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(DOCUMENT_ID.toString()))
                    .andExpect(jsonPath("$[0].filename").value("hyrule-chronicles.txt"))
                    .andExpect(jsonPath("$[0].uploadedBy").value("uploader"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"))
                    // The frontend parses it with new Date(iso), so it must be an ISO-8601 string.
                    .andExpect(jsonPath("$[0].uploadedAt").value("2026-09-18T10:42:00Z"));

            verify(documentService).list(7, false);
        }

        @Test
        void adminGetsEveryDocument() throws Exception {
            when(documentService.list(1, true)).thenReturn(List.of());

            mockMvc.perform(get(BASE_URL).with(user(ADMIN)))
                    .andExpect(status().isOk());

            verify(documentService).list(1, true);
        }

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(documentService);
        }
    }

    @Nested
    class Submit {

        private final MockMultipartFile file =
                new MockMultipartFile("file", "hyrule-chronicles.txt", "text/plain", "Lore text".getBytes());

        @Test
        void createsPendingDocumentFromFileAndMetadata() throws Exception {
            when(documentService.submit(any(), any(), anyInt()))
                    .thenReturn(new SubmitResponse(DOCUMENT_ID, "hyrule-chronicles.txt", "PENDING", "The Legend of Zelda", "en"));

            mockMvc.perform(validSubmit().with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(DOCUMENT_ID.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.universe").value("The Legend of Zelda"));

            var expectedMetadata = new DocumentMetadataRequest(
                    "The Legend of Zelda", "Breath of the Wild", "en", "Hyrule Chronicles",
                    CanonicityEnum.CANON, DocumentTypeEnum.OFFICIAL_GUIDE, "Nintendo", "https://example.com/hyrule");
            verify(documentService).submit(any(), eq(expectedMetadata), eq(7));
        }

        @Test
        void adminCanSubmitThroughRoleHierarchy() throws Exception {
            when(documentService.submit(any(), any(), anyInt()))
                    .thenReturn(new SubmitResponse(DOCUMENT_ID, "hyrule-chronicles.txt", "PENDING", "The Legend of Zelda", "en"));

            mockMvc.perform(validSubmit().with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isCreated());
        }

        @Test
        void rejectsInvalidMetadata() throws Exception {
            mockMvc.perform(multipart(BASE_URL + "/submit").file(file)
                            .param("universe", " ")
                            .param("lang", "ES")
                            .param("originUrl", "not a url")
                            .with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors", hasKey("universe")))
                    .andExpect(jsonPath("$.validationErrors", hasKey("lang")))
                    .andExpect(jsonPath("$.validationErrors", hasKey("canonicity")))
                    .andExpect(jsonPath("$.validationErrors", hasKey("originUrl")));

            verifyNoInteractions(documentService);
        }

        @Test
        void rejectsUnknownCanonicity() throws Exception {
            mockMvc.perform(multipart(BASE_URL + "/submit").file(file)
                            .param("universe", "The Legend of Zelda")
                            .param("lang", "en")
                            .param("canonicity", "MAYBE")
                            .with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors", hasKey("canonicity")));

            verifyNoInteractions(documentService);
        }

        @Test
        void returnsConflictForDuplicate() throws Exception {
            when(documentService.submit(any(), any(), anyInt())).thenThrow(new DuplicateDocumentException("abc123"));

            mockMvc.perform(validSubmit().with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isConflict());
        }

        @Test
        void returnsContentTooLargeWhenUploadExceedsLimit() throws Exception {
            // MockMvc does not parse multipart against the servlet limits, so the exception is raised from the
            // service instead; this checks the handler mapping, not where Spring throws it in production.
            when(documentService.submit(any(), any(), anyInt())).thenThrow(new MaxUploadSizeExceededException(10L * 1024 * 1024));

            mockMvc.perform(validSubmit().with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isContentTooLarge())
                    .andExpect(jsonPath("$.message").value("File exceeds the maximum upload size"));
        }

        @Test
        void returnsBadRequestForInvalidFile() throws Exception {
            when(documentService.submit(any(), any(), anyInt())).thenThrow(new InvalidDocumentFileException("The file is empty"));

            mockMvc.perform(validSubmit().with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("The file is empty"));
        }

        @Test
        void returnsUnsupportedMediaTypeForDisallowedFormat() throws Exception {
            when(documentService.submit(any(), any(), anyInt()))
                    .thenThrow(new UnsupportedDocumentTypeException("cover.png", "image/png"));

            mockMvc.perform(validSubmit().with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        void returnsUnprocessableContentWhenNoTextCanBeExtracted() throws Exception {
            when(documentService.submit(any(), any(), anyInt())).thenThrow(new UnreadableDocumentException("scan.pdf"));

            mockMvc.perform(validSubmit().with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(validSubmit().with(user(UPLOADER)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(documentService);
        }

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(validSubmit().with(csrf()))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(documentService);
        }

        private MockMultipartHttpServletRequestBuilder validSubmit() {
            return multipart(BASE_URL + "/submit").file(file)
                    .param("universe", "The Legend of Zelda")
                    .param("work", "Breath of the Wild")
                    .param("lang", "en")
                    .param("title", "Hyrule Chronicles")
                    .param("canonicity", "CANON")
                    .param("documentType", "OFFICIAL_GUIDE")
                    .param("author", "Nintendo")
                    .param("originUrl", "https://example.com/hyrule");
        }
    }

    @Nested
    class Approve {

        @Test
        void adminApprovesDocument() throws Exception {
            mockMvc.perform(post(BASE_URL + "/{id}/approve", DOCUMENT_ID).with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isNoContent());

            verify(documentService).approve(DOCUMENT_ID, 1);
        }

        @Test
        void returnsConflictWhenAnotherAdminChangedItMeanwhile() throws Exception {
            doThrow(new ObjectOptimisticLockingFailureException(DocumentEntity.class, DOCUMENT_ID))
                    .when(documentService).approve(DOCUMENT_ID, 1);

            mockMvc.perform(post(BASE_URL + "/{id}/approve", DOCUMENT_ID).with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("The document was modified by another user"));
        }

        @Test
        void uploaderCannotApprove() throws Exception {
            mockMvc.perform(post(BASE_URL + "/{id}/approve", DOCUMENT_ID).with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(documentService);
        }

        @Test
        void returnsNotFoundForUnknownDocument() throws Exception {
            doThrow(new DocumentNotFoundException(DOCUMENT_ID)).when(documentService).approve(DOCUMENT_ID, 1);

            mockMvc.perform(post(BASE_URL + "/{id}/approve", DOCUMENT_ID).with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returnsConflictWhenNotPending() throws Exception {
            doThrow(new InvalidDocumentStateException(DOCUMENT_ID, DocumentStatusEnum.APPROVED))
                    .when(documentService).approve(DOCUMENT_ID, 1);

            mockMvc.perform(post(BASE_URL + "/{id}/approve", DOCUMENT_ID).with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class Reject {

        @Test
        void adminRejectsWithReason() throws Exception {
            mockMvc.perform(post(BASE_URL + "/{id}/reject", DOCUMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"Duplicates an existing guide\"}")
                            .with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isNoContent());

            verify(documentService).reject(DOCUMENT_ID, "Duplicates an existing guide", 1);
        }

        @Test
        void acceptsMissingReason() throws Exception {
            mockMvc.perform(post(BASE_URL + "/{id}/reject", DOCUMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": null}")
                            .with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isNoContent());

            verify(documentService).reject(DOCUMENT_ID, null, 1);
        }

        @Test
        void rejectsReasonLongerThan500Characters() throws Exception {
            mockMvc.perform(post(BASE_URL + "/{id}/reject", DOCUMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"" + "a".repeat(501) + "\"}")
                            .with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors", hasKey("reason")));

            verifyNoInteractions(documentService);
        }

        @Test
        void returnsBadRequestWithoutBody() throws Exception {
            mockMvc.perform(post(BASE_URL + "/{id}/reject", DOCUMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));

            verifyNoInteractions(documentService);
        }

        @Test
        void returnsBadRequestForMalformedJson() throws Exception {
            mockMvc.perform(post(BASE_URL + "/{id}/reject", DOCUMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": ")
                            .with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(documentService);
        }

        @Test
        void uploaderCannotReject() throws Exception {
            mockMvc.perform(post(BASE_URL + "/{id}/reject", DOCUMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": null}")
                            .with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(documentService);
        }

        @Test
        void returnsConflictWhenNotPending() throws Exception {
            doThrow(new InvalidDocumentStateException(DOCUMENT_ID, DocumentStatusEnum.REJECTED))
                    .when(documentService).reject(any(), any(), anyInt());

            mockMvc.perform(post(BASE_URL + "/{id}/reject", DOCUMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": null}")
                            .with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class Delete {

        @Test
        void adminDeletesDocument() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/{id}", DOCUMENT_ID).with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isNoContent());

            verify(documentService).delete(DOCUMENT_ID);
        }

        @Test
        void uploaderCannotDelete() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/{id}", DOCUMENT_ID).with(user(UPLOADER)).with(csrf()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(documentService);
        }

        @Test
        void returnsNotFoundForUnknownDocument() throws Exception {
            doThrow(new DocumentNotFoundException(DOCUMENT_ID)).when(documentService).delete(DOCUMENT_ID);

            mockMvc.perform(delete(BASE_URL + "/{id}", DOCUMENT_ID).with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returnsBadRequestForMalformedId() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/{id}", "not-a-uuid").with(user(ADMIN)).with(csrf()))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(documentService);
        }
    }

    private static MainUser mainUser(int id, String username, RoleEntity.RoleEnum roleName) {
        RoleEntity role = new RoleEntity();
        role.setName(roleName);
        UserEntity entity = new UserEntity(username, "{noop}secret", true, List.of(role));
        entity.setId(id);
        return MainUser.from(entity);
    }

    private static DocumentSummaryResponse summary() {
        return new DocumentSummaryResponse(
                DOCUMENT_ID, "hyrule-chronicles.txt", 2_400_000L, Instant.parse("2026-09-18T10:42:00Z"), "uploader",
                DocumentStatusEnum.PENDING, null, null, "The Legend of Zelda", "Breath of the Wild", "en",
                "Hyrule Chronicles", CanonicityEnum.CANON, DocumentTypeEnum.OFFICIAL_GUIDE);
    }

    @Nested
    class GetContent {

        @Test
        void adminGetsExtractedText() throws Exception {
            when(documentService.getContent(DOCUMENT_ID)).thenReturn(new DocumentContentResponse(DOCUMENT_ID, "Lore text"));

            mockMvc.perform(get(BASE_URL + "/{id}/content", DOCUMENT_ID).with(user(ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(DOCUMENT_ID.toString()))
                    .andExpect(jsonPath("$.content").value("Lore text"));
        }

        @Test
        void uploaderCannotReadContent() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{id}/content", DOCUMENT_ID).with(user(UPLOADER)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(documentService);
        }

        @Test
        void returnsNotFoundForUnknownDocument() throws Exception {
            when(documentService.getContent(DOCUMENT_ID)).thenThrow(new DocumentNotFoundException(DOCUMENT_ID));

            mockMvc.perform(get(BASE_URL + "/{id}/content", DOCUMENT_ID).with(user(ADMIN)))
                    .andExpect(status().isNotFound());
        }
    }
}
