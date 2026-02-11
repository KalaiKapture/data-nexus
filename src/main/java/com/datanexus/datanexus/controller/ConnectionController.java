package com.datanexus.datanexus.controller;

import com.datanexus.datanexus.dto.ApiResponse;
import com.datanexus.datanexus.dto.connection.*;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.service.ConnectionService;
import com.datanexus.datanexus.service.SchemaCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/connections")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final SchemaCacheService schemaCacheService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConnections(@AuthenticationPrincipal User user) {
        List<ConnectionDto> connections = connectionService.getUserConnections(user);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "connections", connections,
                "total", connections.size())));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<TestConnectionResponse>> testConnection(
            @Valid @RequestBody TestConnectionRequest request) {
        TestConnectionResponse response = connectionService.testConnection(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, ConnectionDto>>> createConnection(
            @Valid @RequestBody ConnectionRequest request,
            @AuthenticationPrincipal User user) {
        ConnectionDto connection = connectionService.createConnection(request, user);
        return new ResponseEntity<>(ApiResponse.success(Map.of("connection", connection)), HttpStatus.CREATED);
    }

    @PutMapping("/{connectionId}")
    public ResponseEntity<ApiResponse<Map<String, ConnectionDto>>> updateConnection(
            @PathVariable Long connectionId,
            @Valid @RequestBody ConnectionRequest request,
            @AuthenticationPrincipal User user) {
        ConnectionDto connection = connectionService.updateConnection(connectionId, request, user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("connection", connection)));
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<ApiResponse<Void>> deleteConnection(
            @PathVariable Long connectionId,
            @AuthenticationPrincipal User user) {
        connectionService.deleteConnection(connectionId, user);
        return ResponseEntity.ok(ApiResponse.success("Connection deleted successfully"));
    }

    @PatchMapping("/{connectionId}/last-used")
    public ResponseEntity<ApiResponse<Map<String, ConnectionDto>>> updateLastUsed(
            @PathVariable Long connectionId,
            @AuthenticationPrincipal User user) {
        ConnectionDto connection = connectionService.updateLastUsed(connectionId, user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("connection", connection)));
    }

    @GetMapping("/{connectionId}/schema")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchema(
            @PathVariable Long connectionId,
            @AuthenticationPrincipal User user) {
        Map<String, Object> schema = connectionService.getSchema(connectionId, user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("schema", schema)));
    }

    @PostMapping("/{connectionId}/refresh-schema")
    public ResponseEntity<ApiResponse<Map<String, String>>> refreshSchema(
            @PathVariable("connectionId") Long connectionId,
            @AuthenticationPrincipal User user) {

        schemaCacheService.refreshSchema(connectionId, user.getId());

        return ResponseEntity.ok(
                ApiResponse.success(
                        Map.of("message", "Schema and sample data refreshed successfully")
                )
        );
    }
}
