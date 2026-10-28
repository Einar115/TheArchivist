package com.einar115.thearchivist.repository;

import com.einar115.thearchivist.dto.response.DocumentContentResponse;
import com.einar115.thearchivist.dto.response.DocumentSummaryResponse;
import com.einar115.thearchivist.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    final static String SUMMARY_SELECT = """
        select new com.einar115.thearchivist.dto.response.DocumentSummaryResponse(
            d.id, d.filename, d.fileSize, d.uploadedAt, u.username, d.status, d.chunkCount,
            d.rejectionReason, d.universe, d.work, d.lang, d.title, d.canonicity, d.documentType)
        from DocumentEntity d left join d.uploadedBy u
        """;
    boolean existsByChecksumAndStatusNot(String checksum, DocumentEntity.DocumentStatusEnum status);

    @Query(SUMMARY_SELECT + " order by d.uploadedAt desc")
    List<DocumentSummaryResponse> findAllSummaries();

    @Query(SUMMARY_SELECT + " where u.id = :userId order by d.uploadedAt desc")
    List<DocumentSummaryResponse> findSummariesByUploader(@Param("userId") Integer userId);

    @Query("select new com.einar115.thearchivist.dto.response.DocumentContentResponse(d.id, d.content) "
            + "from DocumentEntity d where d.id = :id")
    Optional<DocumentContentResponse> findContentById(@Param("id") UUID id);
}
