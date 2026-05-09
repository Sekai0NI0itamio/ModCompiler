/**
 * Enhanced Navigation Tools Implementation
 * Implements VSCode Copilot-style execution logic for ls, find_files, glob, and semantic_search
 */

const fs = require('fs').promises;
const path = require('path');
const { glob: nodeGlob } = require('glob');
const picomatch = require('picomatch');

/**
 * Workspace-aware path resolution
 */
class WorkspacePathResolver {
  constructor(workspaceRoot = process.cwd()) {
    this.workspaceRoot = workspaceRoot;
  }

  resolve(inputPath) {
    if (!inputPath) return this.workspaceRoot;
    
    // Handle absolute paths
    if (path.isAbsolute(inputPath)) {
      return inputPath;
    }
    
    // Handle workspace-relative paths
    return path.resolve(this.workspaceRoot, inputPath);
  }

  relativeTo(fullPath) {
    return path.relative(this.workspaceRoot, fullPath);
  }

  isWithinWorkspace(fullPath) {
    const relative = path.relative(this.workspaceRoot, fullPath);
    return !relative.startsWith('..') && !path.isAbsolute(relative);
  }
}

/**
 * Intelligent file filtering
 */
class IntelligentFilter {
  constructor() {
    this.defaultArtifactPatterns = [
      'node_modules/**',
      '.git/**',
      'dist/**',
      'build/**',
      'coverage/**',
      '.nyc_output/**',
      '*.log',
      '.DS_Store',
      'Thumbs.db',
      '*.tmp',
      '*.temp',
      '.cache/**',
      '.vscode/**',
      '.idea/**'
    ];
  }

  shouldExclude(filePath, options = {}) {
    const { filterArtifacts = true, showHidden = false, customPatterns = [] } = options;
    
    const basename = path.basename(filePath);
    
    // Hidden files
    if (!showHidden && basename.startsWith('.')) {
      return true;
    }
    
    // Artifact filtering
    if (filterArtifacts) {
      const patterns = [...this.defaultArtifactPatterns, ...customPatterns];
      return patterns.some(pattern => picomatch(pattern)(filePath));
    }
    
    return false;
  }

  filterResults(results, options = {}) {
    return results.filter(result => !this.shouldExclude(result.path, options));
  }
}

/**
 * Enhanced ls implementation
 */
class EnhancedLs {
  constructor(workspaceRoot) {
    this.pathResolver = new WorkspacePathResolver(workspaceRoot);
    this.filter = new IntelligentFilter();
  }

  async execute(params) {
    const {
      path: inputPath = '.',
      paths: inputPaths = null, // New: support multiple directories
      max_depth = 2,
      show_hidden = false,
      include_metadata = true,
      filter_artifacts = true,
      max_results = 100
    } = params;

    const startTime = Date.now();
    
    // Handle multiple paths
    const pathsToProcess = inputPaths ? inputPaths : [inputPath];
    const allResults = [];
    const pathResults = {};
    
    try {
      for (const singlePath of pathsToProcess) {
        const resolvedPath = this.pathResolver.resolve(singlePath);
        
        // Validate path exists
        const stat = await fs.stat(resolvedPath);
        if (!stat.isDirectory()) {
          pathResults[singlePath] = {
            error: `Path is not a directory: ${singlePath}`,
            entries: []
          };
          continue;
        }

        const results = await this.listRecursive(resolvedPath, {
          maxDepth: Math.min(max_depth, 5),
          showHidden: show_hidden,
          includeMetadata: include_metadata,
          filterArtifacts: filter_artifacts,
          maxResults: max_results,
          currentDepth: 0
        });

        const filteredResults = this.filter.filterResults(results, {
          filterArtifacts: filter_artifacts,
          showHidden: show_hidden
        });

        // Sort by type (directories first) then by name
        filteredResults.sort((a, b) => {
          if (a.type !== b.type) {
            return a.type === 'directory' ? -1 : 1;
          }
          return a.name.localeCompare(b.name);
        });

        pathResults[singlePath] = {
          entries: filteredResults,
          basePath: this.pathResolver.relativeTo(resolvedPath)
        };
        
        allResults.push(...filteredResults);
      }

      // Limit total results across all paths
      const limitedResults = allResults.slice(0, max_results);
      const truncated = allResults.length > max_results;

      // Calculate summary statistics
      const summary = {
        directories: limitedResults.filter(r => r.type === 'directory').length,
        files: limitedResults.filter(r => r.type === 'file').length,
        hidden: show_hidden ? limitedResults.filter(r => r.name.startsWith('.')).length : 0,
        totalPaths: pathsToProcess.length,
        pathResults: Object.keys(pathResults).map(path => ({
          path: path,
          success: !pathResults[path].error,
          count: pathResults[path].entries ? pathResults[path].entries.length : 0,
          error: pathResults[path].error
        }))
      };

      return {
        success: true,
        base_path: pathsToProcess.length === 1 ? pathResults[pathsToProcess[0]].basePath : 'multiple',
        entries: limitedResults,
        entriesCount: limitedResults.length,
        totalFound: allResults.length,
        max_depth: max_depth,
        truncated,
        executionTime: Date.now() - startTime,
        summary: {
          ...summary,
          total_items: limitedResults.length,
          execution_time_ms: Date.now() - startTime
        },
        // Multi-path specific data
        multiPath: pathsToProcess.length > 1,
        pathResults: pathResults
      };

    } catch (error) {
      return {
        success: false,
        error: error.message,
        base_path: pathsToProcess.length === 1 ? this.pathResolver.relativeTo(this.pathResolver.resolve(pathsToProcess[0])) : 'multiple',
        entriesCount: 0
      };
    }
  }

  async listRecursive(dirPath, options) {
    const { maxDepth, currentDepth, includeMetadata } = options;
    const results = [];

    if (currentDepth >= maxDepth) {
      return results;
    }

    try {
      const entries = await fs.readdir(dirPath);
      
      for (const entry of entries) {
        const fullPath = path.join(dirPath, entry);
        const relativePath = this.pathResolver.relativeTo(fullPath);
        
        try {
          const stat = await fs.stat(fullPath);
          const isDirectory = stat.isDirectory();
          
          const result = {
            name: entry,
            path: relativePath,
            type: isDirectory ? 'directory' : 'file',
            depth: currentDepth
          };

          if (includeMetadata) {
            result.size = isDirectory ? null : stat.size;
            result.modified = stat.mtime.toISOString();
            result.permissions = stat.mode.toString(8).slice(-3);
          }

          results.push(result);

          // Recurse into directories
          if (isDirectory && currentDepth < maxDepth - 1) {
            const subResults = await this.listRecursive(fullPath, {
              ...options,
              currentDepth: currentDepth + 1
            });
            results.push(...subResults);
          }
        } catch (statError) {
          // Skip entries we can't stat (permission issues, broken symlinks, etc.)
          continue;
        }
      }
    } catch (readdirError) {
      // Directory not readable
      throw readdirError;
    }

    return results;
  }
}

/**
 * Enhanced find_files implementation
 */
class EnhancedFindFiles {
  constructor(workspaceRoot) {
    this.pathResolver = new WorkspacePathResolver(workspaceRoot);
    this.filter = new IntelligentFilter();
  }

  async execute(params) {
    const {
      search_dir = '.',
      patterns,
      max_matches = 50,
      max_depth = 10,
      min_depth = 0,
      include_hidden = false,
      sort_by = 'relevance',
      file_types = [],
      content_filter = null, // New: search for files containing specific text
      exclude_patterns = [] // New: patterns to exclude
    } = params;

    const startTime = Date.now();
    const resolvedSearchDir = this.pathResolver.resolve(search_dir);
    
    try {
      // Validate search directory
      const stat = await fs.stat(resolvedSearchDir);
      if (!stat.isDirectory()) {
        throw new Error(`Search path is not a directory: ${search_dir}`);
      }

      // Enhance patterns for better matching
      const enhancedPatterns = this.enhancePatterns(patterns);
      
      // Execute search with timeout
      const results = await Promise.race([
        this.searchWithPatterns(resolvedSearchDir, enhancedPatterns, {
          maxDepth: max_depth,
          minDepth: min_depth,
          includeHidden: include_hidden,
          fileTypes: file_types,
          maxMatches: max_matches,
          contentFilter: content_filter,
          excludePatterns: exclude_patterns
        }),
        new Promise((_, reject) => 
          setTimeout(() => reject(new Error('Search timeout after 20 seconds')), 20000)
        )
      ]);

      // Sort results
      const sortedResults = this.sortResults(results, sort_by);
      const limitedResults = sortedResults.slice(0, max_matches);

      return {
        success: true,
        files: limitedResults,
        patterns: enhancedPatterns,
        totalFound: results.length,
        searchDir: this.pathResolver.relativeTo(resolvedSearchDir),
        executionTime: Date.now() - startTime,
        truncated: results.length > max_matches,
        contentFiltered: !!content_filter,
        excludePatterns: exclude_patterns
      };

    } catch (error) {
      return {
        success: false,
        error: error.message,
        patterns: patterns,
        searchDir: this.pathResolver.relativeTo(resolvedSearchDir),
        files: []
      };
    }
  }

  enhancePatterns(patterns) {
    return patterns.map(pattern => {
      // Auto-append **/ for better matching (GPT-4.1 compatibility)
      if (!pattern.startsWith('**/') && !pattern.startsWith('/') && !path.isAbsolute(pattern)) {
        return `**/${pattern}`;
      }
      
      // Handle directory patterns
      if (pattern.endsWith('/')) {
        return `${pattern}**`;
      }
      
      return pattern;
    });
  }

  async searchWithPatterns(searchDir, patterns, options) {
    const results = [];
    const seen = new Set(); // Track seen files to prevent duplicates
    const { maxDepth, minDepth, includeHidden, fileTypes, maxMatches, contentFilter, excludePatterns } = options;
    
    for (const pattern of patterns) {
      try {
        // Build ignore patterns including exclude patterns
        const ignorePatterns = includeHidden ? [] : ['.*/**'];
        if (excludePatterns && excludePatterns.length > 0) {
          ignorePatterns.push(...excludePatterns);
        }
        
        const matches = await nodeGlob(pattern, {
          cwd: searchDir,
          absolute: false,
          dot: includeHidden,
          maxDepth: maxDepth,
          ignore: ignorePatterns,
          nodir: true // Only files
        });

        for (const match of matches) {
          if (results.length >= maxMatches) break;
          
          const fullPath = path.join(searchDir, match);
          const relativePath = this.pathResolver.relativeTo(fullPath);
          
          // Skip duplicates using absolute path as key
          const absolutePath = path.resolve(fullPath);
          if (seen.has(absolutePath)) {
            continue;
          }
          seen.add(absolutePath);
          
          const depth = match.split(path.sep).length - 1;
          
          // Apply depth filtering
          if (depth < minDepth) continue;
          
          // Apply file type filtering
          if (fileTypes.length > 0) {
            const ext = path.extname(match).slice(1).toLowerCase();
            if (!fileTypes.includes(ext)) continue;
          }
          
          try {
            const stat = await fs.stat(fullPath);
            
            // Apply content filtering if specified
            let contentMatch = true;
            if (contentFilter) {
              try {
                const content = await fs.readFile(fullPath, 'utf8');
                contentMatch = content.toLowerCase().includes(contentFilter.toLowerCase());
              } catch (readError) {
                // Skip files we can't read for content filtering
                contentMatch = false;
              }
            }
            
            if (contentMatch) {
              results.push({
                fileName: path.basename(match),
                fullPath: relativePath,
                directory: path.dirname(relativePath),
                size: stat.size,
                modified: stat.mtime.toISOString(),
                extension: path.extname(match).slice(1),
                depth: depth,
                matchedPattern: pattern,
                contentMatched: !!contentFilter
              });
            }
          } catch (statError) {
            // Skip files we can't stat
            continue;
          }
        }
      } catch (globError) {
        // Skip invalid patterns
        continue;
      }
    }

    return results;
  }

  sortResults(results, sortBy) {
    switch (sortBy) {
      case 'name':
        return results.sort((a, b) => a.fileName.localeCompare(b.fileName));
      
      case 'modified':
        return results.sort((a, b) => new Date(b.modified) - new Date(a.modified));
      
      case 'size':
        return results.sort((a, b) => b.size - a.size);
      
      case 'relevance':
      default:
        // Sort by depth (shallower first), then by name
        return results.sort((a, b) => {
          if (a.depth !== b.depth) {
            return a.depth - b.depth;
          }
          return a.fileName.localeCompare(b.fileName);
        });
    }
  }
}

/**
 * Enhanced glob implementation
 */
class EnhancedGlob {
  constructor(workspaceRoot) {
    this.pathResolver = new WorkspacePathResolver(workspaceRoot);
    this.resultCache = new Map();
  }

  async execute(params) {
    const {
      path: basePath = '.',
      pattern,
      max_results = 50,
      include_directories = false,
      case_sensitive = null,
      follow_symlinks = false,
      exclude_node_modules = true, // New: exclude node_modules by default
      exclude_patterns = [] // New: additional patterns to exclude
    } = params;

    const startTime = Date.now();
    const resolvedBasePath = this.pathResolver.resolve(basePath);
    
    // Check cache first
    const cacheKey = JSON.stringify({ 
      resolvedBasePath, 
      pattern, 
      max_results, 
      include_directories, 
      exclude_node_modules,
      exclude_patterns 
    });
    if (this.resultCache.has(cacheKey)) {
      return this.resultCache.get(cacheKey);
    }

    try {
      // Validate pattern
      this.validatePattern(pattern);
      
      // Validate base path
      const stat = await fs.stat(resolvedBasePath);
      if (!stat.isDirectory()) {
        throw new Error(`Base path is not a directory: ${basePath}`);
      }

      // Execute glob with timeout
      const results = await Promise.race([
        this.executeGlob(resolvedBasePath, pattern, {
          maxResults: max_results,
          includeDirectories: include_directories,
          caseSensitive: case_sensitive,
          followSymlinks: follow_symlinks,
          excludeNodeModules: exclude_node_modules,
          excludePatterns: exclude_patterns
        }),
        new Promise((_, reject) => 
          setTimeout(() => reject(new Error('Glob timeout after 10 seconds')), 10000)
        )
      ]);

      const result = {
        success: true,
        matches: results,
        pattern: pattern,
        basePath: this.pathResolver.relativeTo(resolvedBasePath),
        totalMatches: results.length,
        executionTime: Date.now() - startTime,
        truncated: false,
        excludedNodeModules: exclude_node_modules,
        excludePatterns: exclude_patterns
      };

      // Cache result
      this.resultCache.set(cacheKey, result);
      
      // Clean cache if it gets too large
      if (this.resultCache.size > 100) {
        const firstKey = this.resultCache.keys().next().value;
        this.resultCache.delete(firstKey);
      }

      return result;

    } catch (error) {
      const result = {
        success: false,
        error: error.message,
        pattern: pattern,
        basePath: this.pathResolver.relativeTo(resolvedBasePath),
        matches: []
      };
      
      return result;
    }
  }

  validatePattern(pattern) {
    if (!pattern || typeof pattern !== 'string') {
      throw new Error('Pattern must be a non-empty string');
    }
    
    // Check for common pattern issues
    if (pattern.includes('***')) {
      throw new Error('Invalid pattern: too many consecutive wildcards');
    }
    
    // Validate bracket expressions
    const bracketMatches = pattern.match(/\[([^\]]*)\]/g);
    if (bracketMatches) {
      for (const match of bracketMatches) {
        if (match === '[]' || match === '[!]') {
          throw new Error('Invalid pattern: empty bracket expression');
        }
      }
    }
  }

  async executeGlob(basePath, pattern, options) {
    const { maxResults, includeDirectories, caseSensitive, followSymlinks, excludeNodeModules, excludePatterns } = options;
    
    const globOptions = {
      cwd: basePath,
      absolute: false,
      dot: false,
      nodir: !includeDirectories,
      follow: followSymlinks,
      nocase: caseSensitive === false
    };

    // Handle platform-specific case sensitivity
    if (caseSensitive === null) {
      globOptions.nocase = process.platform === 'win32' || process.platform === 'darwin';
    }

    // Build ignore patterns
    const ignorePatterns = [];
    
    // Add node_modules exclusion by default
    if (excludeNodeModules) {
      ignorePatterns.push('node_modules/**');
    }
    
    // Add custom exclude patterns
    if (excludePatterns && excludePatterns.length > 0) {
      ignorePatterns.push(...excludePatterns);
    }
    
    // Add ignore patterns to glob options
    if (ignorePatterns.length > 0) {
      globOptions.ignore = ignorePatterns;
    }

    const matches = await nodeGlob(pattern, globOptions);
    const results = [];
    const seen = new Set(); // Track seen files to prevent duplicates

    for (const match of matches.slice(0, maxResults)) {
      const fullPath = path.join(basePath, match);
      const absolutePath = path.resolve(fullPath);
      
      // Skip duplicates using absolute path as key
      if (seen.has(absolutePath)) {
        console.log(`[Enhanced Glob] Skipping duplicate: ${absolutePath}`);
        continue;
      }
      seen.add(absolutePath);
      
      const relativePath = this.pathResolver.relativeTo(fullPath);
      
      try {
        const stat = await fs.stat(fullPath);
        results.push({
          path: relativePath,
          name: path.basename(match),
          type: stat.isDirectory() ? 'directory' : 'file',
          size: stat.isDirectory() ? null : stat.size,
          modified: stat.mtime.toISOString()
        });
      } catch (statError) {
        // Skip files we can't stat
        continue;
      }
    }

    console.log(`[Enhanced Glob] Found ${results.length} unique matches for pattern "${pattern}"`);
    return results;
  }
}

/**
 * Enhanced semantic search implementation
 */
class EnhancedSemanticSearch {
  constructor(workspaceRoot) {
    this.pathResolver = new WorkspacePathResolver(workspaceRoot);
    this.filter = new IntelligentFilter();
    this.indexCache = new Map();
  }

  async execute(params) {
    const {
      path: singlePath,
      paths,
      file_paths,
      query,
      queries,
      max_results = 20,
      context_lines = 3,
      include_snippets = true,
      search_mode = 'progressive',
      file_types = [],
      exclude_patterns = [],
      min_score = 0.1
    } = params;

    const startTime = Date.now();
    
    try {
      // Determine search paths
      const searchPaths = this.resolveSearchPaths(singlePath, paths, file_paths);
      
      // Determine search queries
      const searchQueries = queries || [query];
      
      // Execute search with timeout
      const results = await Promise.race([
        this.performSemanticSearch(searchPaths, searchQueries, {
          maxResults: max_results,
          contextLines: context_lines,
          includeSnippets: include_snippets,
          searchMode: search_mode,
          fileTypes: file_types,
          excludePatterns: exclude_patterns,
          minScore: min_score
        }),
        new Promise((_, reject) => 
          setTimeout(() => reject(new Error('Search timeout after 15 seconds')), 15000)
        )
      ]);

      // Implement progressive loading - return results in chunks
      const totalResults = results.length;
      const returnedResults = results.slice(0, max_results);
      const hasMore = totalResults > max_results;

      return {
        success: true,
        results: returnedResults,
        query: query,
        queries: searchQueries,
        searchPaths: searchPaths.map(p => this.pathResolver.relativeTo(p)),
        totalResults: totalResults,
        returnedResults: returnedResults.length,
        hasMore: hasMore,
        executionTime: Date.now() - startTime,
        searchMode: search_mode,
        // Add pagination info for progressive loading
        pagination: {
          page: 1,
          pageSize: max_results,
          totalPages: Math.ceil(totalResults / max_results),
          hasNextPage: hasMore
        }
      };

    } catch (error) {
      return {
        success: false,
        error: error.message,
        query: query,
        results: []
      };
    }
  }

  resolveSearchPaths(singlePath, paths, filePaths) {
    const searchPaths = [];
    
    if (singlePath) {
      searchPaths.push(this.pathResolver.resolve(singlePath));
    }
    
    if (paths) {
      searchPaths.push(...paths.map(p => this.pathResolver.resolve(p)));
    }
    
    if (filePaths) {
      searchPaths.push(...filePaths.map(p => this.pathResolver.resolve(p)));
    }
    
    // Default to workspace root if no paths specified
    if (searchPaths.length === 0) {
      searchPaths.push(this.pathResolver.workspaceRoot);
    }
    
    return searchPaths;
  }

  async performSemanticSearch(searchPaths, queries, options) {
    const { maxResults, contextLines, includeSnippets, searchMode, fileTypes, excludePatterns, minScore } = options;
    const allResults = [];
    
    for (const searchPath of searchPaths) {
      const pathResults = await this.searchInPath(searchPath, queries, {
        contextLines,
        includeSnippets,
        searchMode,
        fileTypes,
        excludePatterns,
        minScore
      });
      
      allResults.push(...pathResults);
    }
    
    // Remove duplicates and sort by relevance score
    const uniqueResults = this.deduplicateResults(allResults);
    const sortedResults = uniqueResults.sort((a, b) => b.score - a.score);
    
    return sortedResults.slice(0, maxResults);
  }

  async searchInPath(searchPath, queries, options) {
    const results = [];
    const { contextLines, includeSnippets, searchMode, fileTypes, excludePatterns, minScore } = options;
    
    try {
      const stat = await fs.stat(searchPath);
      
      if (stat.isFile()) {
        // Search in single file
        const fileResults = await this.searchInFile(searchPath, queries, options);
        results.push(...fileResults);
      } else if (stat.isDirectory()) {
        // Search in directory
        const files = await this.findSearchableFiles(searchPath, fileTypes, excludePatterns);
        
        for (const filePath of files) {
          const fileResults = await this.searchInFile(filePath, queries, options);
          results.push(...fileResults);
        }
      }
    } catch (error) {
      // Skip paths we can't access
    }
    
    return results;
  }

  async findSearchableFiles(dirPath, fileTypes, excludePatterns) {
    const files = [];
    const maxFileSize = 10 * 1024 * 1024; // 10MB limit
    
    try {
      const entries = await fs.readdir(dirPath, { withFileTypes: true });
      
      for (const entry of entries) {
        const fullPath = path.join(dirPath, entry.name);
        const relativePath = this.pathResolver.relativeTo(fullPath);
        
        // Skip if excluded by patterns
        if (this.filter.shouldExclude(relativePath, { customPatterns: excludePatterns })) {
          continue;
        }
        
        if (entry.isFile()) {
          // Check file type filter
          if (fileTypes.length > 0) {
            const ext = path.extname(entry.name).slice(1).toLowerCase();
            if (!fileTypes.includes(ext)) continue;
          }
          
          // Check file size
          try {
            const stat = await fs.stat(fullPath);
            if (stat.size <= maxFileSize) {
              files.push(fullPath);
            }
          } catch (statError) {
            continue;
          }
        } else if (entry.isDirectory()) {
          // Recurse into subdirectories
          const subFiles = await this.findSearchableFiles(fullPath, fileTypes, excludePatterns);
          files.push(...subFiles);
        }
      }
    } catch (error) {
      // Skip directories we can't read
    }
    
    return files;
  }

  async searchInFile(filePath, queries, options) {
    const { contextLines, includeSnippets, searchMode, minScore } = options;
    const results = [];
    
    try {
      const content = await fs.readFile(filePath, 'utf8');
      const lines = content.split('\n');
      
      for (const query of queries) {
        const matches = this.findMatches(lines, query, searchMode);
        
        for (const match of matches) {
          if (match.score < minScore) continue;
          
          const result = {
            file: this.pathResolver.relativeTo(filePath),
            line: match.lineNumber,
            score: match.score,
            matchedText: match.text
          };
          
          if (includeSnippets) {
            result.snippet = this.extractSnippet(lines, match.lineNumber, contextLines);
          }
          
          results.push(result);
        }
      }
    } catch (error) {
      // Skip files we can't read
    }
    
    return results;
  }

  findMatches(lines, query, searchMode) {
    const matches = [];
    const queryTerms = query.toLowerCase().split(/\s+/).filter(term => term.length > 0);
    
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const lineLower = line.toLowerCase();
      
      let score = 0;
      
      switch (searchMode) {
        case 'exact':
          if (lineLower.includes(query.toLowerCase())) {
            score = 1.0;
          }
          break;
          
        case 'fuzzy':
          score = this.calculateFuzzyScore(lineLower, queryTerms);
          break;
          
        case 'progressive':
          // Try exact first, then fuzzy
          if (lineLower.includes(query.toLowerCase())) {
            score = 1.0;
          } else {
            score = this.calculateFuzzyScore(lineLower, queryTerms) * 0.8;
          }
          break;
          
        case 'semantic':
          score = this.calculateTfIdfScore(lineLower, queryTerms);
          break;
      }
      
      if (score > 0) {
        matches.push({
          lineNumber: i + 1,
          text: line.trim(),
          score: score
        });
      }
    }
    
    return matches;
  }

  calculateFuzzyScore(line, queryTerms) {
    let totalScore = 0;
    let matchedTerms = 0;
    
    for (const term of queryTerms) {
      if (line.includes(term)) {
        matchedTerms++;
        // Boost score for exact word matches
        const wordBoundaryRegex = new RegExp(`\\b${term}\\b`);
        if (wordBoundaryRegex.test(line)) {
          totalScore += 1.0;
        } else {
          totalScore += 0.5;
        }
      }
    }
    
    // Normalize by number of query terms
    return queryTerms.length > 0 ? (totalScore / queryTerms.length) * (matchedTerms / queryTerms.length) : 0;
  }

  calculateTfIdfScore(line, queryTerms) {
    // Simplified TF-IDF scoring
    const lineTerms = line.split(/\s+/).filter(term => term.length > 0);
    const termFreq = {};
    
    // Calculate term frequency
    for (const term of lineTerms) {
      termFreq[term] = (termFreq[term] || 0) + 1;
    }
    
    let score = 0;
    for (const queryTerm of queryTerms) {
      if (termFreq[queryTerm]) {
        // Simple TF score (could be enhanced with IDF)
        const tf = termFreq[queryTerm] / lineTerms.length;
        score += tf;
      }
    }
    
    return score;
  }

  extractSnippet(lines, lineNumber, contextLines) {
    const startLine = Math.max(0, lineNumber - 1 - contextLines);
    const endLine = Math.min(lines.length - 1, lineNumber - 1 + contextLines);
    
    const snippetLines = [];
    for (let i = startLine; i <= endLine; i++) {
      const prefix = i === lineNumber - 1 ? '> ' : '  ';
      snippetLines.push(`${prefix}${i + 1}: ${lines[i]}`);
    }
    
    return snippetLines.join('\n');
  }

  deduplicateResults(results) {
    const seen = new Set();
    const unique = [];
    
    for (const result of results) {
      const key = `${result.file}:${result.line}`;
      if (!seen.has(key)) {
        seen.add(key);
        unique.push(result);
      }
    }
    
    return unique;
  }
}

module.exports = {
  EnhancedLs,
  EnhancedFindFiles,
  EnhancedGlob,
  EnhancedSemanticSearch,
  WorkspacePathResolver,
  IntelligentFilter
};