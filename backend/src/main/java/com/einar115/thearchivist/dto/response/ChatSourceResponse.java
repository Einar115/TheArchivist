package com.einar115.thearchivist.dto.response;

/**
 * A document the answer was built from, sent as the "sources" event before the answer tokens.
 * {@code number} is the [N] the model uses to cite it.
 */
public record ChatSourceResponse(
        int number,
        String documentId,
        String title,
        String universe,
        String work,
        String canonicity
) {
}
