# Mode Switching in Copilot Chat Agentic System

## Mode Types

### 1. CopilotToolMode
The system defines two primary modes for tool invocation:

```typescript
export enum CopilotToolMode {
  /**
   * Give a shorter result, agent mode can call again to get more context
   */
  PartialContext,

  /**
   * Give a longer result, it gets one shot
   */
  FullContext,
}
```

### 2. Agent Modes
Based on intent and complexity:

- **Simple Mode**: Direct responses without tool calling
- **Tool Mode**: Focused tool execution
- **Agent Mode**: Full agentic capabilities with planning
- **Subagent Mode**: Delegated specialized tasks

## Mode Switching Triggers

### Automatic Switching
- **Complexity Detection**: Simple queries → Simple Mode, complex tasks → Agent Mode
- **Context Availability**: Rich context → Full Context mode
- **Tool Requirements**: Tasks requiring tools → Tool Mode
- **User Intent**: Explicit mode requests

### Manual Switching
- User commands to change mode
- Configuration settings
- Extension settings

## Mode Transition Logic

### From Simple to Agent
When a simple response isn't sufficient:
1. Detect need for tools or multi-step reasoning
2. Switch to Agent Mode
3. Initialize tool calling loop
4. Continue with enhanced capabilities

### From Agent to Subagent
When specialized expertise needed:
1. Identify subtask requiring different agent
2. Spawn subagent with specific intent
3. Delegate task execution
4. Await results and integrate

### Context Mode Switching
- **Partial Context**: For iterative workflows
  - Allows follow-up tool calls
  - Shorter initial responses
  - Enables conversation flow

- **Full Context**: For comprehensive tasks
  - Complete information gathering
  - One-shot responses
  - Maximizes context utilization

## Mode-Specific Behaviors

### Simple Mode
- Direct model responses
- No tool calling
- Fast response times
- Limited context

### Tool Mode
- Focused on tool execution
- Minimal reasoning overhead
- Efficient for known operations
- Limited planning capabilities

### Agent Mode
- Full reasoning and planning
- Multi-step task execution
- Tool orchestration
- Complex decision making

### Subagent Mode
- Specialized task handling
- Independent execution context
- Parallel processing capability
- Domain-specific expertise

## Mode Configuration

### Per-Agent Settings
Different agents have different mode preferences:
- Editor agent: Prefers Full Context for comprehensive edits
- Terminal agent: Uses Partial Context for iterative commands
- Search agent: Full Context for complete results

### Dynamic Adaptation
- Learning from user interactions
- Context-aware mode selection
- Performance-based switching

## Implementation Details

### Mode State Management
- Current mode tracking
- Transition history
- Context preservation across modes

### Mode Validation
- Capability checking
- Context requirements
- Tool availability verification

### Mode Recovery
- Fallback to simpler modes on failure
- Graceful degradation
- Error recovery strategies

## Performance Implications

### Mode Overhead
- Simple Mode: Minimal overhead
- Tool Mode: Tool execution overhead
- Agent Mode: Reasoning and planning overhead
- Subagent Mode: Coordination overhead

### Optimization Strategies
- Lazy mode switching
- Mode caching
- Parallel execution in subagent mode

## User Experience

### Transparency
- Mode indication in UI
- Transition notifications
- Progress feedback

### Control
- Manual mode override
- Mode preference settings
- Feedback mechanisms</content>
<parameter name="filePath">/Users/solosolar/Desktop/Code Projects/aicoder/BetaR2/IDE/CopilotChatToolCore/AgentCore/ModeSwitching.md