/**
 * Register Enhanced Navigation Tools
 * Registers the enhanced implementations with the tool service
 */

const { toolService } = require('./toolInvocationService');
const { 
  EnhancedLs, 
  EnhancedFindFiles, 
  EnhancedGlob, 
  EnhancedSemanticSearch 
} = require('./enhancedNavigationTools');

// Get workspace root from environment or current directory
const getWorkspaceRoot = () => {
  // Try to find workspace root by looking for common markers
  const fs = require('fs');
  const path = require('path');
  
  let currentDir = process.cwd();
  const maxDepth = 10;
  let depth = 0;
  
  while (depth < maxDepth) {
    // Check for workspace markers
    const markers = [
      'package.json',
      '.git',
      'tsconfig.json',
      'jsconfig.json',
      '.vscode',
      'Cargo.toml',
      'go.mod',
      'requirements.txt',
      'pom.xml'
    ];
    
    for (const marker of markers) {
      const markerPath = path.join(currentDir, marker);
      if (fs.existsSync(markerPath)) {
        return currentDir;
      }
    }
    
    const parentDir = path.dirname(currentDir);
    if (parentDir === currentDir) {
      // Reached filesystem root
      break;
    }
    
    currentDir = parentDir;
    depth++;
  }
  
  // Fallback to current working directory
  return process.cwd();
};

// Initialize enhanced tool instances
const workspaceRoot = getWorkspaceRoot();
const enhancedLs = new EnhancedLs(workspaceRoot);
const enhancedFindFiles = new EnhancedFindFiles(workspaceRoot);
const enhancedGlob = new EnhancedGlob(workspaceRoot);
const enhancedSemanticSearch = new EnhancedSemanticSearch(workspaceRoot);

/**
 * Enhanced ls executor
 */
async function executeEnhancedLs(toolName, params) {
  console.log(`[Enhanced LS] Executing with params:`, params);
  
  try {
    const result = await enhancedLs.execute(params);
    
    if (result.success) {
      console.log(`[Enhanced LS] Found ${result.entriesCount} entries in ${result.executionTime}ms`);
      
      // Format result for tool system
      return {
        success: true,
        base_path: result.base_path,
        entries: result.entries,
        entriesCount: result.entriesCount,
        max_depth: result.max_depth,
        summary: {
          total_items: result.entriesCount,
          directories: result.summary.directories,
          files: result.summary.files,
          truncated: result.truncated,
          execution_time_ms: result.executionTime
        }
      };
    } else {
      console.error(`[Enhanced LS] Error:`, result.error);
      return {
        success: false,
        error: result.error,
        base_path: result.base_path,
        entriesCount: 0
      };
    }
  } catch (error) {
    console.error(`[Enhanced LS] Unexpected error:`, error);
    return {
      success: false,
      error: error.message,
      entriesCount: 0
    };
  }
}

/**
 * Enhanced find_files executor
 */
async function executeEnhancedFindFiles(toolName, params) {
  console.log(`[Enhanced Find Files] Executing with params:`, params);
  
  try {
    const result = await enhancedFindFiles.execute(params);
    
    if (result.success) {
      console.log(`[Enhanced Find Files] Found ${result.totalFound} files in ${result.executionTime}ms`);
      
      return {
        success: true,
        files: result.files,
        patterns: result.patterns,
        totalFound: result.totalFound,
        searchDir: result.searchDir,
        truncated: result.truncated,
        summary: {
          total_matches: result.totalFound,
          returned_matches: result.files.length,
          execution_time_ms: result.executionTime,
          search_directory: result.searchDir
        }
      };
    } else {
      console.error(`[Enhanced Find Files] Error:`, result.error);
      return {
        success: false,
        error: result.error,
        files: [],
        patterns: result.patterns
      };
    }
  } catch (error) {
    console.error(`[Enhanced Find Files] Unexpected error:`, error);
    return {
      success: false,
      error: error.message,
      files: []
    };
  }
}

/**
 * Enhanced glob executor
 */
async function executeEnhancedGlob(toolName, params) {
  console.log(`[Enhanced Glob] Executing with params:`, params);
  
  try {
    const result = await enhancedGlob.execute(params);
    
    if (result.success) {
      console.log(`[Enhanced Glob] Found ${result.totalMatches} matches in ${result.executionTime}ms`);
      
      return {
        success: true,
        matches: result.matches,
        pattern: result.pattern,
        basePath: result.basePath,
        totalMatches: result.totalMatches,
        summary: {
          total_matches: result.totalMatches,
          execution_time_ms: result.executionTime,
          base_path: result.basePath,
          pattern_used: result.pattern
        }
      };
    } else {
      console.error(`[Enhanced Glob] Error:`, result.error);
      return {
        success: false,
        error: result.error,
        matches: [],
        pattern: result.pattern
      };
    }
  } catch (error) {
    console.error(`[Enhanced Glob] Unexpected error:`, error);
    return {
      success: false,
      error: error.message,
      matches: []
    };
  }
}

/**
 * Enhanced semantic_search executor
 */
async function executeEnhancedSemanticSearch(toolName, params) {
  console.log(`[Enhanced Semantic Search] Executing with params:`, params);
  
  try {
    const result = await enhancedSemanticSearch.execute(params);
    
    if (result.success) {
      console.log(`[Enhanced Semantic Search] Found ${result.totalResults} results in ${result.executionTime}ms`);
      
      return {
        success: true,
        results: result.results,
        query: result.query,
        queries: result.queries,
        searchPaths: result.searchPaths,
        totalResults: result.totalResults,
        searchMode: result.searchMode,
        summary: {
          total_results: result.totalResults,
          execution_time_ms: result.executionTime,
          search_mode: result.searchMode,
          search_paths: result.searchPaths
        }
      };
    } else {
      console.error(`[Enhanced Semantic Search] Error:`, result.error);
      return {
        success: false,
        error: result.error,
        results: [],
        query: result.query
      };
    }
  } catch (error) {
    console.error(`[Enhanced Semantic Search] Unexpected error:`, error);
    return {
      success: false,
      error: error.message,
      results: []
    };
  }
}

/**
 * Register all enhanced navigation tools
 */
function registerEnhancedNavigationTools() {
  console.log(`[Enhanced Tools] Registering enhanced navigation tools with workspace root: ${workspaceRoot}`);
  
  // Register enhanced executors
  toolService.registerExecutor('ls', executeEnhancedLs);
  toolService.registerExecutor('find_files', executeEnhancedFindFiles);
  toolService.registerExecutor('glob', executeEnhancedGlob);
  toolService.registerExecutor('semantic_search', executeEnhancedSemanticSearch);
  
  console.log(`[Enhanced Tools] Successfully registered 4 enhanced navigation tools`);
  
  // Verify registration
  const registeredTools = ['ls', 'find_files', 'glob', 'semantic_search'];
  registeredTools.forEach(toolName => {
    const hasExecutor = toolService.executors && toolService.executors.has(toolName);
    console.log(`[Enhanced Tools] ${toolName}: ${hasExecutor ? '✅ registered' : '❌ not registered'}`);
  });
}

// Auto-register when module is loaded
registerEnhancedNavigationTools();

module.exports = {
  registerEnhancedNavigationTools,
  executeEnhancedLs,
  executeEnhancedFindFiles,
  executeEnhancedGlob,
  executeEnhancedSemanticSearch,
  workspaceRoot
};