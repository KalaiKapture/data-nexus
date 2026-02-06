# DataNexus API - cURL Commands

**Base URL:** `http://localhost:8080/api/v1`

> Replace `YOUR_TOKEN` with the JWT access token obtained from login/register.

---

## 1. Authentication (`/api/v1/auth`)

### Register

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john@example.com",
    "password": "securePass123"
  }'
```

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "password": "securePass123"
  }'
```

### Refresh Token

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

### Get Current User

```bash
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Logout

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 2. Connections (`/api/v1/connections`)

### List All Connections

```bash
curl -X GET http://localhost:8080/api/v1/connections \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Test Connection (No Auth Required)

```bash
curl -X POST http://localhost:8080/api/v1/connections/test \
  -H "Content-Type: application/json" \
  -d '{
    "type": "postgresql",
    "host": "localhost",
    "port": "5432",
    "database": "mydb",
    "username": "dbuser",
    "password": "dbpass"
  }'
```

### Create Connection

```bash
curl -X POST http://localhost:8080/api/v1/connections \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Database",
    "type": "postgresql",
    "typeName": "PostgreSQL",
    "typeIcon": "postgresql",
    "host": "localhost",
    "port": "5432",
    "database": "mydb",
    "username": "dbuser",
    "password": "dbpass"
  }'
```

### Update Connection

```bash
curl -X PUT http://localhost:8080/api/v1/connections/{connectionId} \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Database",
    "type": "postgresql",
    "typeName": "PostgreSQL",
    "typeIcon": "postgresql",
    "host": "localhost",
    "port": "5432",
    "database": "mydb",
    "username": "dbuser",
    "password": "newpass"
  }'
```

### Delete Connection

```bash
curl -X DELETE http://localhost:8080/api/v1/connections/{connectionId} \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Update Last Used Timestamp

```bash
curl -X PATCH http://localhost:8080/api/v1/connections/{connectionId}/last-used \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Get Connection Schema

```bash
curl -X GET http://localhost:8080/api/v1/connections/{connectionId}/schema \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 3. Conversations (`/api/v1/conversations`)

### List All Conversations

```bash
curl -X GET "http://localhost:8080/api/v1/conversations?limit=50&offset=0&sort=recent" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Create Conversation

```bash
curl -X POST http://localhost:8080/api/v1/conversations \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Conversation",
    "connectionIds": ["CONNECTION_ID_1"]
  }'
```

### Get Conversation

```bash
curl -X GET http://localhost:8080/api/v1/conversations/{conversationId} \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Update Conversation

```bash
curl -X PUT http://localhost:8080/api/v1/conversations/{conversationId} \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Renamed Conversation",
    "connectionIds": ["CONNECTION_ID_1", "CONNECTION_ID_2"]
  }'
```

### Add Message to Conversation

```bash
curl -X POST http://localhost:8080/api/v1/conversations/{conversationId}/messages \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "content": "Show me total sales by region"
  }'
```

### Delete Conversation

```bash
curl -X DELETE http://localhost:8080/api/v1/conversations/{conversationId} \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Share Conversation

```bash
curl -X POST http://localhost:8080/api/v1/conversations/{conversationId}/share \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Unshare Conversation

```bash
curl -X DELETE http://localhost:8080/api/v1/conversations/{conversationId}/share \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 4. Query (`/api/v1/query`)

### Execute Query

```bash
curl -X POST http://localhost:8080/api/v1/query/execute \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Show me total revenue by month",
    "connectionIds": ["CONNECTION_ID_1"],
    "conversationId": "CONVERSATION_ID"
  }'
```

### Get Query Suggestions

```bash
curl -X POST http://localhost:8080/api/v1/query/suggestions \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "connectionIds": ["CONNECTION_ID_1"],
    "context": "sales data"
  }'
```

---

## 5. Modules (`/api/v1/modules`)

### List All Modules

```bash
curl -X GET http://localhost:8080/api/v1/modules \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Create Module

```bash
curl -X POST http://localhost:8080/api/v1/modules \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Monthly Revenue Dashboard",
    "query": "SELECT month, SUM(revenue) FROM sales GROUP BY month",
    "data": {},
    "connectionId": "CONNECTION_ID"
  }'
```

### Get Shared Module (No Auth Required)

```bash
curl -X GET http://localhost:8080/api/v1/modules/shared/{shareId}
```

### Increment Module View Count (No Auth Required)

```bash
curl -X POST http://localhost:8080/api/v1/modules/shared/{shareId}/view
```

### Delete Module

```bash
curl -X DELETE http://localhost:8080/api/v1/modules/{moduleId} \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 6. Shared Conversations (`/api/v1/shared`)

### Get Shared Conversation (No Auth Required)

```bash
curl -X GET http://localhost:8080/api/v1/shared/{shareId}
```

---

## Quick Start Flow

```bash
# 1. Register a new user
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe","email":"john@example.com","password":"securePass123"}' | jq .

# 2. Login and extract token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe","password":"securePass123"}' | jq -r '.data.accessToken')

# 3. Test a database connection
curl -s -X POST http://localhost:8080/api/v1/connections/test \
  -H "Content-Type: application/json" \
  -d '{"type":"postgresql","host":"localhost","port":"5432","database":"mydb","username":"dbuser","password":"dbpass"}' | jq .

# 4. Create a connection
CONN_ID=$(curl -s -X POST http://localhost:8080/api/v1/connections \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"My DB","type":"postgresql","host":"localhost","port":"5432","database":"mydb","username":"dbuser","password":"dbpass"}' | jq -r '.data.connection.id')

# 5. Create a conversation
CONV_ID=$(curl -s -X POST http://localhost:8080/api/v1/conversations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Analysis\",\"connectionIds\":[\"$CONN_ID\"]}" | jq -r '.data.conversation.id')

# 6. Execute a query
curl -s -X POST http://localhost:8080/api/v1/query/execute \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"query\":\"Show me all tables\",\"connectionIds\":[\"$CONN_ID\"],\"conversationId\":\"$CONV_ID\"}" | jq .
```
