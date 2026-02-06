# DataNexus — Complete API Reference for UI Integration

> **Purpose:** This document provides every detail needed to build a frontend UI that integrates with the DataNexus backend. It includes TypeScript types, endpoint specifications, request/response examples, authentication flow, WebSocket integration, and error handling.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication Flow](#2-authentication-flow)
3. [TypeScript Types (Shared)](#3-typescript-types-shared)
4. [API Client Setup](#4-api-client-setup)
5. [Auth Endpoints](#5-auth-endpoints)
6. [Connection Endpoints](#6-connection-endpoints)
7. [Conversation Endpoints](#7-conversation-endpoints)
8. [WebSocket — AI Analyst (Real-Time)](#8-websocket--ai-analyst-real-time)
9. [Error Handling](#9-error-handling)
10. [UI Integration Patterns](#10-ui-integration-patterns)

---

## 1. Overview

| Item | Value |
|------|-------|
| **Base URL** | `http://localhost:8080/api/v1` |
| **Auth method** | JWT Bearer token in `Authorization` header |
| **Content-Type** | `application/json` |
| **WebSocket endpoint** | `ws://localhost:8080/ws` (SockJS + STOMP) |
| **ID type** | `number` (Java `Long`) |
| **Timestamps** | ISO-8601 strings (Java `Instant`) — e.g. `"2025-01-15T10:30:00Z"` |

### Every response is wrapped in `ApiResponse<T>`

```json
{
  "success": true,
  "data": { ... },
  "message": "optional string",
  "error": null
}
```

On failure:

```json
{
  "success": false,
  "data": null,
  "message": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message",
    "details": {}          // optional, may be null
  }
}
```

---

## 2. Authentication Flow

```
┌─────────┐         ┌──────────┐
│   UI    │         │  Backend  │
└────┬────┘         └─────┬────┘
     │  POST /auth/login   │
     │  {username,password} │
     │ ───────────────────► │
     │                      │
     │  {accessToken,       │
     │   refreshToken,      │
     │   user, isNewUser}   │
     │ ◄─────────────────── │
     │                      │
     │  Store tokens in     │
     │  localStorage/memory │
     │                      │
     │  GET /connections    │
     │  Authorization:      │
     │  Bearer <token>      │
     │ ───────────────────► │
     │                      │
     │  When 401 received:  │
     │  POST /auth/refresh  │
     │  {refreshToken}      │
     │ ───────────────────► │
     │                      │
     │  {accessToken,       │
     │   refreshToken}      │
     │ ◄─────────────────── │
     │                      │
     │  Retry original req  │
     │  with new token      │
     │ ───────────────────► │
```

### Token Details

| Token | Lifetime | Storage Recommendation |
|-------|----------|----------------------|
| Access Token | 24 hours (86400000 ms) | Memory / sessionStorage |
| Refresh Token | 7 days (604800000 ms) | httpOnly cookie or localStorage |

### Public Endpoints (No Auth Required)

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/connections/test`
- `GET  /api/v1/shared/{shareId}`

All other endpoints require: `Authorization: Bearer <accessToken>`

---

## 3. TypeScript Types (Shared)

Copy these types into your UI project. They match the backend DTOs exactly.

```typescript
// ============================================================
// Generic API Response Wrapper
// ============================================================
interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  error: ErrorDetail | null;
}

interface ErrorDetail {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

// ============================================================
// Auth Types
// ============================================================
interface UserDto {
  id: number;
  username: string;
  email: string;
  createdAt: string;   // ISO-8601
  lastLogin: string | null;
}

interface LoginRequest {
  username: string;     // required
  password: string;     // required
}

interface RegisterRequest {
  username: string;     // required, 3–50 chars
  email: string;        // required, valid email
  password: string;     // required, 8–100 chars
}

interface AuthResponse {
  user: UserDto;
  accessToken: string;
  refreshToken: string;
  isNewUser: boolean;
}

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

interface RefreshTokenRequest {
  refreshToken: string; // required
}

interface UpdateUserRequest {
  username?: string;
  email?: string;
}

// ============================================================
// Connection Types
// ============================================================
interface ConnectionDto {
  id: number;
  name: string;
  type: string;         // "postgresql", "mysql", etc.
  typeName: string;     // "PostgreSQL", "MySQL", etc.
  typeIcon: string;     // icon identifier
  host: string;
  port: string;
  database: string;
  username: string;
  status: string;       // "connected", etc.
  createdAt: string;
  updatedAt: string;
  lastUsed: string | null;
}

interface ConnectionRequest {
  name: string;         // required
  type: string;         // required — "postgresql", "mysql", etc.
  typeName?: string;    // optional display name
  typeIcon?: string;    // optional icon identifier
  host: string;         // required
  port: string;         // required
  database: string;     // required
  username: string;     // required
  password: string;     // required
}

interface TestConnectionRequest {
  type: string;         // required
  host: string;         // required
  port: string;         // required
  database: string;     // required
  username: string;     // required
  password: string;     // required
}

interface TestConnectionResponse {
  connected: boolean;
  message: string;
  serverVersion: string | null;
  latency: number;      // milliseconds
}

// ============================================================
// Conversation Types
// ============================================================
interface ConversationDto {
  id: number;
  name: string;
  connectionIds: number[];
  messages: MessageDto[];
  messageCount: number;
  createdAt: string;
  updatedAt: string;
  isShared: boolean;
  shareId: string | null;
}

interface CreateConversationRequest {
  name: string;          // required
  connectionIds?: number[];
}

interface UpdateConversationRequest {
  name?: string;
  connectionIds?: number[];
}

interface MessageDto {
  id: number;
  content: string;
  sentByUser: boolean;
  createdAt: string;
  updatedAt: string;
}

interface AddMessageRequest {
  content: string;       // required
  sentByUser: boolean;
}

// ============================================================
// WebSocket Types (AI Analyst)
// ============================================================
interface AnalyzeRequest {
  userMessage: string;
  conversationId: number;
  connectionIds: number[];
}

interface AnalyzeResponse {
  success: boolean;
  conversationId: number;
  summary: string;
  queryResults: QueryResult[];
  suggestedVisualization: string | null;
  error: AnalyzeErrorInfo | null;
  timestamp: string;
}

interface QueryResult {
  connectionId: number;
  connectionName: string;
  query: string;
  data: Record<string, unknown>[];
  columns: string[];
  rowCount: number;
}

interface AnalyzeErrorInfo {
  code: string;
  message: string;
  suggestion: string | null;
}

interface AIActivityMessage {
  phase: string;
  status: string;
  message: string;
  conversationId: number | null;
  timestamp: string;
}
```

---

## 4. API Client Setup

Recommended pattern using `fetch` with automatic token refresh:

```typescript
const BASE_URL = "http://localhost:8080/api/v1";

let accessToken: string | null = null;
let refreshToken: string | null = null;

// Set tokens after login/register
function setTokens(access: string, refresh: string) {
  accessToken = access;
  refreshToken = refresh;
}

function clearTokens() {
  accessToken = null;
  refreshToken = null;
}

async function apiRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };

  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }

  let response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
  });

  // Auto-refresh on 401
  if (response.status === 401 && refreshToken) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      headers["Authorization"] = `Bearer ${accessToken}`;
      response = await fetch(`${BASE_URL}${path}`, {
        ...options,
        headers,
      });
    }
  }

  return response.json();
}

async function refreshAccessToken(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    const json: ApiResponse<TokenResponse> = await res.json();
    if (json.success && json.data) {
      setTokens(json.data.accessToken, json.data.refreshToken);
      return true;
    }
    clearTokens();
    return false;
  } catch {
    clearTokens();
    return false;
  }
}
```

---

## 5. Auth Endpoints

### 5.1 Register

```
POST /api/v1/auth/register
Auth: None
```

**Request Body:**

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "securePass123"
}
```

**Validation Rules:**

| Field | Rules |
|-------|-------|
| `username` | Required, 3–50 characters |
| `email` | Required, must be valid email format |
| `password` | Required, 8–100 characters |

**Success Response (201 Created):**

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "username": "johndoe",
      "email": "john@example.com",
      "createdAt": "2025-01-15T10:30:00Z",
      "lastLogin": null
    },
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "isNewUser": true
  }
}
```

**Error Response (409 Conflict — user exists):**

```json
{
  "success": false,
  "error": {
    "code": "CONFLICT",
    "message": "Username already exists"
  }
}
```

**UI Integration:**

```typescript
async function register(data: RegisterRequest): Promise<AuthResponse> {
  const res = await apiRequest<AuthResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify(data),
  });
  if (res.success && res.data) {
    setTokens(res.data.accessToken, res.data.refreshToken);
    return res.data;
  }
  throw new Error(res.error?.message ?? "Registration failed");
}
```

---

### 5.2 Login

```
POST /api/v1/auth/login
Auth: None
```

**Request Body:**

```json
{
  "username": "johndoe",
  "password": "securePass123"
}
```

**Validation Rules:**

| Field | Rules |
|-------|-------|
| `username` | Required |
| `password` | Required |

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "username": "johndoe",
      "email": "john@example.com",
      "createdAt": "2025-01-15T10:30:00Z",
      "lastLogin": "2025-01-16T08:00:00Z"
    },
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "isNewUser": false
  }
}
```

**Error Response (401 Unauthorized):**

```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid username or password"
  }
}
```

**UI Integration:**

```typescript
async function login(data: LoginRequest): Promise<AuthResponse> {
  const res = await apiRequest<AuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(data),
  });
  if (res.success && res.data) {
    setTokens(res.data.accessToken, res.data.refreshToken);
    return res.data;
  }
  throw new Error(res.error?.message ?? "Login failed");
}
```

---

### 5.3 Get Current User

```
GET /api/v1/auth/me
Auth: Bearer Token
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "username": "johndoe",
      "email": "john@example.com",
      "createdAt": "2025-01-15T10:30:00Z",
      "lastLogin": "2025-01-16T08:00:00Z"
    }
  }
}
```

**UI Integration:**

```typescript
async function getCurrentUser(): Promise<UserDto> {
  const res = await apiRequest<{ user: UserDto }>("/auth/me");
  if (res.success && res.data) return res.data.user;
  throw new Error(res.error?.message ?? "Failed to get user");
}
```

---

### 5.4 Update Current User

```
PUT /api/v1/auth/me
Auth: Bearer Token
```

**Request Body:**

```json
{
  "username": "newusername",
  "email": "newemail@example.com"
}
```

Both fields are optional — only send the fields you want to update.

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "username": "newusername",
      "email": "newemail@example.com",
      "createdAt": "2025-01-15T10:30:00Z",
      "lastLogin": "2025-01-16T08:00:00Z"
    }
  }
}
```

**UI Integration:**

```typescript
async function updateUser(data: UpdateUserRequest): Promise<UserDto> {
  const res = await apiRequest<{ user: UserDto }>("/auth/me", {
    method: "PUT",
    body: JSON.stringify(data),
  });
  if (res.success && res.data) return res.data.user;
  throw new Error(res.error?.message ?? "Failed to update user");
}
```

---

### 5.5 Refresh Token

```
POST /api/v1/auth/refresh
Auth: None
```

**Request Body:**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
  }
}
```

**Note:** The refresh token is rotated — store the new one and discard the old.

---

### 5.6 Logout

```
POST /api/v1/auth/logout
Auth: Bearer Token
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

**UI Integration:**

```typescript
async function logout(): Promise<void> {
  await apiRequest<void>("/auth/logout", { method: "POST" });
  clearTokens();
}
```

---

## 6. Connection Endpoints

### 6.1 List All Connections

```
GET /api/v1/connections
Auth: Bearer Token
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "connections": [
      {
        "id": 1,
        "name": "Production PostgreSQL",
        "type": "postgresql",
        "typeName": "PostgreSQL",
        "typeIcon": "postgresql",
        "host": "db.example.com",
        "port": "5432",
        "database": "analytics",
        "username": "readonly_user",
        "status": "connected",
        "createdAt": "2025-01-10T08:00:00Z",
        "updatedAt": "2025-01-15T10:30:00Z",
        "lastUsed": "2025-01-16T09:00:00Z"
      }
    ],
    "total": 1
  }
}
```

**Note:** The `password` field is never returned in responses.

**UI Integration:**

```typescript
async function getConnections(): Promise<{
  connections: ConnectionDto[];
  total: number;
}> {
  const res = await apiRequest<{
    connections: ConnectionDto[];
    total: number;
  }>("/connections");
  if (res.success && res.data) return res.data;
  throw new Error(res.error?.message ?? "Failed to get connections");
}
```

---

### 6.2 Test Connection

```
POST /api/v1/connections/test
Auth: None (public endpoint)
```

Use this before saving a connection, to verify the database is reachable.

**Request Body:**

```json
{
  "type": "postgresql",
  "host": "localhost",
  "port": "5432",
  "database": "mydb",
  "username": "dbuser",
  "password": "dbpass"
}
```

**All fields are required.**

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "connected": true,
    "message": "Connection successful",
    "serverVersion": "PostgreSQL 15.4",
    "latency": 45
  }
}
```

**Failed Connection Response (200 OK — check `connected` field):**

```json
{
  "success": true,
  "data": {
    "connected": false,
    "message": "Connection refused: host=localhost port=5432",
    "serverVersion": null,
    "latency": 0
  }
}
```

**UI Integration:**

```typescript
async function testConnection(
  data: TestConnectionRequest
): Promise<TestConnectionResponse> {
  const res = await apiRequest<TestConnectionResponse>("/connections/test", {
    method: "POST",
    body: JSON.stringify(data),
  });
  if (res.success && res.data) return res.data;
  throw new Error(res.error?.message ?? "Connection test failed");
}
```

---

### 6.3 Create Connection

```
POST /api/v1/connections
Auth: Bearer Token
```

**Request Body:**

```json
{
  "name": "Production PostgreSQL",
  "type": "postgresql",
  "typeName": "PostgreSQL",
  "typeIcon": "postgresql",
  "host": "db.example.com",
  "port": "5432",
  "database": "analytics",
  "username": "readonly_user",
  "password": "secure_password"
}
```

**Required Fields:** `name`, `type`, `host`, `port`, `database`, `username`, `password`
**Optional Fields:** `typeName`, `typeIcon`

**Success Response (201 Created):**

```json
{
  "success": true,
  "data": {
    "connection": {
      "id": 1,
      "name": "Production PostgreSQL",
      "type": "postgresql",
      "typeName": "PostgreSQL",
      "typeIcon": "postgresql",
      "host": "db.example.com",
      "port": "5432",
      "database": "analytics",
      "username": "readonly_user",
      "status": "connected",
      "createdAt": "2025-01-15T10:30:00Z",
      "updatedAt": "2025-01-15T10:30:00Z",
      "lastUsed": null
    }
  }
}
```

**UI Integration:**

```typescript
async function createConnection(
  data: ConnectionRequest
): Promise<ConnectionDto> {
  const res = await apiRequest<{ connection: ConnectionDto }>("/connections", {
    method: "POST",
    body: JSON.stringify(data),
  });
  if (res.success && res.data) return res.data.connection;
  throw new Error(res.error?.message ?? "Failed to create connection");
}
```

---

### 6.4 Update Connection

```
PUT /api/v1/connections/{connectionId}
Auth: Bearer Token
```

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `connectionId` | number | The connection ID |

**Request Body:** Same as Create Connection (all required fields must be sent).

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "connection": { /* ConnectionDto */ }
  }
}
```

**UI Integration:**

```typescript
async function updateConnection(
  connectionId: number,
  data: ConnectionRequest
): Promise<ConnectionDto> {
  const res = await apiRequest<{ connection: ConnectionDto }>(
    `/connections/${connectionId}`,
    { method: "PUT", body: JSON.stringify(data) }
  );
  if (res.success && res.data) return res.data.connection;
  throw new Error(res.error?.message ?? "Failed to update connection");
}
```

---

### 6.5 Delete Connection

```
DELETE /api/v1/connections/{connectionId}
Auth: Bearer Token
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "message": "Connection deleted successfully"
}
```

**UI Integration:**

```typescript
async function deleteConnection(connectionId: number): Promise<void> {
  const res = await apiRequest<void>(`/connections/${connectionId}`, {
    method: "DELETE",
  });
  if (!res.success) {
    throw new Error(res.error?.message ?? "Failed to delete connection");
  }
}
```

---

### 6.6 Update Last Used Timestamp

```
PATCH /api/v1/connections/{connectionId}/last-used
Auth: Bearer Token
```

Call this when the user selects/uses a connection, to track recent activity.

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "connection": { /* ConnectionDto with updated lastUsed */ }
  }
}
```

**UI Integration:**

```typescript
async function touchConnection(connectionId: number): Promise<ConnectionDto> {
  const res = await apiRequest<{ connection: ConnectionDto }>(
    `/connections/${connectionId}/last-used`,
    { method: "PATCH" }
  );
  if (res.success && res.data) return res.data.connection;
  throw new Error(res.error?.message ?? "Failed to update connection");
}
```

---

### 6.7 Get Connection Schema

```
GET /api/v1/connections/{connectionId}/schema
Auth: Bearer Token
```

Returns the database schema (tables, columns, types) for a connection. Useful for displaying schema browsers or providing context to the AI analyst.

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "schema": {
      "tables": [
        {
          "name": "customers",
          "columns": [
            { "name": "id", "type": "integer", "nullable": false },
            { "name": "name", "type": "varchar", "nullable": false },
            { "name": "email", "type": "varchar", "nullable": true },
            { "name": "created_at", "type": "timestamp", "nullable": false }
          ]
        },
        {
          "name": "orders",
          "columns": [
            { "name": "id", "type": "integer", "nullable": false },
            { "name": "customer_id", "type": "integer", "nullable": false },
            { "name": "total", "type": "numeric", "nullable": false },
            { "name": "status", "type": "varchar", "nullable": false }
          ]
        }
      ]
    }
  }
}
```

**UI Integration:**

```typescript
async function getConnectionSchema(
  connectionId: number
): Promise<Record<string, unknown>> {
  const res = await apiRequest<{ schema: Record<string, unknown> }>(
    `/connections/${connectionId}/schema`
  );
  if (res.success && res.data) return res.data.schema;
  throw new Error(res.error?.message ?? "Failed to get schema");
}
```

---

## 7. Conversation Endpoints

### 7.1 List All Conversations

```
GET /api/v1/conversations?limit=50&offset=0&sort=recent
Auth: Bearer Token
```

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `limit` | number | 50 | Max conversations to return |
| `offset` | number | 0 | Pagination offset |
| `sort` | string | "recent" | Sort order |

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "conversations": [
      {
        "id": 1,
        "name": "Sales Analysis",
        "connectionIds": [1, 2],
        "messages": [],
        "messageCount": 5,
        "createdAt": "2025-01-14T08:00:00Z",
        "updatedAt": "2025-01-16T09:30:00Z",
        "isShared": false,
        "shareId": null
      }
    ],
    "total": 1,
    "limit": 50,
    "offset": 0
  }
}
```

**UI Integration:**

```typescript
async function getConversations(
  limit = 50,
  offset = 0,
  sort = "recent"
): Promise<{
  conversations: ConversationDto[];
  total: number;
  limit: number;
  offset: number;
}> {
  const params = new URLSearchParams({
    limit: String(limit),
    offset: String(offset),
    sort,
  });
  const res = await apiRequest<{
    conversations: ConversationDto[];
    total: number;
    limit: number;
    offset: number;
  }>(`/conversations?${params}`);
  if (res.success && res.data) return res.data;
  throw new Error(res.error?.message ?? "Failed to get conversations");
}
```

---

### 7.2 Create Conversation

```
POST /api/v1/conversations
Auth: Bearer Token
```

**Request Body:**

```json
{
  "name": "Sales Analysis",
  "connectionIds": [1, 2]
}
```

**Required Fields:** `name`
**Optional Fields:** `connectionIds` (defaults to empty list)

**Success Response (201 Created):**

```json
{
  "success": true,
  "data": {
    "conversation": {
      "id": 1,
      "name": "Sales Analysis",
      "connectionIds": [1, 2],
      "messages": [],
      "messageCount": 0,
      "createdAt": "2025-01-16T10:00:00Z",
      "updatedAt": "2025-01-16T10:00:00Z",
      "isShared": false,
      "shareId": null
    }
  }
}
```

**UI Integration:**

```typescript
async function createConversation(
  data: CreateConversationRequest
): Promise<ConversationDto> {
  const res = await apiRequest<{ conversation: ConversationDto }>(
    "/conversations",
    { method: "POST", body: JSON.stringify(data) }
  );
  if (res.success && res.data) return res.data.conversation;
  throw new Error(res.error?.message ?? "Failed to create conversation");
}
```

---

### 7.3 Get Conversation

```
GET /api/v1/conversations/{conversationId}
Auth: Bearer Token
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "conversation": {
      "id": 1,
      "name": "Sales Analysis",
      "connectionIds": [1, 2],
      "messages": [
        {
          "id": 1,
          "content": "Show me total revenue by month",
          "sentByUser": true,
          "createdAt": "2025-01-16T10:05:00Z",
          "updatedAt": "2025-01-16T10:05:00Z"
        },
        {
          "id": 2,
          "content": "{\"summary\": \"Here are the monthly revenue figures...\", ...}",
          "sentByUser": false,
          "createdAt": "2025-01-16T10:05:03Z",
          "updatedAt": "2025-01-16T10:05:03Z"
        }
      ],
      "messageCount": 2,
      "createdAt": "2025-01-16T10:00:00Z",
      "updatedAt": "2025-01-16T10:05:03Z",
      "isShared": false,
      "shareId": null
    }
  }
}
```

**Note:** AI messages (`sentByUser: false`) may contain JSON-encoded content with query results, summaries, and visualization suggestions. Parse the `content` field as JSON when `sentByUser` is `false`.

---

### 7.4 Update Conversation

```
PUT /api/v1/conversations/{conversationId}
Auth: Bearer Token
```

**Request Body:**

```json
{
  "name": "Updated Name",
  "connectionIds": [1, 3]
}
```

Both fields are optional.

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "conversation": { /* ConversationDto */ }
  }
}
```

---

### 7.5 Add Message to Conversation

```
POST /api/v1/conversations/{conversationId}/messages
Auth: Bearer Token
```

**Request Body:**

```json
{
  "content": "Show me total sales by region",
  "sentByUser": true
}
```

**Required Fields:** `content`

**Success Response (201 Created):**

```json
{
  "success": true,
  "data": {
    "message": {
      "id": 3,
      "content": "Show me total sales by region",
      "sentByUser": true,
      "createdAt": "2025-01-16T10:10:00Z",
      "updatedAt": "2025-01-16T10:10:00Z"
    }
  }
}
```

---

### 7.6 Get Messages

```
GET /api/v1/conversations/{conversationId}/messages
Auth: Bearer Token
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "messages": [
      {
        "id": 1,
        "content": "Show me total revenue by month",
        "sentByUser": true,
        "createdAt": "2025-01-16T10:05:00Z",
        "updatedAt": "2025-01-16T10:05:00Z"
      },
      {
        "id": 2,
        "content": "{\"summary\": \"Monthly revenue...\", \"queryResults\": [...]}",
        "sentByUser": false,
        "createdAt": "2025-01-16T10:05:03Z",
        "updatedAt": "2025-01-16T10:05:03Z"
      }
    ],
    "total": 2
  }
}
```

---

### 7.7 Delete Conversation

```
DELETE /api/v1/conversations/{conversationId}
Auth: Bearer Token
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "message": "Conversation deleted successfully"
}
```

---

### 7.8 Share Conversation

```
POST /api/v1/conversations/{conversationId}/share
Auth: Bearer Token
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "shareId": "abc123xyz789",
    "conversation": { /* ConversationDto with isShared: true */ }
  }
}
```

---

### 7.9 Unshare Conversation

```
DELETE /api/v1/conversations/{conversationId}/share
Auth: Bearer Token
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "message": "Conversation unshared successfully"
}
```

---

### 7.10 Get Shared Conversation (Public)

```
GET /api/v1/shared/{shareId}
Auth: None (public endpoint)
```

**Success Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "conversation": { /* ConversationDto */ }
  }
}
```

---

## 8. WebSocket — AI Analyst (Real-Time)

The AI Analyst uses WebSocket (STOMP over SockJS) for real-time natural-language query analysis. This is the core feature — users type a question in natural language, and the AI generates SQL, executes it against connected databases, and returns results with visualization suggestions.

### 8.1 Connection Setup

```typescript
import SockJS from "sockjs-client";
import { Client, IMessage } from "@stomp/stompjs";

const WS_URL = "http://localhost:8080/ws";

function connectWebSocket(token: string): Client {
  const client = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });

  client.onConnect = () => {
    console.log("WebSocket connected");

    // Subscribe to AI analysis results
    client.subscribe("/user/queue/ai/response", (message: IMessage) => {
      const response: AnalyzeResponse = JSON.parse(message.body);
      handleAnalyzeResponse(response);
    });

    // Subscribe to real-time activity/progress updates
    client.subscribe("/user/queue/ai/activity", (message: IMessage) => {
      const activity: AIActivityMessage = JSON.parse(message.body);
      handleActivityUpdate(activity);
    });

    // Subscribe to errors
    client.subscribe("/user/queue/ai/error", (message: IMessage) => {
      const error: AnalyzeResponse = JSON.parse(message.body);
      handleAnalyzeError(error);
    });

    // Subscribe to ping responses
    client.subscribe("/user/queue/ai/pong", (message: IMessage) => {
      const pong: AIActivityMessage = JSON.parse(message.body);
      console.log("Pong:", pong);
    });
  };

  client.onStompError = (frame) => {
    console.error("STOMP error:", frame.headers["message"]);
  };

  client.activate();
  return client;
}
```

### 8.2 Send Analysis Request

```typescript
function sendAnalyzeRequest(
  client: Client,
  request: AnalyzeRequest
): void {
  client.publish({
    destination: "/app/ai/analyze",
    body: JSON.stringify(request),
  });
}

// Example usage:
sendAnalyzeRequest(client, {
  userMessage: "Show me total revenue by month for 2024",
  conversationId: 1,
  connectionIds: [1, 2],
});
```

### 8.3 Ping (Health Check)

```typescript
function sendPing(client: Client): void {
  client.publish({
    destination: "/app/ai/ping",
    body: "",
  });
}
```

### 8.4 Activity Phases

The backend sends real-time progress updates via the `/user/queue/ai/activity` channel during analysis. These are the phases you can display in the UI:

| Phase | Status | Description |
|-------|--------|-------------|
| `analyzing` | `started` | AI is parsing the user's question |
| `generating_query` | `in_progress` | AI is generating SQL queries |
| `executing_query` | `in_progress` | Executing queries against databases |
| `processing_results` | `in_progress` | Processing and formatting results |
| `complete` | `completed` | Analysis finished successfully |
| `error` | `failed` | An error occurred |

**UI Pattern — Show Progress:**

```typescript
function handleActivityUpdate(activity: AIActivityMessage): void {
  // Update a progress indicator in the UI
  switch (activity.phase) {
    case "analyzing":
      showStatus("Analyzing your question...");
      break;
    case "generating_query":
      showStatus("Generating SQL query...");
      break;
    case "executing_query":
      showStatus("Running query against database...");
      break;
    case "processing_results":
      showStatus("Processing results...");
      break;
    case "complete":
      hideStatus();
      break;
    case "error":
      showError(activity.message);
      break;
  }
}
```

### 8.5 Handle Analysis Response

```typescript
function handleAnalyzeResponse(response: AnalyzeResponse): void {
  if (response.success) {
    // Display summary text
    displaySummary(response.summary);

    // Display query results as tables/charts
    for (const result of response.queryResults) {
      displayQueryResult({
        connectionName: result.connectionName,
        sql: result.query,
        columns: result.columns,
        rows: result.data,
        rowCount: result.rowCount,
      });
    }

    // Apply suggested visualization
    if (response.suggestedVisualization) {
      applyVisualization(response.suggestedVisualization);
    }
  } else if (response.error) {
    displayError(response.error.message, response.error.suggestion);
  }
}
```

### 8.6 Full WebSocket Integration Example

```typescript
class AIAnalystService {
  private client: Client | null = null;
  private onResponse: (res: AnalyzeResponse) => void;
  private onActivity: (msg: AIActivityMessage) => void;
  private onError: (res: AnalyzeResponse) => void;

  constructor(handlers: {
    onResponse: (res: AnalyzeResponse) => void;
    onActivity: (msg: AIActivityMessage) => void;
    onError: (res: AnalyzeResponse) => void;
  }) {
    this.onResponse = handlers.onResponse;
    this.onActivity = handlers.onActivity;
    this.onError = handlers.onError;
  }

  connect(token: string): void {
    this.client = new Client({
      webSocketFactory: () => new SockJS("http://localhost:8080/ws"),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
    });

    this.client.onConnect = () => {
      this.client!.subscribe("/user/queue/ai/response", (msg) =>
        this.onResponse(JSON.parse(msg.body))
      );
      this.client!.subscribe("/user/queue/ai/activity", (msg) =>
        this.onActivity(JSON.parse(msg.body))
      );
      this.client!.subscribe("/user/queue/ai/error", (msg) =>
        this.onError(JSON.parse(msg.body))
      );
    };

    this.client.activate();
  }

  analyze(request: AnalyzeRequest): void {
    this.client?.publish({
      destination: "/app/ai/analyze",
      body: JSON.stringify(request),
    });
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
  }
}
```

---

## 9. Error Handling

### Error Codes Reference

| Code | HTTP Status | Description | UI Action |
|------|-------------|-------------|-----------|
| `VALIDATION_ERROR` | 400 | Invalid request data | Show field-level errors |
| `INVALID_CREDENTIALS` | 401 | Wrong username/password | Show login error |
| `UNAUTHORIZED` | 401 | Missing or expired token | Redirect to login / refresh token |
| `FORBIDDEN` | 403 | Insufficient permissions | Show access denied message |
| `NOT_FOUND` | 404 | Resource doesn't exist | Show not-found page or message |
| `CONFLICT` | 409 | Resource already exists | Show duplicate error |
| `CONNECTION_FAILED` | 400 | Database connection failed | Show connection error in form |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests | Show rate limit message, retry later |
| `INTERNAL_ERROR` | 500 | Unexpected server error | Show generic error, log details |

### Error Handling Pattern

```typescript
async function safeApiCall<T>(
  fn: () => Promise<T>,
  options?: {
    onUnauthorized?: () => void;
    onNotFound?: () => void;
    onValidationError?: (details: Record<string, unknown>) => void;
  }
): Promise<T | null> {
  try {
    return await fn();
  } catch (error: unknown) {
    const message =
      error instanceof Error ? error.message : "Unknown error";

    // Check for specific error patterns
    if (message.includes("UNAUTHORIZED") || message.includes("401")) {
      options?.onUnauthorized?.();
    } else if (message.includes("NOT_FOUND")) {
      options?.onNotFound?.();
    } else {
      // Show generic toast/notification
      showErrorToast(message);
    }

    return null;
  }
}
```

---

## 10. UI Integration Patterns

### 10.1 Recommended UI Pages and Their API Calls

| Page | API Calls Used |
|------|---------------|
| **Login Page** | `POST /auth/login` |
| **Register Page** | `POST /auth/register` |
| **Dashboard / Home** | `GET /conversations`, `GET /connections` |
| **Connection Manager** | `GET /connections`, `POST /connections/test`, `POST /connections`, `PUT /connections/{id}`, `DELETE /connections/{id}` |
| **Conversation View** | `GET /conversations/{id}`, `GET /conversations/{id}/messages`, `POST /conversations/{id}/messages`, WebSocket AI analyze |
| **New Conversation** | `POST /conversations`, `GET /connections` (to select connections) |
| **Settings / Profile** | `GET /auth/me`, `PUT /auth/me` |
| **Shared View** | `GET /shared/{shareId}` |

### 10.2 Typical User Flow

```
1. User lands on Login/Register page
2. After auth → redirect to Dashboard
3. Dashboard shows:
   - Recent conversations (GET /conversations?sort=recent&limit=10)
   - Connected databases (GET /connections)
4. User adds a database connection:
   - Fill form → Test (POST /connections/test) → Save (POST /connections)
5. User starts a conversation:
   - Create (POST /conversations) with selected connectionIds
   - Connect WebSocket
   - Type question → send via WebSocket /app/ai/analyze
   - Show activity phases (progress)
   - Display results (tables, charts) from AnalyzeResponse
   - Store messages via POST /conversations/{id}/messages
6. User can share conversations:
   - POST /conversations/{id}/share → get shareId
   - Share URL: /shared/{shareId}
```

### 10.3 State Management Recommendations

```typescript
// Suggested global state shape
interface AppState {
  auth: {
    user: UserDto | null;
    accessToken: string | null;
    refreshToken: string | null;
    isAuthenticated: boolean;
  };
  connections: {
    items: ConnectionDto[];
    loading: boolean;
    error: string | null;
  };
  conversations: {
    items: ConversationDto[];
    activeConversation: ConversationDto | null;
    messages: MessageDto[];
    loading: boolean;
    total: number;
  };
  ai: {
    isAnalyzing: boolean;
    currentPhase: string | null;
    phaseMessage: string | null;
    lastResponse: AnalyzeResponse | null;
    wsConnected: boolean;
  };
}
```

### 10.4 NPM Dependencies for WebSocket

```json
{
  "dependencies": {
    "sockjs-client": "^1.6.1",
    "@stomp/stompjs": "^7.0.0"
  },
  "devDependencies": {
    "@types/sockjs-client": "^1.5.4"
  }
}
```
