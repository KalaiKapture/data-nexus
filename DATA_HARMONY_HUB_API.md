# Data Harmony Hub - API Documentation

This document provides comprehensive API specifications for backend development.

**Base URL:** `https://api.dataharmonyhub.com/v1`

**Authentication:** Bearer token in Authorization header
```
Authorization: Bearer <access_token>
```

---

## Table of Contents

1. [Authentication APIs](#1-authentication-apis)
2. [Database Connection APIs](#2-database-connection-apis)
3. [Conversation APIs](#3-conversation-apis)
4. [Query Execution APIs](#4-query-execution-apis)
5. [Module/Shareable APIs](#5-moduleshareable-apis)
6. [Error Handling](#6-error-handling)

---

## 1. Authentication APIs

### 1.1 Login User

**Endpoint:** `POST /auth/login`

**Request:**
```json
{
  "username": "john_doe",
  "password": "securePassword123"
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "user": {
      "id": "user_001",
      "username": "john_doe",
      "email": "john@example.com",
      "createdAt": "2024-01-15T10:30:00.000Z",
      "lastLogin": "2024-06-20T14:25:00.000Z"
    },
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g...",
    "isNewUser": false
  }
}
```

### 1.2 Register User

**Endpoint:** `POST /auth/register`

**Request:**
```json
{
  "username": "new_user",
  "email": "newuser@example.com",
  "password": "securePassword123"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "user": {
      "id": "user_123",
      "username": "new_user",
      "email": "newuser@example.com",
      "createdAt": "2024-06-20T14:25:00.000Z"
    },
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g...",
    "isNewUser": true
  }
}
```

### 1.3 Get User Details

**Endpoint:** `GET /auth/me`

**Response (200):**
```json
{
  "success": true,
  "data": {
    "user": {
      "id": "user_001",
      "username": "john_doe",
      "email": "john@example.com",
      "createdAt": "2024-01-15T10:30:00.000Z",
      "lastLogin": "2024-06-20T14:25:00.000Z",
      "preferences": {
        "theme": "dark",
        "defaultConnectionId": "conn_001"
      }
    }
  }
}
```

### 1.4 Refresh Token

**Endpoint:** `POST /auth/refresh`

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g..."
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "bmV3IHJlZnJlc2g..."
  }
}
```

### 1.5 Logout

**Endpoint:** `POST /auth/logout`

**Response (200):**
```json
{
  "success": true,
  "message": "Successfully logged out"
}
```

---

## 2. Database Connection APIs

### 2.1 Get User Connections

**Endpoint:** `GET /connections`

**Response (200):**
```json
{
  "success": true,
  "data": {
    "connections": [
      {
        "id": "conn_001",
        "name": "Production PostgreSQL",
        "type": "postgresql",
        "typeName": "PostgreSQL",
        "typeIcon": "elephant",
        "host": "db.example.com",
        "port": "5432",
        "database": "production_db",
        "username": "db_user",
        "status": "connected",
        "createdAt": "2024-01-20T08:00:00.000Z",
        "lastUsed": "2024-06-20T14:00:00.000Z"
      }
    ],
    "total": 1
  }
}
```

### 2.2 Test Database Connection

**Endpoint:** `POST /connections/test`

**Request:**
```json
{
  "type": "postgresql",
  "host": "db.example.com",
  "port": "5432",
  "database": "my_database",
  "username": "db_user",
  "password": "db_password"
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "connected": true,
    "message": "Successfully connected to PostgreSQL database",
    "serverVersion": "PostgreSQL 15.3",
    "latency": 45
  }
}
```

### 2.3 Create Connection

**Endpoint:** `POST /connections`

**Request:**
```json
{
  "name": "My Production DB",
  "type": "postgresql",
  "typeName": "PostgreSQL",
  "typeIcon": "elephant",
  "host": "db.example.com",
  "port": "5432",
  "database": "production_db",
  "username": "db_user",
  "password": "db_password"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "connection": { ... }
  }
}
```

### 2.4 Update Connection

**Endpoint:** `PUT /connections/:connectionId`

### 2.5 Delete Connection

**Endpoint:** `DELETE /connections/:connectionId`

### 2.6 Update Connection Last Used

**Endpoint:** `PATCH /connections/:connectionId/last-used`

---

## 3. Conversation APIs

### 3.1 Get User Conversations

**Endpoint:** `GET /conversations`

**Query Parameters:** `limit`, `offset`, `sort`

### 3.2 Create Conversation

**Endpoint:** `POST /conversations`

### 3.3 Get Conversation Details

**Endpoint:** `GET /conversations/:conversationId`

### 3.4 Update Conversation

**Endpoint:** `PUT /conversations/:conversationId`

### 3.5 Add Message to Conversation

**Endpoint:** `POST /conversations/:conversationId/messages`

### 3.6 Delete Conversation

**Endpoint:** `DELETE /conversations/:conversationId`

### 3.7 Share Conversation

**Endpoint:** `POST /conversations/:conversationId/share`

### 3.8 Get Shared Conversation (Public)

**Endpoint:** `GET /shared/:shareId`

### 3.9 Unshare Conversation

**Endpoint:** `DELETE /conversations/:conversationId/share`

---

## 4. Query Execution APIs

### 4.1 Execute Query

**Endpoint:** `POST /query/execute`

### 4.2 Get Query Suggestions

**Endpoint:** `POST /query/suggestions`

### 4.3 Get Database Schema

**Endpoint:** `GET /connections/:connectionId/schema`

---

## 5. Module/Shareable APIs

### 5.1 Get User Modules

**Endpoint:** `GET /modules`

### 5.2 Create Module

**Endpoint:** `POST /modules`

### 5.3 Get Module by Share ID (Public)

**Endpoint:** `GET /modules/shared/:shareId`

### 5.4 Increment Module Views

**Endpoint:** `POST /modules/shared/:shareId/view`

### 5.5 Delete Module

**Endpoint:** `DELETE /modules/:moduleId`

---

## 6. Error Handling

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 400 | BAD_REQUEST | Invalid request parameters |
| 400 | VALIDATION_ERROR | Request validation failed |
| 400 | CONNECTION_FAILED | Database connection test failed |
| 401 | UNAUTHORIZED | Missing or invalid authentication |
| 401 | TOKEN_EXPIRED | Access token has expired |
| 401 | INVALID_CREDENTIALS | Wrong username or password |
| 403 | FORBIDDEN | User doesn't have permission |
| 404 | NOT_FOUND | Resource not found |
| 409 | CONFLICT | Resource already exists |
| 422 | QUERY_EXECUTION_ERROR | Failed to execute query |
| 429 | RATE_LIMITED | Too many requests |
| 500 | INTERNAL_ERROR | Internal server error |

---

## Rate Limiting

- Standard: 100 requests/minute
- Query execution: 30 requests/minute
- Public endpoints: 60 requests/minute

## Timestamps

All timestamps in ISO 8601 format (UTC): `2024-06-20T14:30:00.000Z`
