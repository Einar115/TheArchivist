package com.einar115.thearchivist.rag;

import com.einar115.thearchivist.dto.request.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatFiltersTest {
    private final FilterExpressionBuilder b = new FilterExpressionBuilder();

    @Test
    void hasNoFilterWhenNothingWasChosen() {
        assertThat(ChatFilters.from(new ChatRequest("¿?", null, false))).isNull();
    }

    @Test
    void treatsABlankUniverseAsAllUniverses() {
        assertThat(ChatFilters.from(new ChatRequest("¿?", "   ", false))).isNull();
    }

    @Test
    void filtersByUniverse() {
        assertThat(ChatFilters.from(new ChatRequest("¿?", " The Witcher ", false)))
                .isEqualTo(b.eq("universe", "The Witcher").build());   // también comprueba el strip()
    }

    @Test
    void filtersByCanonOnly() {
        assertThat(ChatFilters.from(new ChatRequest("¿?", null, true)))
                .isEqualTo(b.eq("canonicity", "CANON").build());
    }

    @Test
    void combinesBothFiltersWithAnd() {
        assertThat(ChatFilters.from(new ChatRequest("¿?", "The Witcher", true)))
                .isEqualTo(b.and(b.eq("universe", "The Witcher"), b.eq("canonicity", "CANON")).build());
    }
}
