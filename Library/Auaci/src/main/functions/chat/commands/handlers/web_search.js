// src/main/functions/chat/commands/handlers/web_search.js
// Web search using DuckDuckGo HTML interface (no API key required)
// Provides search results with titles, URLs, and snippets

const https = require('https');
const http = require('http');
const { URL } = require('url');
const { appendLog } = require('../lib/utils');

/**
 * Make HTTP/HTTPS request with redirect following
 */
function makeRequest(url, options = {}, postData = null) {
  return new Promise((resolve, reject) => {
    const maxRedirects = options.maxRedirects || 5;
    
    const doRequest = (currentUrl, redirectsLeft) => {
      if (redirectsLeft <= 0) {
        reject(new Error('Too many redirects'));
        return;
      }
      
      const parsedUrl = new URL(currentUrl);
      const protocol = parsedUrl.protocol === 'https:' ? https : http;
      
      const reqOptions = {
        hostname: parsedUrl.hostname,
        port: parsedUrl.port || (parsedUrl.protocol === 'https:' ? 443 : 80),
        path: parsedUrl.pathname + parsedUrl.search,
        method: options.method || 'GET',
        headers: {
          'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
          'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          'Accept-Language': 'en-US,en;q=0.9',
          ...options.headers
        },
        timeout: options.timeout || 15000
      };
      
      const req = protocol.request(reqOptions, (res) => {
        // Handle redirects
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          const redirectUrl = new URL(res.headers.location, currentUrl).href;
          doRequest(redirectUrl, redirectsLeft - 1);
          return;
        }
        
        const chunks = [];
        res.on('data', chunk => chunks.push(chunk));
        res.on('end', () => {
          const body = Buffer.concat(chunks).toString('utf8');
          resolve({
            statusCode: res.statusCode,
            headers: res.headers,
            body: body
          });
        });
      });
      
      req.on('error', reject);
      req.on('timeout', () => {
        req.destroy();
        reject(new Error('Request timeout'));
      });
      
      if (postData) {
        req.write(postData);
      }
      req.end();
    };
    
    doRequest(url, maxRedirects);
  });
}

/**
 * Decode HTML entities
 */
function decodeHtmlEntities(text) {
  if (!text) return '';
  return text
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&apos;/g, "'")
    .replace(/&#x27;/g, "'")
    .replace(/&#x2F;/g, '/')
    .replace(/&#(\d+);/g, (_, num) => String.fromCharCode(parseInt(num, 10)))
    .replace(/&#x([0-9a-fA-F]+);/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
}

/**
 * Strip HTML tags
 */
function stripHtml(html) {
  if (!html) return '';
  return html.replace(/<[^>]+>/g, '').trim();
}

/**
 * Parse DuckDuckGo HTML search results
 */
function parseDuckDuckGoResults(html) {
  const results = [];
  
  // Match result blocks - DuckDuckGo uses class="result" or class="result results_links"
  const resultRegex = /<div[^>]*class="[^"]*result[^"]*"[^>]*>([\s\S]*?)<\/div>\s*(?=<div[^>]*class="[^"]*result|<div[^>]*class="[^"]*nav-link|$)/gi;
  
  let match;
  while ((match = resultRegex.exec(html)) !== null) {
    const block = match[1];
    
    // Extract URL - look for result__url or result__a
    let url = '';
    const urlMatch = block.match(/href="([^"]+)"[^>]*class="[^"]*result__a/i) ||
                     block.match(/class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"/i) ||
                     block.match(/href="(https?:\/\/[^"]+)"/i);
    if (urlMatch) {
      url = decodeHtmlEntities(urlMatch[1]);
      // DuckDuckGo sometimes uses redirect URLs
      if (url.includes('duckduckgo.com/l/?')) {
        const uddgMatch = url.match(/uddg=([^&]+)/);
        if (uddgMatch) {
          url = decodeURIComponent(uddgMatch[1]);
        }
      }
    }
    
    // Extract title
    let title = '';
    const titleMatch = block.match(/<a[^>]*class="[^"]*result__a[^"]*"[^>]*>([^<]+)</i) ||
                       block.match(/<h2[^>]*class="[^"]*result__title[^"]*"[^>]*>[\s\S]*?<a[^>]*>([^<]+)</i) ||
                       block.match(/<a[^>]*href="[^"]*"[^>]*>([^<]{10,})</i);
    if (titleMatch) {
      title = decodeHtmlEntities(stripHtml(titleMatch[1])).trim();
    }
    
    // Extract snippet
    let snippet = '';
    const snippetMatch = block.match(/<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)<\/a>/i) ||
                         block.match(/<div[^>]*class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)<\/div>/i);
    if (snippetMatch) {
      snippet = decodeHtmlEntities(stripHtml(snippetMatch[1])).trim();
    }
    
    // Only add if we have at least URL and title
    if (url && title && !url.includes('duckduckgo.com')) {
      results.push({
        title: title,
        url: url,
        snippet: snippet || '(No description available)'
      });
    }
  }
  
  return results;
}

/**
 * Search using DuckDuckGo HTML interface
 */
async function searchDuckDuckGo(query, numResults = 10) {
  const encodedQuery = encodeURIComponent(query);
  const searchUrl = `https://html.duckduckgo.com/html/?q=${encodedQuery}`;
  
  try {
    const response = await makeRequest(searchUrl, {
      method: 'GET',
      headers: {
        'Accept': 'text/html',
        'Accept-Language': 'en-US,en;q=0.9'
      }
    });
    
    if (response.statusCode !== 200) {
      throw new Error(`Search returned status ${response.statusCode}`);
    }
    
    const results = parseDuckDuckGoResults(response.body);
    return results.slice(0, numResults);
    
  } catch (err) {
    throw new Error(`DuckDuckGo search failed: ${err.message}`);
  }
}

/**
 * Alternative: Use DuckDuckGo Instant Answer API for quick facts
 * This is limited but good for definitions and quick answers
 */
async function searchInstantAnswer(query) {
  const encodedQuery = encodeURIComponent(query);
  const apiUrl = `https://api.duckduckgo.com/?q=${encodedQuery}&format=json&no_html=1&skip_disambig=1`;
  
  try {
    const response = await makeRequest(apiUrl);
    
    if (response.statusCode !== 200) {
      return null;
    }
    
    const data = JSON.parse(response.body);
    
    if (data.Abstract || data.Answer || data.Definition) {
      return {
        type: 'instant_answer',
        abstract: data.Abstract || '',
        abstractSource: data.AbstractSource || '',
        abstractUrl: data.AbstractURL || '',
        answer: data.Answer || '',
        definition: data.Definition || '',
        definitionSource: data.DefinitionSource || '',
        relatedTopics: (data.RelatedTopics || []).slice(0, 5).map(t => ({
          text: t.Text || '',
          url: t.FirstURL || ''
        })).filter(t => t.text)
      };
    }
    
    return null;
  } catch {
    return null;
  }
}

/**
 * Web search command handler
 * @param {Object} params - { query, num_results? }
 */
module.exports = async function webSearchCmd(params) {
  const query = params.query || params.q;
  const numResults = Math.min(Math.max(parseInt(params.num_results || params.numResults) || 10, 1), 20);
  
  if (!query) {
    return {
      success: false,
      error: 'Missing required parameter: query',
      error_code: 'ERR_INVALID_INPUT',
      query: query
    };
  }
  
  await appendLog(`[tools.web_search] Searching for: "${query}" (max ${numResults} results)`);
  
  try {
    // First try to get instant answer for quick facts
    const instantAnswer = await searchInstantAnswer(query);
    
    // Then get regular search results
    const searchResults = await searchDuckDuckGo(query, numResults);
    
    if (searchResults.length === 0 && !instantAnswer) {
      return {
        success: false,
        error: 'No search results found',
        error_code: 'ERR_NOT_FOUND',
        query: query
      };
    }
    
    await appendLog(`[tools.web_search] Found ${searchResults.length} results`);
    
    // Format results for display
    let formattedResults = `Search results for: "${query}"\n`;
    formattedResults += '='.repeat(50) + '\n\n';
    
    // Add instant answer if available
    if (instantAnswer) {
      formattedResults += '📌 INSTANT ANSWER:\n';
      if (instantAnswer.answer) {
        formattedResults += `${instantAnswer.answer}\n`;
      }
      if (instantAnswer.abstract) {
        formattedResults += `${instantAnswer.abstract}\n`;
        if (instantAnswer.abstractSource) {
          formattedResults += `Source: ${instantAnswer.abstractSource} (${instantAnswer.abstractUrl})\n`;
        }
      }
      if (instantAnswer.definition) {
        formattedResults += `Definition: ${instantAnswer.definition}\n`;
        if (instantAnswer.definitionSource) {
          formattedResults += `Source: ${instantAnswer.definitionSource}\n`;
        }
      }
      formattedResults += '\n' + '-'.repeat(50) + '\n\n';
    }
    
    // Add search results
    formattedResults += '🔍 SEARCH RESULTS:\n\n';
    searchResults.forEach((result, index) => {
      formattedResults += `${index + 1}. ${result.title}\n`;
      formattedResults += `   URL: ${result.url}\n`;
      formattedResults += `   ${result.snippet}\n\n`;
    });
    
    return {
      success: true,
      query: query,
      instant_answer: instantAnswer,
      results: searchResults,
      result_count: searchResults.length,
      formatted: formattedResults,
      display_content: formattedResults
    };
    
  } catch (err) {
    await appendLog(`[tools.web_search] Error: ${err.message}`);
    
    return {
      success: false,
      query: query,
      error: err.message,
      error_code: 'ERR_NETWORK'
    };
  }
};

// Export helper for other modules
module.exports.searchDuckDuckGo = searchDuckDuckGo;
module.exports.searchInstantAnswer = searchInstantAnswer;
