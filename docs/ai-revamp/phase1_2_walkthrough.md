# Phase 1 & 2 Completion: Data Source Abstraction Layer

## Summary

Successfully implemented the foundation for multi-source data integration with support for both databases and MCP servers.

---

## What Was Built

### Core Abstractions

#### 1. **DataSource Interface** ([DataSource.java](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/DataSource.java))

The unified contract for all data sources:

```java
public interface DataSource {
    String getId();
    String getName();
    DataSourceType getType();
    SourceSchema extractSchema();
    ExecutionResult execute(DataRequest request);
    boolean isAvailable();
    String getConnectionInfo();
}
```

**Supported types:** Database, MCP Server, REST API, GraphQL API (extensible)

---

#### 2. **DataRequest Polymorphism** 

Base interface for all data source requests:

**Implementations:**
- [`SqlQuery`](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/request/SqlQuery.java) - SQL database queries
- [`MCPToolCall`](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/request/MCPToolCall.java) - MCP tool invocations
- [`MCPResourceRead`](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/request/MCPResourceRead.java) - MCP resource reads

---

#### 3. **ExecutionResult Model** ([ExecutionResult.java](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/ExecutionResult.java))

Unified result format across all data sources:

```java
ExecutionResult {
    boolean success;
    List<Map<String, Object>> data;
    List<String> columns;
    int rowCount;
    long executionTimeMs;
    String errorMessage;
}
```

---

### Schema Models

#### 4. **SourceSchema** ([SourceSchema.java](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/schema/SourceSchema.java))

Unified schema representation:

```java
SourceSchema {
    String sourceId;
    String sourceName;
    DataSourceType sourceType;
    Object schemaData; // Database: tables/columns, MCP: tools/resources
}
```

Factory methods:
- `fromDatabase()` - Convert existing DatabaseSchema
- `fromMCP()` - Convert MCP capabilities

---

#### 5. **MCP Schema Models**

**[MCPCapabilities](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/schema/MCPCapabilities.java):**
```java
{
    String connectionId;
    String serverName;
    List<MCPTool> tools;
    List<MCPResource> resources;
}
```

**[MCPTool](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/schema/MCPTool.java):**
```java
{
    String name;
    String description;
    Object inputSchema; // JSON Schema
}
```

**[MCPResource](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/schema/MCPResource.java):**
```java
{
    String uri;
    String name;
    String description;
    String mimeType;
}
```

---

### Implementations

#### 6. **DatabaseDataSource** ([DatabaseDataSource.java](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/impl/DatabaseDataSource.java))

Wraps existing database functionality into `DataSource` interface:

- ✅ Reuses `SchemaService` for schema extraction
- ✅ Reuses `QueryExecutionService` for query execution
- ✅ Supports PostgreSQL and MySQL
- ✅ Validates only `SqlQuery` requests

**Example usage:**
```java
DataSource db = databaseDataSourceFactory.create(connection);
SourceSchema schema = db.extractSchema();
ExecutionResult result = db.execute(SqlQuery.builder()
    .sql("SELECT * FROM users LIMIT 10")
    .build());
```

---

#### 7. **MCPServerDataSource** ([MCPServerDataSource.java](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/impl/MCPServerDataSource.java))

Integrates MCP servers as data sources:

- ✅ Extracts tools and resources as schema
- ✅ Supports tool invocations
- ✅ Supports resource reads
- ✅ Uses `MCPClient` for JSON-RPC communication

**Example usage:**
```java
DataSource mcp = mcpDataSourceFactory.create(connection);
SourceSchema schema = mcp.extractSchema();

// Invoke tool
ExecutionResult result = mcp.execute(MCPToolCall.builder()
    .toolName("search_documents")
    .arguments(Map.of("query", "sales report"))
    .build());
```

---

### Infrastructure

#### 8. **DataSourceRegistry** ([DataSourceRegistry.java](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/datasource/DataSourceRegistry.java))

Central registry for managing data sources:

**Features:**
- ✅ Creates appropriate `DataSource` based on connection type
- ✅ Caches data sources for performance
- ✅ Security: User-scoped data source retrieval
- ✅ Health checking

**API:**
```java
// Get by connection object
DataSource ds = registry.getDataSource(connection);

// Get by ID with security
DataSource ds = registry.getDataSourceByConnectionId(connId, userId);

// Get all for user
List<DataSource> all = registry.getAllDataSources(userId);

// Clear cache
registry.clearCache(connectionId);
```

---

#### 9. **MCPClient** ([MCPClient.java](file:///Users/kalaimaran.m/Documents/DataNexus/data-nexus/src/main/java/com/datanexus/datanexus/service/mcp/MCPClient.java))

JSON-RPC client for MCP server communication:

**Implemented Methods:**
- `getCapabilities()` - List tools and resources
- `invokeTool()` - Call MCP tool with arguments
- `readResource()` - Read MCP resource by URI

**Protocol:**
- JSON-RPC 2.0 over HTTP
- Bearer token authentication support
- 60-second timeout
- Proper error handling

**Example JSON-RPC request:**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "search_documents",
    "arguments": {"query": "Q1 sales"}
  },
  "id": "uuid-here"
}
```

--- ## Integration Example

### Unified Multi-Source Query

```java
@Autowired
private DataSourceRegistry registry;

public void analyzeAcrossSources(User user, String query) {
    // Get all user's data sources (databases + MCP servers)
    List<DataSource> sources = registry.getAllDataSources(user.getId());
    
    for (DataSource source : sources) {
        // Extract schema
        SourceSchema schema = source.extractSchema();
        
        // Generate appropriate request based on source type
        DataRequest request = switch (source.getType()) {
            case DATABASE -> SqlQuery.builder()
                .sql("SELECT * FROM sales WHERE date > '2026-01-01'")
                .build();
                
            case MCP_SERVER -> MCPToolCall.builder()
                .toolName("get_analytics")
                .arguments(Map.of("metric", "sales", "period", "Q1"))
                .build();
                
            default -> throw new UnsupportedOperationException();
        };
        
        // Execute
        ExecutionResult result = source.execute(request);
        
        // Process unified result
        if (result.isSuccess()) {
            System.out.println("Got " + result.getRowCount() + 
                             " rows from " + source.getName());
        }
    }
}
```

---

## DatabaseConnection Extension

### Reusing Existing Entity

No new tables needed! The existing `database_connections` table supports MCP servers:

| Field | Database Value | MCP Server Value |
|-------|---------------|------------------|
| `type` | "postgresql", "mysql" | **"MCP"** |
| `host` | Database host | MCP server URL |
| `password` | DB password | Auth token |
| `other_details` | Extra config | **MCP capabilities JSON** |

**Example MCP connection:**
```java
DatabaseConnection mcpConn = DatabaseConnection.builder()
    .type("MCP")
    .name("Analytics MCP Server")
    .host("http://localhost:3000")
    .password("bearer-token-here")
    .otherDetails("{\"transport\":\"http\",\"capabilities\":{...}}")
    .userId(user.getId())
    .build();
```

---

## File Structure

```
src/main/java/com/datanexus/datanexus/
├── service/
│   ├── datasource/
│   │   ├── DataSource.java ..................... Interface
│   │   ├── DataSourceType.java ................. Enum
│   │   ├── DataSourceRegistry.java ............. Registry Service
│   │   ├── DataRequest.java .................... Interface
│   │   ├── DataRequestType.java ................ Enum
│   │   ├── ExecutionResult.java ................ Model
│   │   ├── impl/
│   │   │   ├── DatabaseDataSource.java ......... Database implementation
│   │   │   └── MCPServerDataSource.java ........ MCP implementation
│   │   ├── request/
│   │   │   ├── SqlQuery.java ................... SQL request
│   │   │   ├── MCPToolCall.java ................ MCP tool request
│   │   │   └── MCPResourceRead.java ............ MCP resource request
│   │   └── schema/
│   │       ├── SourceSchema.java ............... Unified schema
│   │       ├── MCPCapabilities.java ............ MCP capabilities
│   │       ├── MCPTool.java .................... MCP tool model
│   │       └── MCPResource.java ................ MCP resource model
│   └── mcp/
│       └── MCPClient.java ...................... MCP JSON-RPC client
```

---

## Next Steps

✅ **Phase 1 & 2 Complete**

**Ready for Phase 3: AI Provider Integration**
- Create `AIProvider` interface
- Implement `GeminiProvider`
- Implement `ClaudeProvider`
- Implement `OpenAIProvider`
- Create `AIProviderFactory`

This will enable the conversational AI layer to generate appropriate requests for each data source type.
