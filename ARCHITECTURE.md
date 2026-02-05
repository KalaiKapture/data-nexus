# DataNexus - B2B SaaS Data Connector & Analytics Platform

## High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND (React.js)                                 │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────┐ │
│  │    Auth     │ │  Connection │ │   Query     │ │  Dashboard  │ │  Shared   │ │
│  │   Pages     │ │   Manager   │ │   Editor    │ │   Builder   │ │  Viewer   │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ HTTPS/REST API
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           API GATEWAY / LOAD BALANCER                            │
│                        (Rate Limiting, SSL Termination)                          │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          BACKEND (Spring Boot)                                   │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                         SECURITY LAYER                                      │ │
│  │   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │ │
│  │   │  JWT Filter  │  │  RBAC Auth   │  │ Rate Limiter │  │ Audit Logger  │  │ │
│  │   └──────────────┘  └──────────────┘  └──────────────┘  └───────────────┘  │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                          REST CONTROLLERS                                   │ │
│  │   ┌────────┐ ┌────────────┐ ┌─────────┐ ┌───────────┐ ┌─────────────────┐  │ │
│  │   │  Auth  │ │ Connection │ │  Query  │ │ Dashboard │ │  Share/Export   │  │ │
│  │   └────────┘ └────────────┘ └─────────┘ └───────────┘ └─────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                           SERVICE LAYER                                     │ │
│  │   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐    │ │
│  │   │   Connection    │  │     Query       │  │      Prompt-to-SQL      │    │ │
│  │   │    Service      │  │   Orchestrator  │  │        Service          │    │ │
│  │   └─────────────────┘  └─────────────────┘  └─────────────────────────┘    │ │
│  │   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐    │ │
│  │   │   Dashboard     │  │     Export      │  │     Visualization       │    │ │
│  │   │    Service      │  │    Service      │  │       Formatter         │    │ │
│  │   └─────────────────┘  └─────────────────┘  └─────────────────────────┘    │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                     DATABASE CONNECTOR LAYER                                │ │
│  │   ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐ │ │
│  │   │  MySQL   │ │ Postgres │ │ Supabase │ │ StarRocks│ │ Generic JDBC     │ │ │
│  │   │ Connector│ │ Connector│ │ Connector│ │ Connector│ │ Connector        │ │ │
│  │   └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────────────┘ │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                        REPOSITORY LAYER                                     │ │
│  │   ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐             │ │
│  │   │    User    │ │ Connection │ │  Dashboard │ │   Audit    │             │ │
│  │   │    Repo    │ │    Repo    │ │    Repo    │ │    Repo    │             │ │
│  │   └────────────┘ └────────────┘ └────────────┘ └────────────┘             │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
            ┌──────────────┐   ┌──────────────┐    ┌──────────────┐
            │   Internal   │   │    Redis     │    │   External   │
            │  PostgreSQL  │   │    Cache     │    │  Databases   │
            │  (Metadata)  │   │              │    │ (User Data)  │
            └──────────────┘   └──────────────┘    └──────────────┘


## API Flow Diagram

┌────────┐     ┌─────────┐     ┌──────────┐     ┌─────────┐     ┌──────────┐
│ Client │────▶│   JWT   │────▶│   Rate   │────▶│ Router  │────▶│Controller│
│        │     │  Filter │     │  Limiter │     │         │     │          │
└────────┘     └─────────┘     └──────────┘     └─────────┘     └──────────┘
                                                                      │
                                                                      ▼
┌────────┐     ┌─────────┐     ┌──────────┐     ┌─────────┐     ┌──────────┐
│Response│◀────│  Audit  │◀────│  Format  │◀────│ Service │◀────│Repository│
│        │     │  Log    │     │ Response │     │  Layer  │     │          │
└────────┘     └─────────┘     └──────────┘     └─────────┘     └──────────┘
```

## Internal Database Schema (Metadata)

```sql
-- Users and Authentication
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    organization_id UUID REFERENCES organizations(id),
    role VARCHAR(50) NOT NULL, -- ADMIN, ANALYST, VIEWER
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    plan_type VARCHAR(50) DEFAULT 'FREE', -- FREE, PRO, ENTERPRISE
    max_connections INT DEFAULT 5,
    max_queries_per_day INT DEFAULT 100,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Database Connections
CREATE TABLE database_connections (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    db_type VARCHAR(50) NOT NULL, -- MYSQL, POSTGRESQL, SUPABASE, STARROCKS
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(255) NOT NULL,
    username_encrypted BYTEA NOT NULL,
    password_encrypted BYTEA NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    last_tested_at TIMESTAMP,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Schema Cache
CREATE TABLE schema_cache (
    id UUID PRIMARY KEY,
    connection_id UUID REFERENCES database_connections(id),
    table_name VARCHAR(255) NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    data_type VARCHAR(100),
    is_nullable BOOLEAN,
    is_primary_key BOOLEAN,
    cached_at TIMESTAMP DEFAULT NOW()
);

-- Saved Queries
CREATE TABLE saved_queries (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    organization_id UUID REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    query_text TEXT NOT NULL,
    connection_ids UUID[] NOT NULL,
    is_prompt_generated BOOLEAN DEFAULT FALSE,
    original_prompt TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Dashboards
CREATE TABLE dashboards (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    organization_id UUID REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    layout JSONB, -- Grid layout configuration
    is_public BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Dashboard Widgets
CREATE TABLE dashboard_widgets (
    id UUID PRIMARY KEY,
    dashboard_id UUID REFERENCES dashboards(id),
    saved_query_id UUID REFERENCES saved_queries(id),
    widget_type VARCHAR(50) NOT NULL, -- TABLE, BAR_CHART, LINE_CHART, PIE_CHART
    title VARCHAR(255),
    config JSONB, -- Chart configuration, colors, etc.
    position_x INT,
    position_y INT,
    width INT,
    height INT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Shared Links
CREATE TABLE shared_links (
    id UUID PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    dashboard_id UUID REFERENCES dashboards(id),
    created_by UUID REFERENCES users(id),
    expires_at TIMESTAMP,
    access_count INT DEFAULT 0,
    max_access_count INT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Audit Logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    organization_id UUID REFERENCES organizations(id),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id UUID,
    details JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Query Execution History
CREATE TABLE query_executions (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    saved_query_id UUID REFERENCES saved_queries(id),
    query_text TEXT NOT NULL,
    connection_ids UUID[],
    execution_time_ms INT,
    row_count INT,
    status VARCHAR(50), -- SUCCESS, FAILED, TIMEOUT
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
```

## Sample API Request/Response

### 1. Create Database Connection
**Request:**
```http
POST /api/v1/connections
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "name": "Production MySQL",
  "dbType": "MYSQL",
  "host": "db.example.com",
  "port": 3306,
  "databaseName": "analytics",
  "username": "readonly_user",
  "password": "secure_password"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Production MySQL",
    "dbType": "MYSQL",
    "host": "db.example.com",
    "port": 3306,
    "databaseName": "analytics",
    "isActive": true,
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```

### 2. Execute Query
**Request:**
```http
POST /api/v1/queries/execute
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "connectionIds": ["550e8400-e29b-41d4-a716-446655440000"],
  "query": "SELECT customer_id, SUM(amount) as total FROM orders GROUP BY customer_id LIMIT 100",
  "options": {
    "timeout": 30000,
    "maxRows": 1000
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "executionId": "660e8400-e29b-41d4-a716-446655440001",
    "columns": [
      {"name": "customer_id", "type": "INTEGER"},
      {"name": "total", "type": "DECIMAL"}
    ],
    "rows": [
      [1, 1500.00],
      [2, 2300.50]
    ],
    "totalRows": 2,
    "executionTimeMs": 145,
    "truncated": false
  }
}
```

### 3. Generate Shareable Link
**Request:**
```http
POST /api/v1/dashboards/{dashboardId}/share
Authorization: Bearer <jwt_token>
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
    "shareToken": "abc123xyz789",
    "shareUrl": "https://datanexus.io/shared/abc123xyz789",
    "expiresAt": "2024-01-22T10:30:00Z",
    "maxAccessCount": 100
  }
}
```

## MVP vs Future Enhancements

### MVP Features
- User authentication (JWT)
- Basic RBAC (Admin, Analyst, Viewer)
- Database connections (MySQL, PostgreSQL)
- SQL query execution with safety checks
- Basic tabular data visualization
- Simple chart types (bar, line, pie)
- CSV export
- Basic dashboard creation
- Simple shareable links

### Phase 2 Enhancements
- Additional database support (Supabase, StarRocks, MongoDB)
- Natural language to SQL (AI-powered)
- Cross-database query merging
- Advanced chart customization
- Excel export with formatting
- Organization management
- Advanced RBAC with custom roles
- Query scheduling
- Email reports

### Phase 3 Enhancements
- Real-time data refresh
- Embedded dashboards (iframe)
- White-labeling
- SSO integration (SAML, OIDC)
- Data lineage tracking
- Query optimization suggestions
- Collaborative editing
- Mobile app
- API access for customers
- Webhook integrations

## Security Considerations

1. **Credential Encryption**: AES-256-GCM encryption for stored credentials
2. **Query Validation**: Strict SQL parsing to block write operations
3. **Connection Isolation**: Each connection uses isolated connection pools
4. **Rate Limiting**: Per-user and per-organization limits
5. **Audit Logging**: All actions logged with user context
6. **JWT Security**: Short-lived tokens with refresh mechanism
7. **HTTPS Only**: All traffic encrypted in transit
8. **Input Validation**: Strict validation on all inputs
9. **Error Handling**: No sensitive information in error responses
