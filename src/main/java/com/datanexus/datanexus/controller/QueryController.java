package com.datanexus.datanexus.controller;

import com.datanexus.datanexus.dto.ApiResponse;
import com.datanexus.datanexus.dto.query.ExecuteQueryRequest;
import com.datanexus.datanexus.dto.query.QueryResultDto;
import com.datanexus.datanexus.dto.query.QuerySuggestionsRequest;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<QueryResultDto>> executeQuery(
            @Valid @RequestBody ExecuteQueryRequest request,
            @AuthenticationPrincipal User user) {
        QueryResultDto result = queryService.executeQuery(request, user);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/suggestions")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getSuggestions(
            @Valid @RequestBody QuerySuggestionsRequest request,
            @AuthenticationPrincipal User user) {
        List<String> suggestions = queryService.getQuerySuggestions(request, user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("suggestions", suggestions)));
    }
}
