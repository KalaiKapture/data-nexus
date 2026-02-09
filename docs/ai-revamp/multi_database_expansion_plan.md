# Multi-Database Support Expansion Plan

## Goal

Expand DataNexus to support 10+ popular data sources:
- **SQL Databases**: PostgreSQL, MySQL, SQLite, Supabase, StarRocks
- **NoSQL**: MongoDB, Redis
- **Analytics/Warehouses**: ClickHouse, Snowflake
- **Search**: Elasticsearch
- **Plus**: MCP Servers (already in progress)

---

## Current State Analysis

### Existing Support ✅
- PostgreSQL
- MySQL  
- SQL Server (likely)

### Architecture Already in Place ✅
- `DatabaseConnection` entity has `type`, `typeName`, `typeIcon` fields
- `DatabaseDataSource` uses JDBC for SQL databases
- `DataSourceRegistry` for managing sources
- `UnifiedExecutionService` for multi-source execution

---

## Supported Databases Configuration

### 1. **PostgreSQL** 🐘
```java
{
  id: "postgresql",
  name: "PostgreSQL",
  icon: "🐘",
  color: "from-blue-500 to-blue-600",
  driver: "org.postgresql.Driver",
  urlPattern: "jdbc:postgresql://{host}:{port}/{database}",
  defaultPort: "5432",
  features: ["SQL", "JSON", "Full-Text Search", "Geospatial"]
}
```

### 2. **MySQL** 🐬
```java
{
  id: "mysql",
  name: "MySQL",
  icon: "🐬",
  color: "from-orange-500 to-orange-600",
  driver: "com.mysql.cj.jdbc.Driver",
  urlPattern: "jdbc:mysql://{host}:{port}/{database}",
  defaultPort: "3306",
  features: ["SQL", "JSON", "Full-Text Search"]
}
```

### 3. **MongoDB** 🍃
```java
{
  id: "mongodb",
  name: "MongoDB",
  icon: "🍃",
  color: "from-green-500 to-green-600",
  driver: "mongodb (MongoDB Java Driver)",
  urlPattern: "mongodb://{host}:{port}/{database}",
  defaultPort: "27017",
  features: ["NoSQL", "Document Store", "Aggregation Pipeline"]
}
```

### 4. **SQLite** 📦
```java
{
  id: "sqlite",
  name: "SQLite",
  icon: "📦",
  color: "from-cyan-500 to-cyan-600",
  driver: "org.sqlite.JDBC",
  urlPattern: "jdbc:sqlite:{filePath}",
  defaultPort: "N/A",
  features: ["SQL", "File-based", "Embedded"]
}
```

### 5. **Supabase** ⚡
```java
{
  id: "supabase",
  name: "Supabase",
  icon: "⚡",
  color: "from-emerald-500 to-emerald-600",
  driver: "org.postgresql.Driver", // PostgreSQL under the hood
  urlPattern: "jdbc:postgresql://{host}:{port}/{database}?sslmode=require",
  defaultPort: "5432",
  features: ["SQL", "Real-time", "Auth", "Storage"]
}
```

### 6. **StarRocks** ⭐
```java
{
  id: "starrocks",
  name: "StarRocks",
  icon: "⭐",
  color: "from-purple-500 to-purple-600",
  driver: "com.mysql.cj.jdbc.Driver", // MySQL protocol
  urlPattern: "jdbc:mysql://{host}:{port}/{database}",
  defaultPort: "9030",
  features: ["OLAP", "Real-time Analytics", "MPP"]
}
```

### 7. **Redis** 🔴
```java
{
  id: "redis",
  name: "Redis",
  icon: "🔴",
  color: "from-red-500 to-red-600",
  driver: "Redis Java Client (Jedis/Lettuce)",
  urlPattern: "redis://{host}:{port}",
  defaultPort: "6379",
  features: ["Key-Value", "Caching", "Pub/Sub", "Streams"]
}
```

### 8. **Elasticsearch** 🔍
```java
{
  id: "elasticsearch",
  name: "Elasticsearch",
  icon: "🔍",
  color: "from-yellow-500 to-yellow-600",
  driver: "Elasticsearch Java Client",
  urlPattern: "http://{host}:{port}",
  defaultPort: "9200",
  features: ["Search", "Analytics", "Full-Text", "Aggregations"]
}
```

### 9. **ClickHouse** ⚡
```java
{
  id: "clickhouse",
  name: "ClickHouse",
  icon: "⚡",
  color: "from-amber-500 to-amber-600",
  driver: "com.clickhouse.jdbc.ClickHouseDriver",
  urlPattern: "jdbc:clickhouse://{host}:{port}/{database}",
  defaultPort: "8123",
  features: ["OLAP", "Column-oriented", "Real-time Analytics"]
}
```

### 10. **Snowflake** ❄️
```java
{
  id: "snowflake",
  name: "Snowflake",
  icon: "❄️",
  color: "from-sky-500 to-sky-600",
  driver: "net.snowflake.client.jdbc.SnowflakeDriver",
  urlPattern: "jdbc:snowflake://{account}.snowflakecomputing.com",
  defaultPort: "443",
  features: ["Cloud Warehouse", "SQL", "Zero-copy Cloning"]
}
```

---

## Implementation Strategy

### Phase 1: Create Database Type Enum

Create `DatabaseType.java` enum to centralize database configurations:

```java
public enum DatabaseType {
    POSTGRESQL("postgresql", "PostgreSQL", "🐘", "from-blue-500 to-blue-600", 
               "org.postgresql.Driver", "jdbc:postgresql://{host}:{port}/{database}", "5432"),
    MYSQL("mysql", "MySQL", "🐬", "from-orange-500 to-orange-600",
          "com.mysql.cj.jdbc.Driver", "jdbc:mysql://{host}:{port}/{database}", "3306"),
    MONGODB("mongodb", "MongoDB", "🍃", "from-green-500 to-green-600",
            null, "mongodb://{host}:{port}/{database}", "27017"),
    SQLITE("sqlite", "SQLite", "📦", "from-cyan-500 to-cyan-600",
           "org.sqlite.JDBC", "jdbc:sqlite:{filePath}", null),
    // ... more types
    
    private final String id;
    private final String name;
    private final String icon;
    private final String color;
    private final String driver;
    private final String urlPattern;
    private final String defaultPort;
    
    // Helper methods
    public boolean isSqlBased() { /* ... */ }
    public boolean isNoSql() { /* ... */ }
    public String buildJdbcUrl(String host, String port, String database) { /* ... */ }
}
```

### Phase 2: Extend DatabaseConnection Validation

Add validation for database-specific connection parameters:

```java
@PrePersist
@PreUpdate
private void validateConnection() {
    DatabaseType dbType = DatabaseType.fromId(this.type);
    
    // Validate required fields based on database type
    if (dbType.isSqlBased() && StringUtils.isBlank(database)) {
        throw new IllegalStateException("Database name required for SQL databases");
    }
    
    if (dbType == DatabaseType.SQLITE && StringUtils.isBlank(otherDetails)) {
        throw new IllegalStateException("File path required for SQLite in otherDetails");
    }
}
```

### Phase 3: Create Specialized DataSource Implementations

For databases that don't use JDBC (MongoDB, Redis, Elasticsearch):

```java
// MongoDBDataSource.java
public class MongoDBDataSource implements DataSource {
    private final DatabaseConnection connection;
    private final MongoClient mongoClient;
    
    @Override
    public ExecutionResult execute(DataRequest request) {
        // MongoDB-specific query execution
    }
}

// RedisDataSource.java  
public class RedisDataSource implements DataSource {
    private final DatabaseConnection connection;
    private final RedisClient redisClient;
    
    @Override
    public ExecutionResult execute(DataRequest request) {
        // Redis command execution
    }
}
```

### Phase 4: Update DataSourceRegistry

Enhance registry to create appropriate DataSource based on database type:

```java
public DataSource getDataSource(DatabaseConnection connection) {
    DatabaseType dbType = DatabaseType.fromId(connection.getType());
    
    return switch (dbType.getCategory()) {
        case SQL -> databaseDataSourceFactory.create(connection);
        case MONGODB -> mongoDataSourceFactory.create(connection);
        case REDIS -> redisDataSourceFactory.create(connection);
        case ELASTICSEARCH -> elasticsearchDataSourceFactory.create(connection);
        case MCP -> mcpDataSourceFactory.create(connection);
        default -> throw new UnsupportedOperationException("Unsupported database type");
    };
}
```

### Phase 5: Extend Schema Extraction

Each database type needs custom schema extraction:

```java
// SchemaExtractorFactory.java
public interface SchemaExtractor {
    SourceSchema extract(DatabaseConnection connection);
}

public class PostgreSQLSchemaExtractor implements SchemaExtractor { /* ... */ }
public class MongoDBSchemaExtractor implements SchemaExtractor { /* ... */ }
public class RedisSchemaExtractor implements SchemaExtractor { /* ... */ }
```

### Phase 6: Update AI Providers

AI providers need to understand different database types:

```java
// In prompt building
if (dbType == DatabaseType.MONGODB) {
    prompt += "Generate MongoDB aggregation pipeline or find query\n";
} else if (dbType == DatabaseType.REDIS) {
    prompt += "Generate Redis commands\n";
} else {
    prompt += "Generate SQL query for " + dbType.getName() + "\n";
}
```

### Phase 7: Add Maven Dependencies

```xml
<!-- pom.xml -->
<dependencies>
    <!-- MongoDB -->
    <dependency>
        <groupId>org.mongodb</groupId>
        <artifactId>mongodb-driver-sync</artifactId>
        <version>4.11.1</version>
    </dependency>
    
    <!-- Redis -->
    <dependency>
        <groupId>redis.clients</groupId>
        <artifactId>jedis</artifactId>
        <version>5.0.2</version>
    </dependency>
    
    <!-- Elasticsearch -->
    <dependency>
        <groupId>co.elastic.clients</groupId>
        <artifactId>elasticsearch-java</artifactId>
        <version>8.11.0</version>
    </dependency>
    
    <!-- ClickHouse -->
    <dependency>
        <groupId>com.clickhouse</groupId>
        <artifactId>clickhouse-jdbc</artifactId>
        <version>0.5.0</version>
    </dependency>
    
    <!-- Snowflake -->
    <dependency>
        <groupId>net.snowflake</groupId>
        <artifactId>snowflake-jdbc</artifactId>
        <version>3.14.4</version>
    </dependency>
    
    <!-- SQLite -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.44.1.0</version>
    </dependency>
</dependencies>
```

---

## Database-Specific Considerations

### MongoDB
- **Query Language**: MQL (MongoDB Query Language), not SQL
- **Schema**: Schemaless, need to infer from sample documents
- **AI Generation**: Must generate JSON queries/aggregations

### Redis
- **Query Language**: Redis commands (GET, SET, HGETALL, etc.)
- **Schema**: Key patterns and data types
- **AI Generation**: Must generate Redis commands

### Elasticsearch
- **Query Language**: Query DSL (JSON-based)
- **Schema**: Index mappings
- **AI Generation**: Must generate Elasticsearch JSON queries

### SQLite
- **Special**: File-based, no host/port
- **Storage**: File path in `otherDetails` field
- **Connection**: Single-user, no authentication typically

### Supabase
- **Backend**: PostgreSQL with extensions
- **Special**: Real-time capabilities, Row-level security
- **Connection**: Requires SSL

---

## Frontend Changes Needed

### Database Selection UI
```jsx
const DATABASE_TYPES = [
  { id: "postgresql", name: "PostgreSQL", icon: "🐘", color: "from-blue-500 to-blue-600" },
  { id: "mysql", name: "MySQL", icon: "🐬", color: "from-orange-500 to-orange-600" },
  { id: "mongodb", name: "MongoDB", icon: "🍃", color: "from-green-500 to-green-600" },
  { id: "sqlite", name: "SQLite", icon: "📦", color: "from-cyan-500 to-cyan-600" },
  { id: "supabase", name: "Supabase", icon: "⚡", color: "from-emerald-500 to-emerald-600" },
  { id: "starrocks", name: "StarRocks", icon: "⭐", color: "from-purple-500 to-purple-600" },
  { id: "redis", name: "Redis", icon: "🔴", color: "from-red-500 to-red-600" },
  { id: "elasticsearch", name: "Elasticsearch", icon: "🔍", color: "from-yellow-500 to-yellow-600" },
  { id: "clickhouse", name: "ClickHouse", icon: "⚡", color: "from-amber-500 to-amber-600" },
  { id: "snowflake", name: "Snowflake", icon: "❄️", color: "from-sky-500 to-sky-600" },
  { id: "mcp", name: "MCP Server", icon: "🔌", color: "from-indigo-500 to-indigo-600" }
];
```

### Dynamic Form Fields
```jsx
{selectedType === 'sqlite' && (
  <input name="filePath" placeholder="/path/to/database.db" />
)}

{selectedType === 'mongodb' && (
  <input name="authDatabase" placeholder="admin" />
)}

{selectedType === 'snowflake' && (
  <input name="account" placeholder="account-identifier" />
)}
```

---

## Testing Strategy

### Unit Tests
- Test each database type's connection string building
- Test schema extraction for each type
- Test query generation for each type

### Integration Tests
- Test actual connections to test instances of each database
- Use Testcontainers for Docker-based databases

### Connection Testing
```java
@Test
void testPostgreSQLConnection() { /* ... */ }

@Test
void testMongoDBConnection() { /* ... */ }

@Test
void testRedisConnection() { /* ... */ }
```

---

## Migration Path

### Database Schema Changes
No schema changes needed! Existing `DatabaseConnection` already supports this via:
- `type` field - stores database type ID
- `typeName` field - stores display name
- `typeIcon` field - stores icon emoji
- `otherDetails` field - JSON for database-specific config

### Backward Compatibility
Existing connections will continue to work. New types are additive.

---

## Rollout Phases

### Phase 1 (Week 1): SQL Databases
- PostgreSQL ✅ (already supported)
- MySQL ✅ (already supported)
- SQLite (new)
- ClickHouse (new)

### Phase 2 (Week 2): Cloud & Analytics
- Supabase (new)
- StarRocks (new)
- Snowflake (new)

### Phase 3 (Week 3): NoSQL
- MongoDB (new)
- Redis (new)

### Phase 4 (Week 4): Search & Polish
- Elasticsearch (new)
- Testing & Polish
- Documentation

---

## Open Questions

1. **MongoDB Query Generation**: Should AI generate MQL or keep it simple with find() queries?
2. **Redis Limitations**: How to handle Redis' non-relational nature in a "query" context?
3. **Elasticsearch**: Should we support both SQL API and native Query DSL?
4. **Authentication**: OAuth for cloud databases (Snowflake, BigQuery)?

---

## Success Criteria

✅ Users can connect to all 10+ database types
✅ Schema extraction works for each type
✅ AI can generate appropriate queries for each type
✅ Query execution returns consistent results format
✅ Error handling is database-aware
✅ Frontend shows proper icons and colors
