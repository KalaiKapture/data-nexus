# AI Chat Architecture Revamp - Documentation

This directory contains all documentation related to the multi-source AI chat architecture revamp.

## Overview

The AI chat system has been revamped to support:
- Multiple data sources (databases and MCP servers)
- Multiple AI providers (Gemini, Claude, OpenAI)
- Conversational clarification flow
- Real-time WebSocket communication

## Documentation Files

### Planning & Analysis
- **[architecture_analysis.md](./architecture_analysis.md)** - Initial analysis of the existing `AIAnalystOrchestrator` architecture
- **[implementation_plan.md](./implementation_plan.md)** - Detailed implementation plan for the revamp
- **[task.md](./task.md)** - Phase-by-phase task checklist

### Implementation Walkthroughs
- **[phase1_2_walkthrough.md](./phase1_2_walkthrough.md)** - Data source abstraction and schema extraction
- **[phase3_walkthrough.md](./phase3_walkthrough.md)** - AI provider integration and WebSocket routing
- **[phase4_5_walkthrough.md](./phase4_5_walkthrough.md)** - Conversation state management and unified execution
- **[phase6_testing_walkthrough.md](./phase6_testing_walkthrough.md)** - Unit testing and validation

## Key Components

### Data Source Layer
- `DataSource` interface - Abstraction for all data sources
- `DatabaseDataSource` - SQL database connections
- `MCPServerDataSource` - MCP server integration
- `DataSourceRegistry` - Central registry for data sources
- `UnifiedExecutionService` - Unified execution across all sources

### AI Provider Layer
- `AIProvider` interface - Abstraction for AI models
- `GeminiProvider` - Google Gemini integration
- `ClaudeProvider` - Anthropic Claude integration
- `OpenAIProvider` - OpenAI integration (placeholder)
- `AIProviderFactory` - Provider selection strategy

### Conversation Management
- `ConversationStateManager` - Multi-turn conversation state tracking
- `MultiSourceChatOrchestrator` - Main orchestration logic
- `AIAnalystWebSocketController` - WebSocket endpoint with routing

### Models & DTOs
- `AIRequest` / `AIResponse` - AI communication models
- `DataRequest` - Unified request abstraction (SQL, MCP Tool, MCP Resource)
- `SourceSchema` - Unified schema representation
- `AnalyzeRequest` / `AnalyzeResponse` - WebSocket DTOs
- `ClarificationRequest` - Clarification flow DTO

## Architecture Diagram

```
User (WebSocket)
    ↓
AIAnalystWebSocketController
    ↓
    ├─→ AIAnalystOrchestrator (legacy)
    └─→ MultiSourceChatOrchestrator (new)
            ↓
            ├─→ ConversationStateManager
            ├─→ DataSourceRegistry
            │       ├─→ DatabaseDataSource
            │       └─→ MCPServerDataSource
            ├─→ AIProviderFactory
            │       ├─→ GeminiProvider
            │       ├─→ ClaudeProvider
            │       └─→ OpenAIProvider
            └─→ UnifiedExecutionService
```

## Testing

See [phase6_testing_walkthrough.md](./phase6_testing_walkthrough.md) for comprehensive test documentation.

### Test Files
- `MultiSourceChatOrchestratorTest` - Orchestration tests
- `UnifiedExecutionServiceTest` - Execution layer tests
- `ConversationStateManagerTest` - State management tests
- `GeminiProviderTest` - AI provider tests

## Next Steps

### Phase 7: Deployment & Migration
- Database migration strategy for new schema
- Backward compatibility testing
- Production deployment plan
- Environment variable configuration

## Configuration

Required environment variables:
```properties
# AI Providers
ai.gemini.api-key=your-key
ai.gemini.model=gemini-1.5-pro
ai.claude.api-key=your-key
ai.claude.model=claude-3-5-sonnet-20241022
ai.openai.api-key=your-key
ai.openai.model=gpt-4
ai.default-provider=gemini

# MCP Configuration
mcp.connection-timeout=5000
mcp.request-timeout=30000
```

## Known Issues & TODOs

1. **Compilation Errors** (to fix):
   - `GeminiProvider.java:131` - String.join type mismatch
   - `GeminiProvider.java:132` - Missing `getType()` method
   - `ClaudeProvider.java:81` - Missing `buildPromptPublic()` method

2. **Entity Field Mismatch**:
   - `Conversation` entity uses `setUser()` not `setUser_id()`

3. **Future Enhancements**:
   - Full OpenAI provider implementation
   - Visualization suggestions from AI
   - Query optimization hints
   - Performance monitoring

## Contributing

When adding new features:
1. Update the appropriate phase walkthrough
2. Add unit tests
3. Update this README
4. Follow the existing patterns in `MultiSourceChatOrchestrator`
