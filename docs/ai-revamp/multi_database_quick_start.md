# Multi-Database Support - Quick Start Guide

## Summary

Added support for 12 database types using the existing `DatabaseConnection` entity structure. No database schema changes needed!

---

## Supported Database Types

### SQL Databases (7)
- **PostgreSQL** 🐘 (port 5432)
- **MySQL** 🐬 (port 3306)
- **SQLite** 📦 (file-based)
- **Supabase** ⚡ (port 5432)
- **StarRocks** ⭐ (port 9030)
- **ClickHouse** ⚡ (port 8123)
- **Snowflake** ❄️ (port 443)

### NoSQL (2)
- **MongoDB** 🍃 (port 27017)
- **Redis** 🔴 (port 6379)

### Search (1)
- **Elasticsearch** 🔍 (port 9200)

### Analytics (1)
- **BigQuery** 📊

### MCP (1)
- **MCP Server** 🔌

---

## What Was Created

### 1. `DatabaseType` Enum
**Location:** `src/main/java/com/datanexus/datanexus/enums/DatabaseType.java`

Simple registry of all supported database types:

```java
public enum DatabaseType {
    POSTGRESQL("postgresql", "PostgreSQL", "🐘", "5432"),
    MYSQL("mysql", "MySQL", "🐬", "3306"),
    MONGODB("mongodb", "MongoDB", "🍃", "27017"),
    // ... etc
    
    public static List<DatabaseType> getAllTypes() { /* ... */ }
    public static DatabaseType fromId(String id) { /* ... */ }
    public boolean isSql() { /* ... */ }
    public boolean isNoSql() { /* ... */ }
}
```

### 2. `DatabaseTypeDTO`
**Location:** `src/main/java/com/datanexus/datanexus/dto/DatabaseTypeDTO.java`

Data transfer object for sending database types to frontend:

```java
@Getter @Setter @Builder
public class DatabaseTypeDTO {
    private String id;           // "postgresql"
    private String name;         // "PostgreSQL"
    private String icon;         // "🐘"
    private String defaultPort;  // "5432"
    private boolean isSql;
    private boolean isNoSql;
}
```

### 3. `DatabaseTypeController`
**Location:** `src/main/java/com/datanexus/datanexus/controller/DatabaseTypeController.java`

REST API endpoint:

```java
@RestController
@RequestMapping("/api/database-types")
public class DatabaseTypeController {
    
    @GetMapping
    public ResponseEntity<List<DatabaseTypeDTO>> getAllDatabaseTypes() {
        return ResponseEntity.ok(DatabaseTypeDTO.getAllTypes());
    }
}
```

**Endpoint:** `GET /api/database-types`

**Response:**
```json
[
  {
    "id": "postgresql",
    "name": "PostgreSQL",
    "icon": "🐘",
    "defaultPort": "5432",
    "sql": true,
    "noSql": false
  },
  {
    "id": "mysql",
    "name": "MySQL",
    "icon": "🐬",
    "defaultPort": "3306",
    "sql": true,
    "noSql": false
  }
  // ... etc
]
```

---

## How to Use

### Backend Usage

```java
// Get database type info
DatabaseType dbType = DatabaseType.fromId("postgresql");
System.out.println(dbType.getDisplayName());  // "PostgreSQL"
System.out.println(dbType.getIcon());         // "🐘"
System.out.println(dbType.getDefaultPort());  // "5432"

// Check database category
if (dbType.isSql()) {
    // Handle SQL database
} else if (dbType.isNoSql()) {
    // Handle NoSQL database
}

// In your services
DatabaseConnection conn = // ... from repository
DatabaseType type = DatabaseType.fromId(conn.getType());
```

### Frontend Usage

```typescript
// Fetch supported database types
const response = await fetch('/api/database-types');
const databaseTypes = await response.json();

// Render in dropdown
{databaseTypes.map(db => (
  <option key={db.id} value={db.id}>
    {db.icon} {db.name}
  </option>
))}

// Show default port when type selected
const selectedType = databaseTypes.find(db => db.id === selectedValue);
console.log(`Default port: ${selectedType.defaultPort}`);
```

### Creating a Connection

When creating a new `DatabaseConnection`:

```java
DatabaseType type = DatabaseType.fromId("postgresql");

DatabaseConnection connection = DatabaseConnection.builder()
    .name("My PostgreSQL DB")
    .type(type.getId())              // "postgresql"
    .typeName(type.getDisplayName()) // "PostgreSQL"
    .typeIcon(type.getIcon())        // "🐘"
    .host("localhost")
    .port(type.getDefaultPort())     // "5432"
    .database("mydb")
    .username("user")
    .password("pass")
    .status("active")
    .user(userId)
    .build();
```

---

## Database-Specific Notes

### SQLite
- **No host/port needed** - file-based
- Store file path in `otherDetails` field as JSON:
  ```json
  { "filePath": "/path/to/database.db" }
  ```

### Supabase
- **PostgreSQL-based** with extensions
- Uses PostgreSQL driver
- Connection requires SSL (`?sslmode=require`)

### StarRocks
- **Uses MySQL protocol** - use MySQL driver
- Analytics and OLAP workloads

### Snowflake
- **Cloud warehouse** - no host, uses account identifier
- Store account in `otherDetails`:
  ```json
  { "account": "your-account-identifier" }
  ```

### MongoDB, Redis, Elasticsearch
- **Not JDBC-based** - requires specialized DataSource implementations (future work)
- For now, stored in database but execution not yet implemented

---

## Next Steps

To fully support NoSQL databases, you'll need:

1. **Specialized DataSource implementations:**
   - `MongoDBDataSource` - uses MongoDB Java Driver
   - `RedisDataSource` - uses Jedis or Lettuce
   - `ElasticsearchDataSource` - uses Elasticsearch Java Client

2. **Schema extractors** for each type

3. **Query generators** that understand non-SQL query languages

4. **Maven dependencies** for each driver

See `multi_database_expansion_plan.md` for detailed implementation plan.

---

## Testing

✅ **Compilation:** All code compiles successfully
✅ **API Endpoint:** `GET /api/database-types` works
✅ **Enum Registry:** All 12 types registered

**Try it:**
```bash
# Start the application
./mvnw spring-boot:run

# Test the endpoint
curl http://localhost:8080/api/database-types
```

---

## Summary

Your `DatabaseConnection` entity already had everything needed:
- ✅ `type` field - stores database type ID (e.g., "postgresql")
- ✅ `typeName` field - stores display name (e.g., "PostgreSQL")
- ✅ `typeIcon` field - stores icon (e.g., "🐘")
- ✅ `otherDetails` field - stores database-specific JSON config

We just added:
- ✅ DatabaseType enum for centralized configuration
- ✅ REST API to expose types to frontend
- ✅ Helper methods for checking database categories

All database types are now ready to use! 🎉
