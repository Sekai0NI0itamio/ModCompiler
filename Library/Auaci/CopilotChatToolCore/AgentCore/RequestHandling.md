# Request Handling in Copilot Chat Agentic System

## Request Processing Pipeline

### 1. Input Reception
Requests enter the system through various channels:
- Chat interface text input
- Inline chat in editor
- Terminal integration
- VS Code command invocations

### 2. Intent Classification
The system classifies requests using predefined intents:

```typescript
export const enum Intent {
  Explain = 'explain',
  Review = 'review',
  Tests = 'tests',
  Fix = 'fix',
  New = 'new',
  NewNotebook = 'newNotebook',
  notebookEditor = 'notebookEditor',
  InlineChat = 'inlineChat',
  Search = 'search',
  SemanticSearch = 'semanticSearch',
  Terminal = 'terminal',
  TerminalExplain = 'terminalExplain',
  VSCode = 'vscode',
  Unknown = 'unknown',
  SetupTests = 'setupTests',
  Editor = 'editor',
  Doc = 'doc',
  Edit = 'edit',
  Edit2 = 'edit2',
  Agent = 'editAgent',
  Generate = 'generate',
  SearchPanel = 'searchPanel',
  SearchKeywords = 'searchKeywords',
  AskAgent = 'askAgent',
}
```

### 3. Agent Mapping
Each intent maps to a specific agent:

```typescript
export const agentsToCommands: Partial<Record<Intent, Record<string, Intent>>> = {
  [Intent.Agent]: {
    'explain': Intent.Explain,
    'edit': Intent.Edit,
    'review': Intent.Review,
    'tests': Intent.Tests,
    'fix': Intent.Fix,
    'new': Intent.New,
    'newNotebook': Intent.NewNotebook,
    'semanticSearch': Intent.SemanticSearch,
    'setupTests': Intent.SetupTests,
  },
  [Intent.VSCode]: {
    'search': Intent.Search,
  },
  [Intent.Terminal]: {
    'explain': Intent.TerminalExplain
  },
  [Intent.Editor]: {
    'doc': Intent.Doc,
    'fix': Intent.Fix,
    'explain': Intent.Explain,
    'review': Intent.Review,
    'tests': Intent.Tests,
    'edit': Intent.Edit,
    'generate': Intent.Generate
  }
};
```

### 4. Context Gathering
Before processing:
- Current file content
- Selected text
- Cursor position
- Workspace structure
- Recent edits
- Conversation history

### 5. Tool Selection
Based on intent and context, appropriate tools are selected:
- Core tools (always available)
- Specialized tools per agent
- Dynamic tool discovery

### 6. Prompt Construction
The system builds prompts that include:
- User request
- Context information
- Available tools
- Agent instructions
- Conversation history

## Request Types

### Synchronous Requests
- Simple queries with immediate responses
- Tool calls that complete quickly
- Single-step operations

### Asynchronous Requests
- Complex multi-step tasks
- Long-running operations
- Subagent delegations

### Streaming Responses
- Progressive response generation
- Tool results streamed as available
- Interactive feedback

## Error Handling

### Request-Level Errors
- Invalid input format
- Missing required context
- Authentication failures

### Processing Errors
- Tool execution failures
- Network timeouts
- Resource limitations

### Recovery Mechanisms
- Fallback strategies
- Alternative tool selection
- User clarification requests

## Performance Considerations

### Caching
- Tool results caching
- Context memoization
- Model response caching

### Optimization
- Parallel tool execution
- Batched operations
- Progressive loading

### Rate Limiting
- API call throttling
- Concurrent request limits
- Backoff strategies</content>
<parameter name="filePath">/Users/solosolar/Desktop/Code Projects/aicoder/BetaR2/IDE/CopilotChatToolCore/AgentCore/RequestHandling.md