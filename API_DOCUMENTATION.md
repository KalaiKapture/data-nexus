# DataNexus API Documentation

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication
All endpoints except `/auth/**` and `/shared/**` require a valid JWT token in the Authorization header:
```
Authorization: Bearer <jwt_token>
```

---

## Authentication Endpoints

### Register
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123",
  "fullName": "John Doe",
  "organizationName": "Acme Corp"  // optional
}
```

### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresIn": 86400000,
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "fullName": "John Doe",
      "role": "ADMIN",
      "organization": { ... }
    }
  }
}
```

### Refresh Token
```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

### Logout
```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

### Get Current User
```http
GET /api/v1/auth/me
Authorization: Bearer <token>
```

---

## Database Connection Endpoints

### Create Connection
```http
POST /api/v1/connections
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Production MySQL",
  "dbType": "MYSQL",  // MYSQL, POSTGRESQL, SUPABASE, STARROCKS
  "host": "db.example.com",
  "port": 3306,
  "databaseName": "analytics",
  "username": "readonly_user",
  "password": "secure_password"
}
```

### Get All Connections
```http
GET /api/v1/connections
Authorization: Bearer <token>
```

### Get Connection
```http
GET /api/v1/connections/{connectionId}
Authorization: Bearer <token>
```

### Update Connection
```http
PUT /api/v1/connections/{connectionId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Updated MySQL",
  "dbType": "MYSQL",
  "host": "new-host.example.com",
  "port": 3306,
  "databaseName": "analytics",
  "username": "readonly_user",
  "password": "new_password"
}
```

### Delete Connection
```http
DELETE /api/v1/connections/{connectionId}
Authorization: Bearer <token>
```

### Test Connection
```http
POST /api/v1/connections/{connectionId}/test
Authorization: Bearer <token>
```

### Get Schema (Tables & Columns)
```http
GET /api/v1/connections/{connectionId}/schema
Authorization: Bearer <token>
```

### Refresh Schema Cache
```http
POST /api/v1/connections/{connectionId}/schema/refresh
Authorization: Bearer <token>
```

---

## Query Endpoints

### Execute SQL Query
```http
POST /api/v1/queries/execute
Authorization: Bearer <token>
Content-Type: application/json

{
  "connectionIds": ["uuid1", "uuid2"],
  "query": "SELECT * FROM customers LIMIT 100",
  "options": {
    "timeout": 30000,
    "maxRows": 1000,
    "page": 1,
    "pageSize": 100
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "executionId": "uuid",
    "columns": [
      {"name": "id", "type": "INTEGER"},
      {"name": "name", "type": "VARCHAR"}
    ],
    "rows": [
      [1, "John"],
      [2, "Jane"]
    ],
    "totalRows": 2,
    "executionTimeMs": 145,
    "truncated": false,
    "pagination": {
      "page": 1,
      "pageSize": 100,
      "totalPages": 1,
      "totalElements": 2
    }
  }
}
```

### Prompt to SQL (Natural Language)
```http
POST /api/v1/queries/prompt
Authorization: Bearer <token>
Content-Type: application/json

{
  "connectionIds": ["uuid1"],
  "prompt": "Show me total sales by customer",
  "executeImmediately": true,
  "options": {
    "timeout": 30000,
    "maxRows": 1000
  }
}
```

### Save Query
```http
POST /api/v1/queries/saved
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Monthly Sales Report",
  "description": "Aggregates sales by month",
  "queryText": "SELECT DATE_TRUNC('month', date) as month, SUM(amount) FROM sales GROUP BY 1",
  "connectionIds": ["uuid1"],
  "isPromptGenerated": false
}
```

### Get Saved Queries
```http
GET /api/v1/queries/saved
Authorization: Bearer <token>
```

### Get Saved Queries (Paginated)
```http
GET /api/v1/queries/saved/paginated?page=0&size=20
Authorization: Bearer <token>
```

### Get Saved Query
```http
GET /api/v1/queries/saved/{queryId}
Authorization: Bearer <token>
```

### Update Saved Query
```http
PUT /api/v1/queries/saved/{queryId}
Authorization: Bearer <token>
Content-Type: application/json
```

### Delete Saved Query
```http
DELETE /api/v1/queries/saved/{queryId}
Authorization: Bearer <token>
```

### Execute Saved Query
```http
POST /api/v1/queries/saved/{queryId}/execute
Authorization: Bearer <token>
Content-Type: application/json

{
  "timeout": 30000,
  "maxRows": 1000
}
```

### Format for Visualization
```http
POST /api/v1/queries/visualize?chartType=BAR_CHART
Authorization: Bearer <token>
Content-Type: application/json

// Body: QueryResultResponse from execute endpoint
```

### Export Data
```http
POST /api/v1/queries/export
Authorization: Bearer <token>
Content-Type: application/json

{
  "format": "CSV",  // CSV, EXCEL, JSON
  "fileName": "export",
  "includeHeaders": true
}
// Body also includes QueryResultResponse
```

---

## Dashboard Endpoints

### Create Dashboard
```http
POST /api/v1/dashboards
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Sales Dashboard",
  "description": "Overview of sales metrics",
  "layout": "{\"columns\": 12, \"rowHeight\": 100}",
  "isPublic": false
}
```

### Get All Dashboards
```http
GET /api/v1/dashboards
Authorization: Bearer <token>
```

### Get Dashboard
```http
GET /api/v1/dashboards/{dashboardId}
Authorization: Bearer <token>
```

### Update Dashboard
```http
PUT /api/v1/dashboards/{dashboardId}
Authorization: Bearer <token>
Content-Type: application/json
```

### Delete Dashboard
```http
DELETE /api/v1/dashboards/{dashboardId}
Authorization: Bearer <token>
```

### Add Widget to Dashboard
```http
POST /api/v1/dashboards/{dashboardId}/widgets
Authorization: Bearer <token>
Content-Type: application/json

{
  "savedQueryId": "uuid",
  "widgetType": "BAR_CHART",  // TABLE, BAR_CHART, LINE_CHART, PIE_CHART, AREA_CHART, SCATTER_CHART, METRIC_CARD
  "title": "Monthly Revenue",
  "config": "{\"colors\": [\"#3B82F6\"]}",
  "positionX": 0,
  "positionY": 0,
  "width": 6,
  "height": 4
}
```

### Update Widget
```http
PUT /api/v1/dashboards/{dashboardId}/widgets/{widgetId}
Authorization: Bearer <token>
Content-Type: application/json
```

### Delete Widget
```http
DELETE /api/v1/dashboards/{dashboardId}/widgets/{widgetId}
Authorization: Bearer <token>
```

---

## Sharing Endpoints

### Create Share Link
```http
POST /api/v1/dashboards/{dashboardId}/share
Authorization: Bearer <token>
Content-Type: application/json

{
  "expiresInDays": 7,
  "maxAccessCount": 100
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "shareToken": "abc123xyz789",
    "shareUrl": "http://localhost:8080/shared/abc123xyz789",
    "expiresAt": "2024-01-22T10:30:00Z",
    "maxAccessCount": 100,
    "accessCount": 0,
    "isActive": true
  }
}
```

### Get Share Links
```http
GET /api/v1/dashboards/{dashboardId}/share
Authorization: Bearer <token>
```

### Revoke Share Link
```http
DELETE /api/v1/dashboards/{dashboardId}/share/{shareLinkId}
Authorization: Bearer <token>
```

### Access Shared Dashboard (Public)
```http
GET /api/v1/shared/{token}
```

---

## Role-Based Access Control (RBAC)

| Role    | Permissions |
|---------|-------------|
| ADMIN   | Full access - manage connections, users, dashboards, queries |
| ANALYST | Create/edit queries and dashboards, read connections |
| VIEWER  | Read-only access to shared dashboards |

---

## Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": {}  // Optional additional details
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Common Error Codes
- `VALIDATION_ERROR` - Invalid request data
- `UNAUTHORIZED` - Missing or invalid authentication
- `FORBIDDEN` - Insufficient permissions
- `RESOURCE_NOT_FOUND` - Requested resource doesn't exist
- `INVALID_QUERY` - SQL query validation failed
- `QUERY_TIMEOUT` - Query execution timed out
- `CONNECTION_ERROR` - Database connection failed
- `RATE_LIMIT_EXCEEDED` - Too many requests
- `INTERNAL_ERROR` - Unexpected server error

---

## Rate Limiting

- **Requests per minute:** 60 (configurable)
- **Queries per minute:** 30 (configurable)

Rate limit headers are included in responses:
```
X-RateLimit-Remaining: 59
X-RateLimit-Reset: 1705312800
```

---

## Swagger UI

Interactive API documentation available at:
```
http://localhost:8080/swagger-ui.html
```
