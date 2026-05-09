/**
 * Enhanced Tool Renderer
 * Provides VSCode Copilot-style rendering for enhanced navigation tools
 * Includes anti-truncation measures and progressive result loading
 */

// Constants for result size management
const MAX_SAFE_RESULT_SIZE = 15000; // Characters
const MAX_DETAILS_ITEMS = 50; // Maximum items to show in details
const TRUNCATION_THRESHOLD = 10000; // When to start aggressive truncation

/**
 * Render enhanced ls results
 */
function renderEnhancedLs(result, toolName) {
  if (!result.success) {
    return {
      summary: `Directory listing failed: ${result.error}`,
      details: []
    };
  }

  const { base_path, entries, summary, multiPath, pathResults } = result;
  
  // Handle multi-path results
  if (multiPath && pathResults) {
    const successfulPaths = summary.pathResults.filter(p => p.success);
    const failedPaths = summary.pathResults.filter(p => !p.success);
    
    let summaryText = `Listed ${summary.total_items} items across ${successfulPaths.length} directories`;
    if (failedPaths.length > 0) {
      summaryText += ` (${failedPaths.length} failed)`;
    }
    
    const details = [];
    
    // Show results by path
    Object.keys(pathResults).forEach(pathKey => {
      const pathResult = pathResults[pathKey];
      if (pathResult.error) {
        details.push(`❌ ${pathKey}: ${pathResult.error}`);
      } else {
        const pathEntries = pathResult.entries || [];
        const dirs = pathEntries.filter(e => e.type === 'directory').length;
        const files = pathEntries.filter(e => e.type === 'file').length;
        details.push(`📁 ${pathKey}: ${dirs} directories, ${files} files`);
        
        // Show some entries
        pathEntries.slice(0, 5).forEach(entry => {
          const icon = entry.type === 'directory' ? '📁' : '📄';
          const sizeInfo = entry.size ? ` (${formatFileSize(entry.size)})` : '';
          details.push(`  ${icon} ${entry.name}${sizeInfo}`);
        });
        
        if (pathEntries.length > 5) {
          details.push(`  ... and ${pathEntries.length - 5} more items`);
        }
      }
      details.push(''); // Add spacing
    });
    
    if (result.truncated) {
      details.push(`⚠️ Results truncated - showing first ${summary.total_items} items across all directories`);
    }
    
    return {
      summary: summaryText,
      details: details,
      metadata: {
        executionTime: summary.execution_time_ms,
        totalItems: summary.total_items,
        truncated: result.truncated,
        multiPath: true,
        pathCount: Object.keys(pathResults).length
      }
    };
  }
  
  // Single path results (original logic)
  const displayPath = base_path || '.';
  const summaryText = `Listed ${summary.total_items} items in "${displayPath}" (${summary.directories} directories, ${summary.files} files)`;
  
  // Group entries by type for better presentation
  const directories = entries.filter(e => e.type === 'directory');
  const files = entries.filter(e => e.type === 'file');
  
  const details = [];
  
  // Add directories first
  if (directories.length > 0) {
    details.push('📁 Directories:');
    directories.forEach(dir => {
      const depthIndent = '  '.repeat(dir.depth || 0);
      details.push(`${depthIndent}${dir.name}/`);
    });
  }
  
  // Add files
  if (files.length > 0) {
    details.push('📄 Files:');
    files.forEach(file => {
      const depthIndent = '  '.repeat(file.depth || 0);
      const sizeInfo = file.size ? ` (${formatFileSize(file.size)})` : '';
      details.push(`${depthIndent}${file.name}${sizeInfo}`);
    });
  }
  
  if (result.truncated) {
    details.push(`\n⚠️ Results truncated - showing first ${summary.total_items} items`);
    details.push(`💡 Use smaller max_depth or more specific path to see more results`);
  }
  
  return {
    summary: summaryText,
    details: details,
    metadata: {
      executionTime: summary.execution_time_ms,
      totalItems: summary.total_items,
      truncated: result.truncated
    }
  };
}

/**
 * Render enhanced find_files results with anti-truncation measures
 */
function renderEnhancedFindFiles(result, toolName) {
  if (!result.success) {
    return {
      summary: `File search failed: ${result.error}`,
      details: []
    };
  }

  const { files, patterns, totalFound, contentFiltered, excludePatterns, searchDir } = result;
  const displaySearchDir = searchDir || '.';
  
  // Create summary line
  let summaryText = `Found ${totalFound || files.length} files matching patterns in "${displaySearchDir}"`;
  if (contentFiltered) {
    summaryText += ' (content filtered)';
  }
  
  const details = [];
  
  // Show search patterns (always include this)
  if (patterns && patterns.length > 0) {
    details.push('🔍 Search patterns:');
    patterns.forEach(pattern => {
      details.push(`  ${pattern}`);
    });
    
    if (excludePatterns && excludePatterns.length > 0) {
      details.push('🚫 Excluded patterns:');
      excludePatterns.forEach(pattern => {
        details.push(`  ${pattern}`);
      });
    }
    
    details.push('');
  }
  
  // Anti-truncation: Limit the number of files shown
  const maxFilesToShow = Math.min(files.length, MAX_DETAILS_ITEMS);
  const filesToShow = files.slice(0, maxFilesToShow);
  
  // Group files by directory for better organization
  const filesByDir = {};
  filesToShow.forEach(file => {
    const dir = file.directory || '.';
    if (!filesByDir[dir]) {
      filesByDir[dir] = [];
    }
    filesByDir[dir].push(file);
  });
  
  // Render grouped results with size control
  const sortedDirs = Object.keys(filesByDir).sort();
  let currentSize = 0;
  
  for (const dir of sortedDirs) {
    if (sortedDirs.length > 1) {
      const dirLine = `📁 ${dir}/`;
      details.push(dirLine);
      currentSize += dirLine.length;
    }
    
    const dirFiles = filesByDir[dir];
    for (const file of dirFiles) {
      const indent = sortedDirs.length > 1 ? '  ' : '';
      const sizeInfo = file.size ? ` (${formatFileSize(file.size)})` : '';
      const modifiedInfo = file.modified ? ` - ${formatDate(file.modified)}` : '';
      const contentIcon = file.contentMatched ? '🔍 ' : '📄 ';
      const fileLine = `${indent}${contentIcon}${file.fileName}${sizeInfo}${modifiedInfo}`;
      
      // Check if adding this line would exceed safe size
      if (currentSize + fileLine.length > MAX_SAFE_RESULT_SIZE) {
        const remaining = files.length - details.length + (sortedDirs.length > 1 ? sortedDirs.length : 0);
        details.push(`\n⚠️ Result truncated for display - ${remaining} more files available`);
        details.push(`💡 Use more specific patterns or smaller max_matches to see complete results`);
        break;
      }
      
      details.push(fileLine);
      currentSize += fileLine.length;
    }
    
    if (sortedDirs.length > 1 && currentSize < MAX_SAFE_RESULT_SIZE) {
      details.push('');
    }
    
    // Break outer loop if we hit size limit
    if (currentSize > MAX_SAFE_RESULT_SIZE) {
      break;
    }
  }
  
  // Add truncation info if needed
  if (files.length > maxFilesToShow) {
    const remaining = files.length - maxFilesToShow;
    details.push(`\n📊 Showing ${maxFilesToShow} of ${files.length} files (${remaining} more available)`);
    details.push(`💡 Use more specific patterns to reduce result set`);
  }
  
  return {
    summary: summaryText,
    details: details,
    metadata: {
      executionTime: result.executionTime,
      totalMatches: totalFound || files.length,
      returnedMatches: files.length,
      displayedMatches: maxFilesToShow,
      contentFiltered: contentFiltered,
      truncatedForDisplay: files.length > maxFilesToShow || currentSize > MAX_SAFE_RESULT_SIZE
    }
  };
}

/**
 * Render enhanced glob results with anti-truncation measures
 */
function renderEnhancedGlob(result, toolName) {
  if (!result.success) {
    return {
      summary: `Glob pattern matching failed: ${result.error}`,
      details: []
    };
  }

  const { matches, pattern, totalMatches, basePath, excludedNodeModules, excludePatterns } = result;
  const displayBasePath = basePath || '.';
  
  // Create summary line with exclusion info
  let summaryText = `Pattern "${pattern}" matched ${totalMatches} items in "${displayBasePath}"`;
  if (excludedNodeModules) {
    summaryText += ' (excluding node_modules)';
  }
  
  const details = [];
  
  // Show exclusion info
  if (excludedNodeModules || (excludePatterns && excludePatterns.length > 0)) {
    details.push('🚫 Excluded patterns:');
    if (excludedNodeModules) {
      details.push('  node_modules/**');
    }
    if (excludePatterns && excludePatterns.length > 0) {
      excludePatterns.forEach(pattern => {
        details.push(`  ${pattern}`);
      });
    }
    details.push('');
  }
  
  if (matches.length === 0) {
    details.push('No matches found');
    if (excludedNodeModules) {
      details.push('💡 Try setting exclude_node_modules: false to include node_modules');
    }
    return {
      summary: summaryText,
      details: details,
      metadata: {
        executionTime: result.executionTime,
        totalMatches: 0
      }
    };
  }
  
  // Anti-truncation: Limit matches shown
  const maxMatchesToShow = Math.min(matches.length, MAX_DETAILS_ITEMS);
  const matchesToShow = matches.slice(0, maxMatchesToShow);
  
  // Group by type
  const directories = matchesToShow.filter(m => m.type === 'directory');
  const files = matchesToShow.filter(m => m.type === 'file');
  
  let currentSize = 0;
  
  // Show directories first
  if (directories.length > 0) {
    const dirHeader = '📁 Directories:';
    details.push(dirHeader);
    currentSize += dirHeader.length;
    
    for (const dir of directories) {
      const dirLine = `  ${dir.name}/`;
      if (currentSize + dirLine.length > MAX_SAFE_RESULT_SIZE) {
        details.push(`  ... (more directories truncated for display)`);
        break;
      }
      details.push(dirLine);
      currentSize += dirLine.length;
    }
  }
  
  // Show files
  if (files.length > 0) {
    const fileHeader = '📄 Files:';
    details.push(fileHeader);
    currentSize += fileHeader.length;
    
    for (const file of files) {
      const sizeInfo = file.size ? ` (${formatFileSize(file.size)})` : '';
      const modifiedInfo = file.modified ? ` - ${formatDate(file.modified)}` : '';
      const fileLine = `  ${file.name}${sizeInfo}${modifiedInfo}`;
      
      if (currentSize + fileLine.length > MAX_SAFE_RESULT_SIZE) {
        details.push(`  ... (more files truncated for display)`);
        break;
      }
      
      details.push(fileLine);
      currentSize += fileLine.length;
    }
  }
  
  // Add truncation info if needed
  if (matches.length > maxMatchesToShow) {
    const remaining = matches.length - maxMatchesToShow;
    details.push(`\n📊 Showing ${maxMatchesToShow} of ${matches.length} matches (${remaining} more available)`);
    details.push(`💡 Use more specific pattern to reduce result set`);
  }
  
  return {
    summary: summaryText,
    details: details,
    metadata: {
      executionTime: result.executionTime,
      totalMatches: totalMatches,
      displayedMatches: maxMatchesToShow,
      pattern: pattern,
      excludedNodeModules: excludedNodeModules,
      truncatedForDisplay: matches.length > maxMatchesToShow || currentSize > MAX_SAFE_RESULT_SIZE
    }
  };
}

/**
 * Render enhanced semantic_search results with anti-truncation measures
 */
function renderEnhancedSemanticSearch(result, toolName) {
  if (!result.success) {
    return {
      summary: `Semantic search failed: ${result.error}`,
      details: []
    };
  }

  const { results, query, totalResults, hasMore, pagination, searchMode } = result;
  const displaySearchMode = searchMode || 'progressive';
  
  // Create summary line with pagination info
  let summaryText = `Found ${totalResults || results.length} results for "${query}" using ${displaySearchMode} search`;
  if (hasMore && pagination) {
    summaryText += ` (page ${pagination.page}/${pagination.totalPages})`;
  }
  
  const details = [];
  
  if (results.length === 0) {
    details.push('No matches found');
    details.push('💡 Try:');
    details.push('  • Using broader search terms');
    details.push('  • Checking file types with find_files');
    details.push('  • Using different search mode');
    
    return {
      summary: summaryText,
      details: details,
      metadata: {
        executionTime: result.executionTime,
        totalResults: 0,
        searchMode: displaySearchMode
      }
    };
  }
  
  // Anti-truncation: Limit results and track size
  const maxResultsToShow = Math.min(results.length, MAX_DETAILS_ITEMS);
  const resultsToShow = results.slice(0, maxResultsToShow);
  
  // Group results by file for better presentation
  const resultsByFile = {};
  resultsToShow.forEach(result => {
    if (!resultsByFile[result.file]) {
      resultsByFile[result.file] = [];
    }
    resultsByFile[result.file].push(result);
  });
  
  // Render grouped results with size control
  const sortedFiles = Object.keys(resultsByFile).sort();
  let currentSize = 0;
  
  for (const file of sortedFiles) {
    const fileResults = resultsByFile[file];
    const topScore = Math.max(...fileResults.map(r => r.score));
    
    const fileLine = `📄 ${file} (relevance: ${(topScore * 100).toFixed(1)}%)`;
    
    // Check size limits
    if (currentSize + fileLine.length > MAX_SAFE_RESULT_SIZE) {
      const remainingFiles = sortedFiles.length - details.filter(d => d.startsWith('📄')).length;
      details.push(`\n⚠️ Result truncated for display - ${remainingFiles} more files with matches`);
      details.push(`💡 Use more specific query or smaller max_results to see complete results`);
      break;
    }
    
    details.push(fileLine);
    currentSize += fileLine.length;
    
    // Show top matches from this file (limited)
    const topMatches = fileResults
      .sort((a, b) => b.score - a.score)
      .slice(0, 2); // Show max 2 matches per file to prevent truncation
    
    for (const match of topMatches) {
      const matchText = match.matchedText.substring(0, 60) + (match.matchedText.length > 60 ? '...' : '');
      const matchLine = `  Line ${match.line}: ${matchText}`;
      
      if (currentSize + matchLine.length > MAX_SAFE_RESULT_SIZE) {
        details.push(`  ... (more matches truncated for display)`);
        break;
      }
      
      details.push(matchLine);
      currentSize += matchLine.length;
      
      // Show limited snippet
      if (match.snippet && currentSize < TRUNCATION_THRESHOLD) {
        const snippetLines = match.snippet.split('\n').slice(0, 2); // Show max 2 lines of snippet
        for (const line of snippetLines) {
          const snippetLine = `    ${line.substring(0, 80)}${line.length > 80 ? '...' : ''}`;
          if (currentSize + snippetLine.length > MAX_SAFE_RESULT_SIZE) {
            break;
          }
          details.push(snippetLine);
          currentSize += snippetLine.length;
        }
      }
    }
    
    if (fileResults.length > 2) {
      details.push(`  ... and ${fileResults.length - 2} more matches in this file`);
    }
    
    details.push('');
    
    // Break if we're approaching size limits
    if (currentSize > TRUNCATION_THRESHOLD) {
      break;
    }
  }
  
  // Add pagination info if there are more results
  if (results.length > maxResultsToShow || hasMore) {
    const totalAvailable = totalResults || results.length;
    details.push(`📊 Showing ${Math.min(maxResultsToShow, results.length)} of ${totalAvailable} total results`);
    details.push(`💡 Use more specific query to reduce result set`);
    if (pagination && pagination.hasNextPage) {
      details.push(`⏭️ ${totalAvailable - results.length} more results available`);
    }
  }
  
  return {
    summary: summaryText,
    details: details,
    metadata: {
      executionTime: result.executionTime,
      totalResults: totalResults || results.length,
      returnedResults: results.length,
      displayedResults: maxResultsToShow,
      searchMode: displaySearchMode,
      query: query,
      hasMore: hasMore,
      pagination: pagination,
      truncatedForDisplay: results.length > maxResultsToShow || currentSize > TRUNCATION_THRESHOLD
    }
  };
}

/**
 * Utility functions
 */
function formatFileSize(bytes) {
  if (bytes === 0) return '0 B';
  
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function formatDate(dateString) {
  try {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now - date;
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    
    if (diffDays === 0) {
      return 'today';
    } else if (diffDays === 1) {
      return 'yesterday';
    } else if (diffDays < 7) {
      return `${diffDays} days ago`;
    } else {
      return date.toLocaleDateString();
    }
  } catch (error) {
    return dateString;
  }
}

/**
 * Main enhanced renderer function
 */
function renderEnhancedToolResult(toolName, result) {
  const normalizedName = toolName.toLowerCase();
  
  switch (normalizedName) {
    case 'ls':
      return renderEnhancedLs(result, toolName);
    
    case 'find_files':
      return renderEnhancedFindFiles(result, toolName);
    
    case 'glob':
      return renderEnhancedGlob(result, toolName);
    
    case 'semantic_search':
      return renderEnhancedSemanticSearch(result, toolName);
    
    default:
      // Fallback for non-enhanced tools
      return {
        summary: `${toolName} completed`,
        details: [JSON.stringify(result, null, 2)],
        metadata: {}
      };
  }
}

module.exports = {
  renderEnhancedToolResult,
  renderEnhancedLs,
  renderEnhancedFindFiles,
  renderEnhancedGlob,
  renderEnhancedSemanticSearch,
  formatFileSize,
  formatDate
};