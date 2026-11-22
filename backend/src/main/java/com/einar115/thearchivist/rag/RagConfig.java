package com.einar115.thearchivist.rag;

import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public RetrievalAugmentationAdvisor ragAdvisor(VectorStore vectorStore, @Value("${app.rag.top-k:5}") int topK, @Value("${app.rag.similarity-threshold:0.40}") double similarityThreshold) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .promptTemplate(ChatPrompts.USER_TEMPLATE)
                        .emptyContextPromptTemplate(ChatPrompts.EMPTY_CONTEXT_TEMPLATE)
                        // The default formatter keeps only the text; ours adds the [Fuente N | ...] header with the metadata.
                        .documentFormatter(documents -> RetrievedContext.from(documents).text())
                        .build())
                .build();
    }

}