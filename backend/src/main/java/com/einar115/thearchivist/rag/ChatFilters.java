package com.einar115.thearchivist.rag;


import com.einar115.thearchivist.dto.request.ChatRequest;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class ChatFilters {

    public static Filter.Expression from(ChatRequest request) {
        var b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();
        if (StringUtils.hasText(request.universe())) conditions.add(b.eq("universe", request.universe().strip()));
        if (request.canonOnly()) conditions.add(b.eq("canonicity", "CANON"));
        return conditions.stream().reduce(b::and).map(FilterExpressionBuilder.Op::build).orElse(null);
    }

    private  ChatFilters() {}
}
