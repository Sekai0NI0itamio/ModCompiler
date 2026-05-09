# Copilot Chat Tool Core

This directory contains exact copies of all Copilot Chat tools (except `apply_patch` which has been implemented separately) along with detailed investigation reports of the agentic system.

## Structure

### AgentCore/
Detailed documentation of Copilot's agentic system architecture:

- **AgenticSystemOverview.md**: High-level overview of the agent system
- **RequestHandling.md**: How requests are processed and routed
- **ModeSwitching.md**: Different modes and when they're activated
- **AgentTypes.md**: Different agent types and their capabilities
- **ToolCallingLoop.md**: The core tool execution loop mechanism
- **Subagents.md**: How subagents work and are coordinated
- **ExampleResponse.md**: Complete example of a request-response cycle

### Tools/
Complete implementations of all Copilot Chat tools:

#### Core Tools
- `codebaseTool.tsx` - Semantic code search
- `readFileTool.tsx` - File reading with intelligent chunking
- `createFileTool.tsx` - File creation
- `replaceStringTool.tsx` - String replacement with context matching
- `multiReplaceStringTool.tsx` - Multiple string replacements
- `listDirTool.tsx` - Directory listing
- `createDirectoryTool.tsx` - Directory creation

#### Search & Discovery
- `findFilesTool.tsx` - File finding with patterns
- `findTextInFilesTool.tsx` - Text search across files
- `searchWorkspaceSymbolsTool.tsx` - Symbol search in workspace
- `usagesTool.tsx` - Find usages of symbols

#### Development Workflow
- `getErrorsTool.tsx` - Error and diagnostic retrieval
- `scmChangesTool.tsx` - Git change tracking
- `manageTodoListTool.tsx` - TODO list management
- `memoryTool.tsx` - Conversation memory management

#### Testing
- `testFailureTool.tsx` - Test failure analysis
- `findTestsFilesTool.tsx` - Test file discovery

#### Jupyter Notebook
- `newNotebookTool.tsx` - Notebook creation
- `editNotebookTool.tsx` - Notebook editing
- `runNotebookCellTool.tsx` - Cell execution
- `notebookSummaryTool.tsx` - Notebook analysis
- `getNotebookCellOutputTool.tsx` - Cell output retrieval

#### VS Code Integration
- `vscodeAPITool.ts` - VS Code API documentation
- `vscodeCmdTool.tsx` - VS Code command execution
- `installExtensionTool.tsx` - Extension management
- `getSearchViewResultsTool.tsx` - Search view integration

#### Web & External
- `githubRepoTool.tsx` - GitHub repository operations
- `simpleBrowserTool.tsx` - Web browsing
- `fetchWebPageTool.tsx` - Web page fetching

#### Specialized
- `docTool.tsx` - Documentation generation
- `readProjectStructureTool.ts` - Project structure analysis
- `toolReplayTool.tsx` - Tool execution replay
- `searchSubagentTool.ts` - Search subagent spawning

### Common/
Shared infrastructure:
- `toolsRegistry.ts` - Tool registration system
- `toolNames.ts` - Tool name definitions and categories
- `toolUtils.ts` - Utility functions
- `toolsService.ts` - Tool service management

## Implementation Notes

These tools are copied directly from the VS Code Copilot Chat extension and will require adaptation to work in the AI Coding IDE environment:

1. **VS Code API Dependencies**: Replace `vscode` imports with IDE equivalents
2. **Extension Context**: Adapt to the IDE's plugin architecture
3. **UI Integration**: Update UI rendering for the IDE's interface
4. **Authentication**: Handle API keys and authentication differently
5. **File System**: Use the IDE's file system abstraction

## Agentic System Investigation

The AgentCore documentation provides comprehensive analysis of:

- **Request Processing**: How user queries are analyzed and routed
- **Mode Switching**: When and why the system changes operational modes
- **Tool Calling Loop**: The iterative process of tool execution and decision making
- **Subagent Coordination**: How specialized agents are spawned and managed
- **Response Generation**: How final answers are constructed from tool results

## Next Steps

1. **Adaptation**: Modify each tool to work with the IDE's APIs
2. **Integration**: Connect tools to the IDE's tool execution system
3. **Testing**: Validate tool functionality in the IDE environment
4. **Optimization**: Tune performance for the IDE's use cases

## Key Findings from Investigation

- Copilot uses a sophisticated multi-agent system with specialized agents for different tasks
- The tool calling loop enables complex multi-step operations through iterative tool execution
- Subagents allow for parallel processing and domain specialization
- Context management and mode switching are critical for efficient operation
- The system balances between providing comprehensive results and maintaining responsiveness</content>
<parameter name="filePath">/Users/solosolar/Desktop/Code Projects/aicoder/BetaR2/IDE/CopilotChatToolCore/README.md