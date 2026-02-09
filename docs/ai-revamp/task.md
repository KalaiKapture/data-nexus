# Multi-Source AI Chat Architecture - Revamp Plan

## Phase 1: Architecture Design
- [/] Create implementation plan for new architecture
- [ ] Design data source abstraction layer
- [ ] Design AI provider strategy pattern
- [ ] Design conversational flow state machine

## Phase 2: Core Components
- [ ] Implement `DataSource` abstraction interface
- [ ] Implement `DatabaseDataSource` (existing functionality)
- [ ] Implement `MCPServerDataSource` (new)
- [ ] Extend `DatabaseConnection` to support type="MCP"
- [ ] Create `DataSourceRegistry` for managing sources
- [ ] Implement unified schema extraction

## Phase 3: AI Provider Integration
- [x] Create `AIProvider` interface
- [x] Implement `OpenAIProvider`
- [x] Implement `GeminiProvider`
- [x] Implement `ClaudeProvider`
- [x] Create `AIProviderFactory` with user selection

## Phase 4: Conversational Flow
- [x] Design conversation state management
- [x] Implement clarification request/response handling
- [x] Update WebSocket message types for bidirectional flow
- [x] Add conversation context tracking

## Phase 5: Query Generation & Execution
- [x] Extend query generation for MCP server requests
- [x] Create unified execution layer for multi-source
- [x] Implement result formatting and merging

## Phase 6: Testing & Validation
- [x] Unit tests for new components
- [x] Integration tests
- [ ] End-to-end testing
- [x] Documentation updates

## Phase 7: Deployment & Migration
- [ ] Database migration strategy
- [ ] Backward compatibility testing
- [ ] Production deployment plan
