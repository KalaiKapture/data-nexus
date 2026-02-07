# DataNexus WebSocket Integration Guide for React UI

> **Complete reference** for integrating the DataNexus STOMP-over-WebSocket backend into a React frontend.
> Covers: connection setup, authentication, sending messages, subscribing to real-time activity updates, handling responses/errors, and common pitfalls that cause "activity not reflecting in UI."

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Backend WebSocket Topology](#2-backend-websocket-topology)
3. [Required NPM Packages](#3-required-npm-packages)
4. [Step 1 — Connect to the WebSocket (STOMP + SockJS)](#4-step-1--connect-to-the-websocket-stomp--sockjs)
5. [Step 2 — Authentication Flow](#5-step-2--authentication-flow)
6. [Step 3 — Subscribe to Channels](#6-step-3--subscribe-to-channels)
7. [Step 4 — Send an Analyze Request](#7-step-4--send-an-analyze-request)
8. [Step 5 — Handle Activity Updates (Real-Time)](#8-step-5--handle-activity-updates-real-time)
9. [Step 6 — Handle Final Response](#9-step-6--handle-final-response)
10. [Step 7 — Handle Errors](#10-step-7--handle-errors)
11. [Step 8 — Ping / Connection Health Check](#11-step-8--ping--connection-health-check)
12. [Complete React Hook — `useWebSocket`](#12-complete-react-hook--usewebsocket)
13. [Complete React Context Provider](#13-complete-react-context-provider)
14. [Example UI Component Usage](#14-example-ui-component-usage)
15. [Activity Phases Reference](#15-activity-phases-reference)
16. [Message Payload Schemas (TypeScript)](#16-message-payload-schemas-typescript)
17. [Common Issues & Debugging](#17-common-issues--debugging)
18. [Sequence Diagram](#18-sequence-diagram)

---

## 1. Architecture Overview

```
┌─────────────────────┐         STOMP over WebSocket          ┌──────────────────────────┐
│                     │  ──────────────────────────────────▶   │  Spring Boot Backend     │
│   React Frontend    │         /ws  (SockJS fallback)         │                          │
│                     │  ◀──────────────────────────────────   │  Port: 8080              │
│  @stomp/stompjs     │                                        │                          │
│  sockjs-client      │   Auth: JWT token in STOMP CONNECT     │  WebSocketConfig.java    │
│                     │         header "Authorization"          │  AIAnalystWSController   │
└─────────────────────┘                                        │  AIAnalystOrchestrator   │
                                                               └──────────────────────────┘
```

**Protocol stack:** The backend uses **STOMP** (Simple Text Oriented Messaging Protocol) **over WebSocket** with an optional **SockJS** fallback. This is NOT raw WebSocket — you MUST use a STOMP client library.

---

## 2. Backend WebSocket Topology

### Endpoint

| Property | Value |
|---|---|
| WebSocket URL | `ws://localhost:8080/ws` |
| SockJS fallback URL | `http://localhost:8080/ws` |
| Application destination prefix | `/app` |
| Broker prefixes | `/topic`, `/queue` |
| User destination prefix | `/user` |

### Destinations (Channels)

#### Client → Server (Send)

| STOMP Destination | Purpose | Payload |
|---|---|---|
| `/app/ai/analyze` | Submit an AI analysis request | `AnalyzeRequest` JSON |
| `/app/ai/ping` | Health check / connectivity test | *(empty body)* |

#### Server → Client (Subscribe)

> **CRITICAL:** All user-specific channels are prefixed with `/user/` by Spring. You subscribe to `/user/queue/...` — Spring automatically resolves the user from the authenticated STOMP session. You do **NOT** put the user ID in the subscription path.

| Subscribe To | Purpose | Payload |
|---|---|---|
| `/user/queue/ai/activity` | **Real-time activity/phase updates** | `AIActivityMessage` JSON |
| `/user/queue/ai/response` | **Final analysis response** | `AnalyzeResponse` JSON |
| `/user/queue/ai/error` | **Error responses** | `AnalyzeResponse` JSON (with `success: false`) |
| `/user/queue/ai/pong` | Ping response | `AIActivityMessage` JSON |

---

## 3. Required NPM Packages

```bash
npm install @stomp/stompjs sockjs-client
npm install -D @types/sockjs-client   # if using TypeScript
```

**Why these two?**
- `@stomp/stompjs` — Modern STOMP client (v6+/v7+) with reconnect, heartbeat, and error handling built-in.
- `sockjs-client` — Provides fallback transports (XHR streaming, long-polling) when native WebSocket is blocked by proxies/firewalls.

> **Do NOT use** the deprecated `stompjs` package (no `@stomp/` prefix) — it lacks reconnect and modern features.

---

## 4. Step 1 — Connect to the WebSocket (STOMP + SockJS)

```typescript
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

const BACKEND_URL = "http://localhost:8080/ws"; // SockJS endpoint

function createStompClient(accessToken: string): Client {
  const client = new Client({
    // Use SockJS factory for fallback support
    webSocketFactory: () => new SockJS(BACKEND_URL),

    // Send JWT token in STOMP CONNECT frame headers
    connectHeaders: {
      Authorization: `Bearer ${accessToken}`,
    },

    // Heartbeat: client sends every 10s, expects server every 10s
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    // Auto-reconnect with 5-second delay
    reconnectDelay: 5000,

    // Debug logging (disable in production)
    debug: (msg) => {
      console.log("[STOMP]", msg);
    },
  });

  return client;
}
```

### Key Points

1. **`webSocketFactory`** — Use `SockJS` constructor, NOT `new WebSocket(...)`. The backend registers the `/ws` endpoint with `.withSockJS()` AND without it. SockJS handles both.
2. **`connectHeaders.Authorization`** — The backend `WebSocketAuthInterceptor` extracts the JWT from either the `Authorization: Bearer <token>` header or a plain `token` header on the STOMP CONNECT frame.
3. **`reconnectDelay`** — Automatic reconnect. When connection drops, stompjs retries every 5 seconds. You MUST re-subscribe after reconnect (covered below).

---

## 5. Step 2 — Authentication Flow

### How It Works on the Backend

```
Client sends STOMP CONNECT frame
  └── Header: Authorization: Bearer <jwt>
        │
        ▼
WebSocketAuthInterceptor.preSend()
  ├── Extracts token from "Authorization" or "token" header
  ├── Validates JWT via JwtTokenProvider.validateToken()
  ├── Loads User entity from DB via UserRepository.findById()
  ├── Creates UsernamePasswordAuthenticationToken(user, null, [])
  └── Sets auth on StompHeaderAccessor → accessor.setUser(auth)
        │
        ▼
STOMP session is now authenticated
  └── principal.getName() returns user.getId().toString()
  └── /user/ destinations resolve to this user
```

### Getting the Token

```typescript
// Login via REST API first
const loginResponse = await fetch("http://localhost:8080/api/v1/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email: "user@example.com", password: "password" }),
});

const data = await loginResponse.json();
const accessToken = data.data.accessToken;
// data.data.refreshToken — store for token refresh
// data.data.user — user info { id, username, email }
```

### What Happens If Auth Fails

- Backend throws `IllegalArgumentException("Invalid or missing authentication token")`
- The STOMP connection is **rejected** — you'll get an `onStompError` callback
- The client will attempt to reconnect (due to `reconnectDelay`), which will keep failing until you provide a valid token
- **Fix:** Update `connectHeaders` with a fresh token before reconnecting

---

## 6. Step 3 — Subscribe to Channels

**Subscribe INSIDE the `onConnect` callback** — subscriptions made before the connection is established are lost.

```typescript
client.onConnect = (frame) => {
  console.log("Connected to WebSocket:", frame);

  // 1. Subscribe to real-time ACTIVITY updates
  client.subscribe("/user/queue/ai/activity", (message) => {
    const activity: AIActivityMessage = JSON.parse(message.body);
    console.log("Activity:", activity);
    // Update your UI state here
  });

  // 2. Subscribe to FINAL RESPONSE
  client.subscribe("/user/queue/ai/response", (message) => {
    const response: AnalyzeResponse = JSON.parse(message.body);
    console.log("Response:", response);
    // Display results in your UI
  });

  // 3. Subscribe to ERRORS
  client.subscribe("/user/queue/ai/error", (message) => {
    const error: AnalyzeResponse = JSON.parse(message.body);
    console.error("Error:", error);
    // Show error in your UI
  });

  // 4. Subscribe to PONG (optional, for health checks)
  client.subscribe("/user/queue/ai/pong", (message) => {
    const pong: AIActivityMessage = JSON.parse(message.body);
    console.log("Pong:", pong);
  });
};
```

### CRITICAL: Why "/user/queue/..." and NOT "/queue/..."

Spring's `setUserDestinationPrefix("/user")` means when the server calls:

```java
messagingTemplate.convertAndSendToUser(
    user.getId().toString(),   // ← user identifier
    "/queue/ai/activity",      // ← destination
    activity                   // ← payload
);
```

Spring internally routes this to: `/user/{userId}/queue/ai/activity`

But on the **client side**, you subscribe to `/user/queue/ai/activity` (without the userId). Spring resolves the current authenticated user automatically from the STOMP session principal.

**If you subscribe to `/queue/ai/activity` (without `/user/` prefix) — you will NEVER receive messages. This is the #1 cause of "activity not reflecting in UI."**

---

## 7. Step 4 — Send an Analyze Request

```typescript
function sendAnalyzeRequest(
  client: Client,
  userMessage: string,
  conversationId: number | null,
  connectionIds: number[]
) {
  const request: AnalyzeRequest = {
    userMessage,
    conversationId,    // null for new conversation, number for existing
    connectionIds,     // at least one required, e.g. [1] or [1, 2]
  };

  client.publish({
    destination: "/app/ai/analyze",
    body: JSON.stringify(request),
  });
}

// Usage:
sendAnalyzeRequest(client, "Show me all users", null, [1]);
sendAnalyzeRequest(client, "Count orders by status", 5, [1, 2]);
```

### Request Validation Rules

| Field | Required | Validation |
|---|---|---|
| `userMessage` | Yes | Cannot be null or blank |
| `connectionIds` | Yes | Must have at least 1 ID |
| `conversationId` | No | null = creates new conversation; number = appends to existing |

If validation fails, the server sends an error to both `/user/queue/ai/response` and `/user/queue/ai/error`.

---

## 8. Step 5 — Handle Activity Updates (Real-Time)

After you send an analyze request, the backend processes it through **7 phases**. Each phase sends one or more `AIActivityMessage` updates to `/user/queue/ai/activity`.

### Activity Message Shape

```typescript
interface AIActivityMessage {
  phase: string;            // e.g., "understanding_intent", "executing_queries"
  status: string;           // "in_progress" | "completed" | "error"
  message: string;          // Human-readable description of what's happening
  conversationId: number;   // Links activity to a specific conversation
  timestamp: string;        // ISO 8601 instant, e.g., "2026-02-07T12:34:56.789Z"
}
```

### Example Activity Stream (what you receive in order)

```json
{"phase":"understanding_intent","status":"in_progress","message":"Analyzing your request: \"Show me all users\"","conversationId":1,"timestamp":"2026-02-07T10:00:00.100Z"}
{"phase":"understanding_intent","status":"completed","message":"User intent understood","conversationId":1,"timestamp":"2026-02-07T10:00:00.200Z"}
{"phase":"mapping_data_sources","status":"in_progress","message":"Identifying data sources from 1 connection(s)","conversationId":1,"timestamp":"2026-02-07T10:00:00.300Z"}
{"phase":"mapping_data_sources","status":"completed","message":"Mapped to 1 data source(s): My PostgreSQL","conversationId":1,"timestamp":"2026-02-07T10:00:00.400Z"}
{"phase":"analyzing_schemas","status":"in_progress","message":"Retrieving schema information from connected databases","conversationId":1,"timestamp":"2026-02-07T10:00:00.500Z"}
{"phase":"analyzing_schemas","status":"in_progress","message":"Schema loaded for 'My PostgreSQL': 5 table(s) found","conversationId":1,"timestamp":"2026-02-07T10:00:01.000Z"}
{"phase":"analyzing_schemas","status":"completed","message":"Schema analysis complete for 1 database(s)","conversationId":1,"timestamp":"2026-02-07T10:00:01.100Z"}
{"phase":"generating_queries","status":"in_progress","message":"Generating safe read-only queries based on your request","conversationId":1,"timestamp":"2026-02-07T10:00:01.200Z"}
{"phase":"generating_queries","status":"in_progress","message":"Generated query for connection 1: SELECT t.id, t.username, t.email FROM users t LIMIT 100","conversationId":1,"timestamp":"2026-02-07T10:00:01.500Z"}
{"phase":"generating_queries","status":"completed","message":"Generated 1 safe SELECT query/queries","conversationId":1,"timestamp":"2026-02-07T10:00:01.600Z"}
{"phase":"executing_queries","status":"in_progress","message":"Executing queries against data sources","conversationId":1,"timestamp":"2026-02-07T10:00:01.700Z"}
{"phase":"executing_queries","status":"in_progress","message":"Executing query on 'My PostgreSQL'...","conversationId":1,"timestamp":"2026-02-07T10:00:01.800Z"}
{"phase":"executing_queries","status":"in_progress","message":"Query on 'My PostgreSQL' returned 42 row(s) in 120ms","conversationId":1,"timestamp":"2026-02-07T10:00:02.000Z"}
{"phase":"executing_queries","status":"completed","message":"Query execution completed with 1 successful result(s)","conversationId":1,"timestamp":"2026-02-07T10:00:02.100Z"}
{"phase":"preparing_response","status":"in_progress","message":"Preparing response and determining best visualization","conversationId":1,"timestamp":"2026-02-07T10:00:02.200Z"}
{"phase":"preparing_response","status":"completed","message":"Response prepared with suggested visualization: table","conversationId":1,"timestamp":"2026-02-07T10:00:02.300Z"}
{"phase":"completed","status":"completed","message":"Analysis complete","conversationId":1,"timestamp":"2026-02-07T10:00:02.400Z"}
```

### Recommended React State Structure for Activity

```typescript
interface ActivityState {
  phases: {
    understanding_intent: PhaseState;
    mapping_data_sources: PhaseState;
    analyzing_schemas: PhaseState;
    generating_queries: PhaseState;
    executing_queries: PhaseState;
    preparing_response: PhaseState;
    completed: PhaseState;
  };
  currentPhase: string | null;
  isProcessing: boolean;
  logs: AIActivityMessage[];  // full ordered log of all activity messages
}

interface PhaseState {
  status: "pending" | "in_progress" | "completed" | "error";
  message: string;
  startedAt: string | null;
  completedAt: string | null;
}
```

---

## 9. Step 6 — Handle Final Response

After all phases complete, the server sends the final `AnalyzeResponse` to `/user/queue/ai/response`.

### Success Response Shape

```typescript
interface AnalyzeResponse {
  success: true;
  conversationId: number;
  summary: string;                        // Human-readable summary text
  queryResults: QueryResult[];            // Array of results from each connection
  suggestedVisualization: string;         // "table" | "bar_chart" | "pie_chart" | "line_chart" | "kpi_card"
  error: null;
  timestamp: string;
}

interface QueryResult {
  connectionId: number;
  connectionName: string;
  query: string;                          // The SQL that was executed
  data: Record<string, any>[];            // Array of row objects
  columns: string[];                      // Column names in order
  rowCount: number;
}
```

### Example Success Response

```json
{
  "success": true,
  "conversationId": 1,
  "summary": "Results for your query: \"Show me all users\"\n\nFrom My PostgreSQL: 42 row(s) returned",
  "queryResults": [
    {
      "connectionId": 1,
      "connectionName": "My PostgreSQL",
      "query": "SELECT t.id, t.username, t.email FROM users t LIMIT 100",
      "data": [
        { "id": 1, "username": "john_doe", "email": "john@example.com" },
        { "id": 2, "username": "jane_smith", "email": "jane@example.com" }
      ],
      "columns": ["id", "username", "email"],
      "rowCount": 2
    }
  ],
  "suggestedVisualization": "table",
  "error": null,
  "timestamp": "2026-02-07T10:00:02.400Z"
}
```

---

## 10. Step 7 — Handle Errors

Error responses are sent to **both** `/user/queue/ai/response` AND `/user/queue/ai/error`.

### Error Response Shape

```typescript
interface AnalyzeResponse {
  success: false;
  conversationId: number | null;
  summary: null;
  queryResults: null;
  suggestedVisualization: null;
  error: {
    code: string;
    message: string;
    suggestion: string;      // Actionable hint for the user
  };
  timestamp: string;
}
```

### Error Codes Reference

| Code | When It Happens | User-Facing Suggestion |
|---|---|---|
| `VALIDATION_ERROR` | Empty message or missing connectionIds | Check inputs |
| `NO_CONNECTIONS` | Connection IDs don't match any user connections | Verify connection IDs |
| `SCHEMA_ERROR` | Can't connect to any database to read schema | Check DB connections |
| `QUERY_GENERATION_FAILED` | Can't generate a valid SELECT for the request | Rephrase the question |
| `INTERNAL_ERROR` | Unexpected server exception | Retry; check connections |

---

## 11. Step 8 — Ping / Connection Health Check

```typescript
// Send ping
client.publish({
  destination: "/app/ai/ping",
  body: "",
});

// Response arrives on /user/queue/ai/pong:
// { "phase": "ping", "status": "ok", "message": "Connected as user 1", "timestamp": "..." }
```

Use this to verify the WebSocket connection is authenticated and alive.

---

## 12. Complete React Hook — `useWebSocket`

```typescript
// src/hooks/useWebSocket.ts

import { useRef, useState, useCallback, useEffect } from "react";
import { Client, IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";

// ---------- Types ----------

export interface AIActivityMessage {
  phase: string;
  status: string;
  message: string;
  conversationId: number | null;
  timestamp: string;
}

export interface QueryResult {
  connectionId: number;
  connectionName: string;
  query: string;
  data: Record<string, any>[];
  columns: string[];
  rowCount: number;
}

export interface AnalyzeResponse {
  success: boolean;
  conversationId: number | null;
  summary: string | null;
  queryResults: QueryResult[] | null;
  suggestedVisualization: string | null;
  error: {
    code: string;
    message: string;
    suggestion: string;
  } | null;
  timestamp: string;
}

export interface AnalyzeRequest {
  userMessage: string;
  conversationId: number | null;
  connectionIds: number[];
}

export type ConnectionStatus = "disconnected" | "connecting" | "connected" | "error";

// ---------- Hook ----------

const WS_URL = import.meta.env.VITE_WS_URL || "http://localhost:8080/ws";

export function useWebSocket(accessToken: string | null) {
  const clientRef = useRef<Client | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("disconnected");
  const [activities, setActivities] = useState<AIActivityMessage[]>([]);
  const [latestResponse, setLatestResponse] = useState<AnalyzeResponse | null>(null);
  const [latestError, setLatestError] = useState<AnalyzeResponse | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [currentPhase, setCurrentPhase] = useState<string | null>(null);

  // Connect
  const connect = useCallback(() => {
    if (!accessToken) {
      console.warn("Cannot connect: no access token");
      return;
    }

    // Disconnect existing client if any
    if (clientRef.current?.active) {
      clientRef.current.deactivate();
    }

    setConnectionStatus("connecting");

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
      debug: (msg) => {
        if (import.meta.env.DEV) {
          console.debug("[STOMP]", msg);
        }
      },
    });

    client.onConnect = () => {
      setConnectionStatus("connected");

      // Subscribe to activity updates
      client.subscribe("/user/queue/ai/activity", (message: IMessage) => {
        const activity: AIActivityMessage = JSON.parse(message.body);
        setActivities((prev) => [...prev, activity]);
        setCurrentPhase(activity.phase);

        // Track processing state
        if (activity.phase === "completed" && activity.status === "completed") {
          setIsProcessing(false);
          setCurrentPhase(null);
        }
        if (activity.phase === "error") {
          setIsProcessing(false);
        }
      });

      // Subscribe to final response
      client.subscribe("/user/queue/ai/response", (message: IMessage) => {
        const response: AnalyzeResponse = JSON.parse(message.body);
        setLatestResponse(response);
        if (!response.success) {
          setLatestError(response);
        }
        setIsProcessing(false);
        setCurrentPhase(null);
      });

      // Subscribe to errors
      client.subscribe("/user/queue/ai/error", (message: IMessage) => {
        const error: AnalyzeResponse = JSON.parse(message.body);
        setLatestError(error);
        setIsProcessing(false);
        setCurrentPhase(null);
      });

      // Subscribe to pong
      client.subscribe("/user/queue/ai/pong", (message: IMessage) => {
        console.log("Pong received:", JSON.parse(message.body));
      });
    };

    client.onStompError = (frame) => {
      console.error("STOMP error:", frame.headers["message"], frame.body);
      setConnectionStatus("error");
    };

    client.onWebSocketClose = () => {
      setConnectionStatus("disconnected");
    };

    client.onWebSocketError = (event) => {
      console.error("WebSocket error:", event);
      setConnectionStatus("error");
    };

    client.activate();
    clientRef.current = client;
  }, [accessToken]);

  // Disconnect
  const disconnect = useCallback(() => {
    if (clientRef.current?.active) {
      clientRef.current.deactivate();
      clientRef.current = null;
    }
    setConnectionStatus("disconnected");
  }, []);

  // Send analyze request
  const sendAnalyzeRequest = useCallback((request: AnalyzeRequest) => {
    if (!clientRef.current?.active) {
      console.error("Cannot send: WebSocket not connected");
      return false;
    }

    // Reset state for new request
    setActivities([]);
    setLatestResponse(null);
    setLatestError(null);
    setIsProcessing(true);
    setCurrentPhase("understanding_intent");

    clientRef.current.publish({
      destination: "/app/ai/analyze",
      body: JSON.stringify(request),
    });

    return true;
  }, []);

  // Send ping
  const sendPing = useCallback(() => {
    if (!clientRef.current?.active) return false;
    clientRef.current.publish({
      destination: "/app/ai/ping",
      body: "",
    });
    return true;
  }, []);

  // Clear activities
  const clearActivities = useCallback(() => {
    setActivities([]);
    setCurrentPhase(null);
    setLatestResponse(null);
    setLatestError(null);
  }, []);

  // Auto-connect when token is available
  useEffect(() => {
    if (accessToken) {
      connect();
    }
    return () => {
      disconnect();
    };
  }, [accessToken, connect, disconnect]);

  return {
    connectionStatus,
    activities,
    latestResponse,
    latestError,
    isProcessing,
    currentPhase,
    connect,
    disconnect,
    sendAnalyzeRequest,
    sendPing,
    clearActivities,
  };
}
```

---

## 13. Complete React Context Provider

```typescript
// src/context/WebSocketContext.tsx

import React, { createContext, useContext, useMemo } from "react";
import {
  useWebSocket,
  AIActivityMessage,
  AnalyzeResponse,
  AnalyzeRequest,
  ConnectionStatus,
} from "../hooks/useWebSocket";

interface WebSocketContextValue {
  connectionStatus: ConnectionStatus;
  activities: AIActivityMessage[];
  latestResponse: AnalyzeResponse | null;
  latestError: AnalyzeResponse | null;
  isProcessing: boolean;
  currentPhase: string | null;
  connect: () => void;
  disconnect: () => void;
  sendAnalyzeRequest: (request: AnalyzeRequest) => boolean;
  sendPing: () => boolean;
  clearActivities: () => void;
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null);

interface Props {
  accessToken: string | null;
  children: React.ReactNode;
}

export function WebSocketProvider({ accessToken, children }: Props) {
  const ws = useWebSocket(accessToken);

  const value = useMemo(() => ws, [
    ws.connectionStatus,
    ws.activities,
    ws.latestResponse,
    ws.latestError,
    ws.isProcessing,
    ws.currentPhase,
  ]);

  return (
    <WebSocketContext.Provider value={value}>
      {children}
    </WebSocketContext.Provider>
  );
}

export function useWS(): WebSocketContextValue {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error("useWS must be used within a <WebSocketProvider>");
  }
  return context;
}
```

### Mounting in App

```typescript
// src/App.tsx

import { WebSocketProvider } from "./context/WebSocketContext";
import { useAuth } from "./hooks/useAuth"; // your auth hook

function App() {
  const { accessToken } = useAuth();

  return (
    <WebSocketProvider accessToken={accessToken}>
      {/* Your app routes/components */}
    </WebSocketProvider>
  );
}
```

---

## 14. Example UI Component Usage

### Activity Tracker Component

```tsx
// src/components/AIActivityTracker.tsx

import { useWS } from "../context/WebSocketContext";

const PHASE_LABELS: Record<string, string> = {
  understanding_intent: "Understanding your request",
  mapping_data_sources: "Finding data sources",
  analyzing_schemas: "Analyzing database schemas",
  generating_queries: "Generating SQL queries",
  executing_queries: "Running queries",
  preparing_response: "Preparing results",
  completed: "Done",
  error: "Error occurred",
};

const PHASE_ORDER = [
  "understanding_intent",
  "mapping_data_sources",
  "analyzing_schemas",
  "generating_queries",
  "executing_queries",
  "preparing_response",
  "completed",
];

export function AIActivityTracker() {
  const { activities, currentPhase, isProcessing } = useWS();

  if (!isProcessing && activities.length === 0) return null;

  // Derive phase status from activities
  const phaseStatus: Record<string, string> = {};
  for (const activity of activities) {
    phaseStatus[activity.phase] = activity.status;
  }

  return (
    <div className="activity-tracker">
      <h3>AI Analysis Progress</h3>
      <div className="phases">
        {PHASE_ORDER.map((phase) => {
          const status = phaseStatus[phase] || "pending";
          const isCurrent = phase === currentPhase;

          return (
            <div
              key={phase}
              className={`phase ${status} ${isCurrent ? "current" : ""}`}
            >
              <span className="phase-icon">
                {status === "completed" ? "✓" :
                 status === "in_progress" ? "⟳" :
                 status === "error" ? "✗" : "○"}
              </span>
              <span className="phase-label">{PHASE_LABELS[phase]}</span>
            </div>
          );
        })}
      </div>

      {/* Live log */}
      <div className="activity-log">
        {activities.map((a, i) => (
          <div key={i} className={`log-entry ${a.status}`}>
            <span className="log-time">
              {new Date(a.timestamp).toLocaleTimeString()}
            </span>
            <span className="log-message">{a.message}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
```

### Chat Input + Submit Component

```tsx
// src/components/AIChatInput.tsx

import { useState } from "react";
import { useWS } from "../context/WebSocketContext";

interface Props {
  connectionIds: number[];
  conversationId: number | null;
  onConversationCreated?: (id: number) => void;
}

export function AIChatInput({ connectionIds, conversationId, onConversationCreated }: Props) {
  const [message, setMessage] = useState("");
  const { sendAnalyzeRequest, isProcessing, connectionStatus, latestResponse } = useWS();

  // Watch for new conversation ID in responses
  if (latestResponse?.conversationId && !conversationId && onConversationCreated) {
    onConversationCreated(latestResponse.conversationId);
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim() || connectionIds.length === 0) return;

    const sent = sendAnalyzeRequest({
      userMessage: message.trim(),
      conversationId,
      connectionIds,
    });

    if (sent) {
      setMessage("");
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <div className="status-badge">
        {connectionStatus === "connected" ? "🟢 Connected" :
         connectionStatus === "connecting" ? "🟡 Connecting..." :
         "🔴 Disconnected"}
      </div>

      <input
        type="text"
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        placeholder="Ask a question about your data..."
        disabled={isProcessing || connectionStatus !== "connected"}
      />

      <button
        type="submit"
        disabled={
          isProcessing ||
          connectionStatus !== "connected" ||
          !message.trim() ||
          connectionIds.length === 0
        }
      >
        {isProcessing ? "Analyzing..." : "Analyze"}
      </button>
    </form>
  );
}
```

### Results Display Component

```tsx
// src/components/AIResults.tsx

import { useWS } from "../context/WebSocketContext";

export function AIResults() {
  const { latestResponse, latestError } = useWS();

  if (latestError?.error) {
    return (
      <div className="error-panel">
        <h4>Error: {latestError.error.code}</h4>
        <p>{latestError.error.message}</p>
        <p className="suggestion">{latestError.error.suggestion}</p>
      </div>
    );
  }

  if (!latestResponse?.success) return null;

  return (
    <div className="results-panel">
      <p className="summary">{latestResponse.summary}</p>
      <p className="viz-hint">
        Suggested visualization: <strong>{latestResponse.suggestedVisualization}</strong>
      </p>

      {latestResponse.queryResults?.map((result, i) => (
        <div key={i} className="query-result">
          <h4>{result.connectionName}</h4>
          <code className="sql">{result.query}</code>
          <p>{result.rowCount} row(s) returned</p>

          <table>
            <thead>
              <tr>
                {result.columns.map((col) => (
                  <th key={col}>{col}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.data.map((row, ri) => (
                <tr key={ri}>
                  {result.columns.map((col) => (
                    <td key={col}>{String(row[col] ?? "")}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  );
}
```

---

## 15. Activity Phases Reference

| Phase Code | Description | When It Fires |
|---|---|---|
| `understanding_intent` | Parsing user's natural-language question | Immediately on request |
| `mapping_data_sources` | Resolving connection IDs to actual DB connections | After intent |
| `analyzing_schemas` | Connecting to DBs and reading table/column metadata | After mapping |
| `generating_queries` | Building safe SELECT SQL from schema + intent | After schema |
| `executing_queries` | Running generated SQL against live databases | After generation |
| `preparing_response` | Building summary text and choosing visualization type | After execution |
| `completed` | All done — final response follows immediately | Last phase |
| `error` | Something went wrong — error response follows | On any failure |

Each phase fires with `status: "in_progress"` (one or more times) and then `status: "completed"` (once), except `error` which fires with `status: "error"`.

---

## 16. Message Payload Schemas (TypeScript)

Copy these into your project (e.g., `src/types/websocket.ts`):

```typescript
// src/types/websocket.ts

// ===== Client → Server =====

export interface AnalyzeRequest {
  userMessage: string;
  conversationId: number | null;
  connectionIds: number[];
}

// ===== Server → Client =====

export interface AIActivityMessage {
  phase:
    | "understanding_intent"
    | "mapping_data_sources"
    | "analyzing_schemas"
    | "generating_queries"
    | "executing_queries"
    | "preparing_response"
    | "completed"
    | "error"
    | "ping";
  status: "in_progress" | "completed" | "error" | "ok";
  message: string;
  conversationId: number | null;
  timestamp: string; // ISO 8601
}

export interface AnalyzeResponse {
  success: boolean;
  conversationId: number | null;
  summary: string | null;
  queryResults: QueryResult[] | null;
  suggestedVisualization:
    | "table"
    | "bar_chart"
    | "pie_chart"
    | "line_chart"
    | "kpi_card"
    | null;
  error: ErrorInfo | null;
  timestamp: string; // ISO 8601
}

export interface QueryResult {
  connectionId: number;
  connectionName: string;
  query: string;
  data: Record<string, any>[];
  columns: string[];
  rowCount: number;
}

export interface ErrorInfo {
  code: string;
  message: string;
  suggestion: string;
}
```

---

## 17. Common Issues & Debugging

### Issue 1: "Activity not reflecting in UI"

**Root Causes (most likely → least likely):**

| # | Cause | Fix |
|---|---|---|
| 1 | Subscribing to `/queue/ai/activity` instead of **`/user/queue/ai/activity`** | Always prefix subscriptions with `/user/` |
| 2 | Subscribing BEFORE `onConnect` fires — subscriptions are lost | Move all `.subscribe()` calls INSIDE the `client.onConnect` callback |
| 3 | Token expired or invalid — connection silently rejected | Check `onStompError` for auth failures; refresh the token |
| 4 | State not updating React render — using stale ref or closure | Use `setState(prev => [...prev, newItem])` functional updates |
| 5 | Multiple Client instances — subscribing on one, receiving on another | Use a single Client via `useRef` and clean up on unmount |
| 6 | Using raw `WebSocket` instead of STOMP client | This backend uses STOMP protocol — raw WS will NOT work |
| 7 | CORS blocking the SockJS handshake | Backend allows all origins (`*`). Check browser dev tools Network tab |
| 8 | Reconnect happens but subscriptions not re-established | `onConnect` fires on every reconnect — subscriptions inside it are re-created automatically |

### Issue 2: "STOMP error on connect"

```
>>> ERROR
message: ...
```

- **Invalid token** — re-authenticate via REST `/api/v1/auth/login` and get a fresh `accessToken`
- **Token header missing** — ensure `connectHeaders: { Authorization: "Bearer <token>" }` is set
- **User not found in DB** — the JWT references a userId that doesn't exist

### Issue 3: "Connection drops randomly"

- Ensure `heartbeatIncoming` and `heartbeatOutgoing` are set (e.g., `10000`)
- Ensure `reconnectDelay` is set (e.g., `5000`)
- Check if a proxy/load balancer is closing idle WebSocket connections — increase idle timeout

### Issue 4: "Getting responses for wrong conversation"

- Every `AIActivityMessage` and `AnalyzeResponse` includes a `conversationId` field
- Filter activities in your UI by `conversationId` to show only the current conversation's updates

### Issue 5: "SockJS connection fails, falls back to polling"

- Check that the backend is reachable at `http://localhost:8080/ws/info` — SockJS makes this call first
- If behind a reverse proxy, ensure it forwards WebSocket upgrade requests and passes through `/ws/**`

### Debugging Checklist

```
1. Open Browser DevTools → Network tab → filter by "WS"
2. You should see a WebSocket connection to /ws/xxx/xxx/websocket
3. In the Messages tab, look for:
   - CONNECT frame (outgoing) with Authorization header
   - CONNECTED frame (incoming) — means auth succeeded
   - SUBSCRIBE frames for /user/queue/ai/activity, /user/queue/ai/response, /user/queue/ai/error
   - SEND frame to /app/ai/analyze when you submit a request
   - MESSAGE frames coming back with activity/response data
4. If you see ERROR frame after CONNECT → auth failed
5. If SUBSCRIBE is there but no MESSAGE frames → check the subscription destination paths
6. Check server logs for "WebSocket authenticated for user: ..." messages
```

---

## 18. Sequence Diagram

```
React UI                          Spring Boot Backend
   │                                      │
   │  1. POST /api/v1/auth/login          │
   │ ────────────────────────────────────▶ │
   │  ◀──── { accessToken, user }         │
   │                                      │
   │  2. STOMP CONNECT (Authorization:    │
   │     Bearer <token>)                  │
   │ ════════════════════════════════════▶ │
   │     WebSocketAuthInterceptor         │
   │     validates JWT, loads User        │
   │  ◀════ STOMP CONNECTED               │
   │                                      │
   │  3. SUBSCRIBE /user/queue/ai/activity│
   │ ════════════════════════════════════▶ │
   │  SUBSCRIBE /user/queue/ai/response   │
   │ ════════════════════════════════════▶ │
   │  SUBSCRIBE /user/queue/ai/error      │
   │ ════════════════════════════════════▶ │
   │                                      │
   │  4. SEND /app/ai/analyze             │
   │     { userMessage, connectionIds,    │
   │       conversationId }               │
   │ ════════════════════════════════════▶ │
   │                                      │
   │     ┌──── AIAnalystOrchestrator ─────┤
   │     │ Phase 1: Understanding intent  │
   │  ◀══╪═ MESSAGE /user/queue/ai/activity
   │     │   { phase: "understanding_intent", status: "in_progress" }
   │  ◀══╪═ MESSAGE { status: "completed" }
   │     │                                │
   │     │ Phase 2: Mapping data sources  │
   │  ◀══╪═ MESSAGE { phase: "mapping_data_sources", ... }
   │  ◀══╪═ MESSAGE { status: "completed" }
   │     │                                │
   │     │ Phase 3: Analyzing schemas     │
   │  ◀══╪═ MESSAGE(s) { phase: "analyzing_schemas", ... }
   │  ◀══╪═ MESSAGE { status: "completed" }
   │     │                                │
   │     │ Phase 4: Generating queries    │
   │  ◀══╪═ MESSAGE(s) { phase: "generating_queries", ... }
   │  ◀══╪═ MESSAGE { status: "completed" }
   │     │                                │
   │     │ Phase 5: Executing queries     │
   │  ◀══╪═ MESSAGE(s) { phase: "executing_queries", ... }
   │  ◀══╪═ MESSAGE { status: "completed" }
   │     │                                │
   │     │ Phase 6: Preparing response    │
   │  ◀══╪═ MESSAGE(s) { phase: "preparing_response", ... }
   │  ◀══╪═ MESSAGE { status: "completed" }
   │     │                                │
   │     │ Phase 7: Completed             │
   │  ◀══╪═ MESSAGE { phase: "completed" }
   │     └────────────────────────────────┤
   │                                      │
   │  ◀══ MESSAGE /user/queue/ai/response │
   │     { success: true, queryResults, summary, ... }
   │                                      │
   │  (Connection stays open for next     │
   │   request — no need to reconnect)    │
   │                                      │
```

---

## Quick Start Checklist

- [ ] Install `@stomp/stompjs` and `sockjs-client`
- [ ] Login via REST API and obtain `accessToken`
- [ ] Create STOMP client with `SockJS` factory and JWT in `connectHeaders`
- [ ] Subscribe to `/user/queue/ai/activity`, `/user/queue/ai/response`, `/user/queue/ai/error` **inside** `onConnect`
- [ ] Send analyze requests to `/app/ai/analyze` with `{ userMessage, connectionIds, conversationId }`
- [ ] Update React state on each activity message using functional `setState`
- [ ] Display activity phases as a progress tracker
- [ ] Render final results table/chart based on `suggestedVisualization`
- [ ] Handle errors from both the error subscription and the response subscription
- [ ] Implement reconnect handling (automatic with `reconnectDelay`)
