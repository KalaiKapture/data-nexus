package com.datanexus.datanexus.service.mcp;

import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.service.datasource.ExecutionResult;
import com.datanexus.datanexus.service.datasource.request.MCPResourceRead;
import com.datanexus.datanexus.service.datasource.request.MCPToolCall;
import com.datanexus.datanexus.service.datasource.schema.MCPCapabilities;
import com.datanexus.datanexus.service.datasource.schema.MCPResource;
import com.datanexus.datanexus.service.datasource.schema.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Client for interacting with MCP (Model Context Protocol) servers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MCPClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Get server capabilities (list of tools and resources)
     */
    public MCPCapabilities getCapabilities(DatabaseConnection connection) {
        try {
            String serverUrl = connection.getHost();

            // Call MCP server to list tools
            List<MCPTool> tools = listTools(serverUrl, connection.getPassword());

            // Call MCP server to list resources
            List<MCPResource> resources = listResources(serverUrl, connection.getPassword());

            return MCPCapabilities.builder()
                    .connectionId(connection.getId().toString())
                    .serverName(connection.getName())
                    .tools(tools)
                    .resources(resources)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get MCP capabilities from {}: {}",
                    connection.getName(), e.getMessage());
            throw new RuntimeException("Failed to get MCP capabilities", e);
        }
    }

    /**
     * List available tools from MCP server
     */
    private List<MCPTool> listTools(String serverUrl, String authToken) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "jsonrpc", "2.0",
                "method", "tools/list",
                "id", UUID.randomUUID().toString());

        HttpRequest request = buildRequest(serverUrl + "/rpc", authToken, requestBody);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode toolsNode = root.path("result").path("tools");

        List<MCPTool> tools = new ArrayList<>();
        if (toolsNode.isArray()) {
            for (JsonNode toolNode : toolsNode) {
                tools.add(MCPTool.builder()
                        .name(toolNode.path("name").asText())
                        .description(toolNode.path("description").asText())
                        .inputSchema(toolNode.path("inputSchema"))
                        .build());
            }
        }

        return tools;
    }

    /**
     * List available resources from MCP server
     */
    private List<MCPResource> listResources(String serverUrl, String authToken) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "jsonrpc", "2.0",
                "method", "resources/list",
                "id", UUID.randomUUID().toString());

        HttpRequest request = buildRequest(serverUrl + "/rpc", authToken, requestBody);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode resourcesNode = root.path("result").path("resources");

        List<MCPResource> resources = new ArrayList<>();
        if (resourcesNode.isArray()) {
            for (JsonNode resourceNode : resourcesNode) {
                resources.add(MCPResource.builder()
                        .uri(resourceNode.path("uri").asText())
                        .name(resourceNode.path("name").asText())
                        .description(resourceNode.path("description").asText())
                        .mimeType(resourceNode.path("mimeType").asText())
                        .build());
            }
        }

        return resources;
    }

    /**
     * Invoke a tool on the MCP server
     */
    public ExecutionResult invokeTool(DatabaseConnection connection, MCPToolCall toolCall) {
        long startTime = System.currentTimeMillis();

        try {
            String serverUrl = connection.getHost();

            Map<String, Object> requestBody = Map.of(
                    "jsonrpc", "2.0",
                    "method", "tools/call",
                    "params", Map.of(
                            "name", toolCall.getToolName(),
                            "arguments", toolCall.getArguments() != null ? toolCall.getArguments() : Map.of()),
                    "id", UUID.randomUUID().toString());

            HttpRequest request = buildRequest(serverUrl + "/rpc", connection.getPassword(), requestBody);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long executionTime = System.currentTimeMillis() - startTime;

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode resultNode = root.path("result");

            // Convert result to list of maps for consistency
            List<Map<String, Object>> data = new ArrayList<>();
            data.add(objectMapper.convertValue(resultNode, Map.class));

            return ExecutionResult.builder()
                    .success(true)
                    .data(data)
                    .rowCount(1)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Failed to invoke MCP tool {}: {}", toolCall.getToolName(), e.getMessage());

            return ExecutionResult.builder()
                    .success(false)
                    .executionTimeMs(executionTime)
                    .errorMessage("Tool invocation failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Read a resource from the MCP server
     */
    public ExecutionResult readResource(DatabaseConnection connection, MCPResourceRead resourceRead) {
        long startTime = System.currentTimeMillis();

        try {
            String serverUrl = connection.getHost();

            Map<String, Object> requestBody = Map.of(
                    "jsonrpc", "2.0",
                    "method", "resources/read",
                    "params", Map.of("uri", resourceRead.getUri()),
                    "id", UUID.randomUUID().toString());

            HttpRequest request = buildRequest(serverUrl + "/rpc", connection.getPassword(), requestBody);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long executionTime = System.currentTimeMillis() - startTime;

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentsNode = root.path("result").path("contents");

            List<Map<String, Object>> data = new ArrayList<>();
            if (contentsNode.isArray()) {
                for (JsonNode contentNode : contentsNode) {
                    data.add(objectMapper.convertValue(contentNode, Map.class));
                }
            }

            return ExecutionResult.builder()
                    .success(true)
                    .data(data)
                    .rowCount(data.size())
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Failed to read MCP resource {}: {}", resourceRead.getUri(), e.getMessage());

            return ExecutionResult.builder()
                    .success(false)
                    .executionTimeMs(executionTime)
                    .errorMessage("Resource read failed: " + e.getMessage())
                    .build();
        }
    }

    private HttpRequest buildRequest(String url, String authToken, Map<String, Object> requestBody)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));

        if (authToken != null && !authToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + authToken);
        }

        return builder.build();
    }
}
