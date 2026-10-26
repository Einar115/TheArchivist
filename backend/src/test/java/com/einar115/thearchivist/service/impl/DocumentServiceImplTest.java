package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.config.AiConfig;
import com.einar115.thearchivist.dto.request.DocumentMetadataRequest;
import com.einar115.thearchivist.dto.response.DocumentContentResponse;
import com.einar115.thearchivist.dto.response.SubmitResponse;
import com.einar115.thearchivist.entity.DocumentEntity;
import com.einar115.thearchivist.entity.DocumentEntity.CanonicityEnum;
import com.einar115.thearchivist.entity.DocumentEntity.DocumentStatusEnum;
import com.einar115.thearchivist.entity.DocumentEntity.DocumentTypeEnum;
import com.einar115.thearchivist.entity.UserEntity;
import com.einar115.thearchivist.exception.DocumentNotFoundException;
import com.einar115.thearchivist.exception.DuplicateDocumentException;
import com.einar115.thearchivist.exception.InvalidDocumentFileException;
import com.einar115.thearchivist.exception.InvalidDocumentStateException;
import com.einar115.thearchivist.exception.UnreadableDocumentException;
import com.einar115.thearchivist.exception.UnsupportedDocumentTypeException;
import com.einar115.thearchivist.repository.DocumentRepository;
import com.einar115.thearchivist.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("7c1e4a52-0d3b-4f6e-9a21-000000000001");
    private static final Integer UPLOADER_ID = 7;
    private static final Integer REVIEWER_ID = 1;
    private static final String LORE_TEXT = """
            Before the Calamity buried Hyrule Castle, the royal family guarded three fragments of the Triforce.
            The Sheikah chronicles describe them as keepers of a forgotten technology.
            """;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Captor
    private ArgumentCaptor<DocumentEntity> documentCaptor;

    @Captor
    private ArgumentCaptor<List<Document>> chunksCaptor;

    @Captor
    private ArgumentCaptor<Filter.Expression> filterCaptor;

    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        // The real splitter from AiConfig: it is pure computation, so the chunks match production.
        documentService = new DocumentServiceImpl(vectorStore, new AiConfig().tokenTextSplitter(), userRepository, documentRepository);
    }

    @Nested
    class Submit {

        private final MockMultipartFile file =
                new MockMultipartFile("file", "hyrule-chronicles.txt", "text/plain", LORE_TEXT.getBytes(StandardCharsets.UTF_8));

        @Test
        void storesPendingDocumentWithMetadataAndComputedFields() throws Exception {
            UserEntity uploader = new UserEntity();
            when(userRepository.getReferenceById(UPLOADER_ID)).thenReturn(uploader);
            when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> {
                DocumentEntity entity = invocation.getArgument(0);
                entity.setId(DOCUMENT_ID);
                return entity;
            });

            SubmitResponse response = documentService.submit(file, fullMetadata(), UPLOADER_ID);

            assertThat(response).isEqualTo(new SubmitResponse(DOCUMENT_ID, "hyrule-chronicles.txt", "PENDING", "The Legend of Zelda", "en"));
            verify(documentRepository).save(documentCaptor.capture());
            DocumentEntity saved = documentCaptor.getValue();

            assertThat(saved.getStatus()).isEqualTo(DocumentStatusEnum.PENDING);
            assertThat(saved.getFilename()).isEqualTo("hyrule-chronicles.txt");
            assertThat(saved.getFileSize()).isEqualTo(file.getSize());
            assertThat(saved.getUploadedBy()).isSameAs(uploader);
            assertThat(saved.getUploadedAt()).isNotNull();
            assertThat(saved.getContent()).isEqualTo(LORE_TEXT.strip());
            assertThat(saved.getCharCount()).isEqualTo(LORE_TEXT.strip().length());
            assertThat(saved.getChecksum()).isEqualTo(sha256(file.getBytes()));
            assertThat(saved.getMimeType()).isEqualTo("text/plain");

            assertThat(saved.getUniverse()).isEqualTo("The Legend of Zelda");
            assertThat(saved.getWork()).isEqualTo("Breath of the Wild");
            assertThat(saved.getLang()).isEqualTo("en");
            assertThat(saved.getTitle()).isEqualTo("Hyrule Chronicles");
            assertThat(saved.getCanonicity()).isEqualTo(CanonicityEnum.CANON);
            assertThat(saved.getDocumentType()).isEqualTo(DocumentTypeEnum.OFFICIAL_GUIDE);
            assertThat(saved.getAuthor()).isEqualTo("Nintendo");
            assertThat(saved.getOriginUrl()).isEqualTo("https://example.com/hyrule");
        }

        @Test
        void storesBlankOptionalFieldsAsNullAndStripsUniverse() throws Exception {
            when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> {
                DocumentEntity entity = invocation.getArgument(0);
                entity.setId(DOCUMENT_ID);
                return entity;
            });
            // An HTML form sends optional fields left empty as "".
            var metadata = new DocumentMetadataRequest("  The Legend of Zelda  ", "", "en", "   ", CanonicityEnum.SPECULATION, null, "", "");

            SubmitResponse response = documentService.submit(file, metadata, UPLOADER_ID);

            assertThat(response.universe()).isEqualTo("The Legend of Zelda");
            verify(documentRepository).save(documentCaptor.capture());
            DocumentEntity saved = documentCaptor.getValue();
            assertThat(saved.getUniverse()).isEqualTo("The Legend of Zelda");
            assertThat(saved.getWork()).isNull();
            assertThat(saved.getTitle()).isNull();
            assertThat(saved.getAuthor()).isNull();
            assertThat(saved.getOriginUrl()).isNull();
            assertThat(saved.getDocumentType()).isNull();
        }

        @Test
        void rejectsDuplicateOfActiveDocument() throws Exception {
            String checksum = sha256(file.getBytes());
            when(documentRepository.existsByChecksumAndStatusNot(checksum, DocumentStatusEnum.REJECTED)).thenReturn(true);

            assertThatThrownBy(() -> documentService.submit(file, fullMetadata(), UPLOADER_ID))
                    .isInstanceOf(DuplicateDocumentException.class);

            verify(documentRepository, never()).save(any());
        }

        @Test
        void acceptsMarkdown() throws Exception {
            returnSavedEntity();
            var markdown = new MockMultipartFile("file", "notas.md", "text/markdown", "# Hyrule\n\nLore notes.".getBytes(StandardCharsets.UTF_8));

            documentService.submit(markdown, fullMetadata(), UPLOADER_ID);

            verify(documentRepository).save(documentCaptor.capture());
            assertThat(documentCaptor.getValue().getMimeType()).isEqualTo("text/markdown");
        }

        @Test
        void keepsOnlyTheNameWhenTheBrowserSendsAPath() throws Exception {
            returnSavedEntity();
            var withPath = new MockMultipartFile("file", "C:\\fakepath\\hyrule-chronicles.txt", "text/plain", LORE_TEXT.getBytes(StandardCharsets.UTF_8));

            SubmitResponse response = documentService.submit(withPath, fullMetadata(), UPLOADER_ID);

            assertThat(response.filename()).isEqualTo("hyrule-chronicles.txt");
        }

        @Test
        void rejectsEmptyFile() {
            var empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

            assertThatThrownBy(() -> documentService.submit(empty, fullMetadata(), UPLOADER_ID))
                    .isInstanceOf(InvalidDocumentFileException.class);

            verifyNoInteractions(documentRepository);
        }

        @Test
        void rejectsFileWithoutName() {
            var nameless = new MockMultipartFile("file", "", "text/plain", LORE_TEXT.getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> documentService.submit(nameless, fullMetadata(), UPLOADER_ID))
                    .isInstanceOf(InvalidDocumentFileException.class);

            verifyNoInteractions(documentRepository);
        }

        @Test
        void rejectsNameLongerThanTheColumn() {
            var longName = new MockMultipartFile("file", "a".repeat(252) + ".txt", "text/plain", LORE_TEXT.getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> documentService.submit(longName, fullMetadata(), UPLOADER_ID))
                    .isInstanceOf(InvalidDocumentFileException.class);

            verifyNoInteractions(documentRepository);
        }

        @Test
        void rejectsUnsupportedTypeDetectedFromTheBytes() {
            // A PNG renamed to .txt: the type comes from the content, not from the name or the browser's Content-Type.
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0x0d, 'I', 'H', 'D', 'R'};
            var disguised = new MockMultipartFile("file", "notes.txt", "text/plain", png);

            assertThatThrownBy(() -> documentService.submit(disguised, fullMetadata(), UPLOADER_ID))
                    .isInstanceOf(UnsupportedDocumentTypeException.class)
                    .hasMessageContaining("image/png");

            verifyNoInteractions(documentRepository);
        }

        @Test
        void rejectsDocumentWithoutText() {
            var blank = new MockMultipartFile("file", "blank.txt", "text/plain", "   \n\n   ".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> documentService.submit(blank, fullMetadata(), UPLOADER_ID))
                    .isInstanceOf(UnreadableDocumentException.class);

            verify(documentRepository, never()).save(any());
        }

        @Test
        void rejectsDocumentThatCannotBeParsed() {
            var corrupt = new MockMultipartFile("file", "broken.pdf", "application/pdf", "%PDF-1.7\nnot really a pdf".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> documentService.submit(corrupt, fullMetadata(), UPLOADER_ID))
                    .isInstanceOf(UnreadableDocumentException.class);

            verify(documentRepository, never()).save(any());
        }

        @Test
        void reportsConcurrentDuplicateCaughtByTheUniqueIndex() throws Exception {
            String checksum = sha256(file.getBytes());
            // The first check passes; by the time the row is inserted another upload of the same file has won.
            when(documentRepository.existsByChecksumAndStatusNot(checksum, DocumentStatusEnum.REJECTED)).thenReturn(false, true);
            when(documentRepository.save(any(DocumentEntity.class))).thenThrow(new DataIntegrityViolationException("uq_documents_checksum_active"));

            assertThatThrownBy(() -> documentService.submit(file, fullMetadata(), UPLOADER_ID))
                    .isInstanceOf(DuplicateDocumentException.class);
        }

        @Test
        void rethrowsIntegrityViolationsUnrelatedToDuplicates() throws Exception {
            when(documentRepository.save(any(DocumentEntity.class))).thenThrow(new DataIntegrityViolationException("other constraint"));

            assertThatThrownBy(() -> documentService.submit(file, fullMetadata(), UPLOADER_ID))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        private void returnSavedEntity() {
            when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> {
                DocumentEntity entity = invocation.getArgument(0);
                entity.setId(DOCUMENT_ID);
                return entity;
            });
        }
    }

    @Nested
    class Approve {

        @Test
        void indexesChunksAndMarksDocumentApproved() {
            DocumentEntity document = pendingDocument();
            UserEntity reviewer = new UserEntity();
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
            when(userRepository.getReferenceById(REVIEWER_ID)).thenReturn(reviewer);

            documentService.approve(DOCUMENT_ID, REVIEWER_ID);

            // Previous vectors are cleared before adding, so a retried approve does not index twice.
            InOrder order = inOrder(vectorStore, documentRepository);
            order.verify(vectorStore).delete(filterCaptor.capture());
            order.verify(vectorStore).add(chunksCaptor.capture());
            order.verify(documentRepository).save(document);

            assertThat(filterCaptor.getValue()).isEqualTo(documentIdFilter());
            List<Document> chunks = chunksCaptor.getValue();
            assertThat(chunks).isNotEmpty();
            assertThat(chunks.getFirst().getMetadata())
                    .containsEntry("documentId", DOCUMENT_ID.toString())
                    .containsEntry("filename", "hyrule-chronicles.txt")
                    .containsEntry("universe", "The Legend of Zelda")
                    .containsEntry("work", "Breath of the Wild")
                    .containsEntry("lang", "en")
                    .containsEntry("title", "Hyrule Chronicles")
                    .containsEntry("canonicity", "CANON")
                    .containsEntry("documentType", "OFFICIAL_GUIDE")
                    .containsEntry("uploadedAt", document.getUploadedAt().toString());

            assertThat(document.getStatus()).isEqualTo(DocumentStatusEnum.APPROVED);
            assertThat(document.getChunkCount()).isEqualTo(chunks.size());
            assertThat(document.getReviewedBy()).isSameAs(reviewer);
            assertThat(document.getReviewedAt()).isNotNull();
        }

        @Test
        void leavesUnsetOptionalFieldsOutOfChunkMetadata() {
            DocumentEntity document = pendingDocument();
            document.setWork(null);
            document.setTitle(null);
            document.setDocumentType(null);
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

            documentService.approve(DOCUMENT_ID, REVIEWER_ID);

            verify(vectorStore).add(chunksCaptor.capture());
            assertThat(chunksCaptor.getValue().getFirst().getMetadata())
                    .doesNotContainKeys("work", "title", "documentType")
                    .containsKeys("documentId", "filename", "universe", "lang", "canonicity", "uploadedAt");
        }

        @Test
        void failsWhenDocumentDoesNotExist() {
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.approve(DOCUMENT_ID, REVIEWER_ID))
                    .isInstanceOf(DocumentNotFoundException.class);

            verifyNoInteractions(vectorStore);
            verify(documentRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = DocumentStatusEnum.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
        void failsWhenDocumentIsNotPending(DocumentStatusEnum status) {
            DocumentEntity document = pendingDocument();
            document.setStatus(status);
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

            assertThatThrownBy(() -> documentService.approve(DOCUMENT_ID, REVIEWER_ID))
                    .isInstanceOf(InvalidDocumentStateException.class);

            verifyNoInteractions(vectorStore);
            verify(documentRepository, never()).save(any());
            assertThat(document.getStatus()).isEqualTo(status);
        }

        @Test
        void failsWhenDocumentHasNoText() {
            DocumentEntity document = pendingDocument();
            document.setContent("");
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

            assertThatThrownBy(() -> documentService.approve(DOCUMENT_ID, REVIEWER_ID))
                    .isInstanceOf(InvalidDocumentStateException.class);

            verifyNoInteractions(vectorStore);
            verify(documentRepository, never()).save(any());
            assertThat(document.getStatus()).isEqualTo(DocumentStatusEnum.PENDING);
        }
    }

    @Nested
    class Reject {

        @Test
        void marksDocumentRejectedWithReasonAndReviewer() {
            DocumentEntity document = pendingDocument();
            UserEntity reviewer = new UserEntity();
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
            when(userRepository.getReferenceById(REVIEWER_ID)).thenReturn(reviewer);

            documentService.reject(DOCUMENT_ID, "  Duplicates an existing guide  ", REVIEWER_ID);

            verify(documentRepository).save(document);
            assertThat(document.getStatus()).isEqualTo(DocumentStatusEnum.REJECTED);
            assertThat(document.getRejectionReason()).isEqualTo("Duplicates an existing guide");
            assertThat(document.getReviewedBy()).isSameAs(reviewer);
            assertThat(document.getReviewedAt()).isNotNull();
            // A pending document was never indexed, so there is nothing to remove from Qdrant.
            verifyNoInteractions(vectorStore);
        }

        @Test
        void storesBlankReasonAsNull() {
            DocumentEntity document = pendingDocument();
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

            documentService.reject(DOCUMENT_ID, "   ", REVIEWER_ID);

            assertThat(document.getStatus()).isEqualTo(DocumentStatusEnum.REJECTED);
            assertThat(document.getRejectionReason()).isNull();
        }

        @Test
        void failsWhenDocumentDoesNotExist() {
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.reject(DOCUMENT_ID, null, REVIEWER_ID))
                    .isInstanceOf(DocumentNotFoundException.class);

            verify(documentRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = DocumentStatusEnum.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
        void failsWhenDocumentIsNotPending(DocumentStatusEnum status) {
            DocumentEntity document = pendingDocument();
            document.setStatus(status);
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

            assertThatThrownBy(() -> documentService.reject(DOCUMENT_ID, "Too late", REVIEWER_ID))
                    .isInstanceOf(InvalidDocumentStateException.class);

            verify(documentRepository, never()).save(any());
            assertThat(document.getStatus()).isEqualTo(status);
            assertThat(document.getRejectionReason()).isNull();
        }
    }

    @Nested
    class Delete {

        @ParameterizedTest
        @EnumSource(DocumentStatusEnum.class)
        void removesVectorsBeforeDeletingRow(DocumentStatusEnum status) {
            DocumentEntity document = pendingDocument();
            document.setStatus(status);
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

            documentService.delete(DOCUMENT_ID);

            InOrder order = inOrder(vectorStore, documentRepository);
            order.verify(vectorStore).delete(filterCaptor.capture());
            order.verify(documentRepository).delete(document);
            assertThat(filterCaptor.getValue()).isEqualTo(documentIdFilter());
        }

        @Test
        void failsWhenDocumentDoesNotExist() {
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.delete(DOCUMENT_ID))
                    .isInstanceOf(DocumentNotFoundException.class);

            verifyNoInteractions(vectorStore);
            verify(documentRepository, never()).delete(any());
        }

        @Test
        void keepsRowWhenVectorStoreFails() {
            DocumentEntity document = pendingDocument();
            document.setStatus(DocumentStatusEnum.APPROVED);
            when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
            doThrow(new IllegalStateException("Qdrant unavailable")).when(vectorStore).delete(any(Filter.Expression.class));

            assertThatThrownBy(() -> documentService.delete(DOCUMENT_ID))
                    .isInstanceOf(IllegalStateException.class);

            // The row survives, so the delete can be retried from the UI.
            verify(documentRepository, never()).delete(any());
        }
    }

    private static DocumentMetadataRequest fullMetadata() {
        return new DocumentMetadataRequest(
                "The Legend of Zelda",
                "Breath of the Wild",
                "en",
                "Hyrule Chronicles",
                CanonicityEnum.CANON,
                DocumentTypeEnum.OFFICIAL_GUIDE,
                "Nintendo",
                "https://example.com/hyrule");
    }

    private static DocumentEntity pendingDocument() {
        DocumentEntity document = new DocumentEntity();
        document.setId(DOCUMENT_ID);
        document.setFilename("hyrule-chronicles.txt");
        document.setUploadedAt(Instant.parse("2026-09-18T10:42:00Z"));
        document.setContent(LORE_TEXT);
        document.setStatus(DocumentStatusEnum.PENDING);
        document.setUniverse("The Legend of Zelda");
        document.setWork("Breath of the Wild");
        document.setLang("en");
        document.setTitle("Hyrule Chronicles");
        document.setCanonicity(CanonicityEnum.CANON);
        document.setDocumentType(DocumentTypeEnum.OFFICIAL_GUIDE);
        return document;
    }

    @Nested
    class GetContent {

        @Test
        void returnsStoredText() {
            var expected = new DocumentContentResponse(DOCUMENT_ID, LORE_TEXT);
            when(documentRepository.findContentById(DOCUMENT_ID)).thenReturn(Optional.of(expected));

            assertThat(documentService.getContent(DOCUMENT_ID)).isEqualTo(expected);
        }

        @Test
        void failsWhenDocumentDoesNotExist() {
            when(documentRepository.findContentById(DOCUMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.getContent(DOCUMENT_ID))
                    .isInstanceOf(DocumentNotFoundException.class);
        }
    }

    private static Filter.Expression documentIdFilter() {
        return new FilterExpressionBuilder().eq("documentId", DOCUMENT_ID.toString()).build();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
