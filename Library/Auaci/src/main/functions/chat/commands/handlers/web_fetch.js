// src/main/functions/chat/commands/handlers/web_fetch.js
// Fetch content from a URL

const https = require('https');
const http = require('http');
const { URL } = require('url');
const { appendLog } = require('../lib/utils');

// Maximum output size in characters (5KB)
const MAX_OUTPUT_CHARS = 5000;

/**
 * Truncate output to maximum size
 */
function truncateOutput(output, maxChars = MAX_OUTPUT_CHARS) {
  if (!output || output.length <= maxChars) {
    return output;
  }
  const truncated = output.substring(0, maxChars);
  return truncated + `\n\n[... content truncated, showing first ${maxChars} characters of ${output.length} total ...]`;
}

/**
 * Strip HTML tags and decode entities for cleaner text
 */
function htmlToText(html) {
  // Remove script and style tags with their content
  let text = html.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '');
  text = text.replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '');
  
  // Remove HTML tags
  text = text.replace(/<[^>]+>/g, ' ');
  
  // Decode common HTML entities
  text = text.replace(/&nbsp;/g, ' ');
  text = text.replace(/&amp;/g, '&');
  text = text.replace(/&lt;/g, '<');
  text = text.replace(/&gt;/g, '>');
  text = text.replace(/&quot;/g, '"');
  text = text.replace(/&#39;/g, "'");
  text = text.replace(/&apos;/g, "'");
  
  // Collapse whitespace
  text = text.replace(/\s+/g, ' ');
  
  // Trim and clean up
  text = text.trim();
  
  return text;
}

/**
 * Fetch URL with redirect following
 */
function fetchUrl(url, maxRedirects = 5) {
  return new Promise((resolve, reject) => {
    if (maxRedirects <= 0) {
      reject(new Error('Too many redirects'));
      return;
    }
    
    const parsedUrl = new URL(url);
    const protocol = parsedUrl.protocol === 'https:' ? https : http;
    
    const options = {
      hostname: parsedUrl.hostname,
      port: parsedUrl.port || (parsedUrl.protocol === 'https:' ? 443 : 80),
      path: parsedUrl.pathname + parsedUrl.search,
      method: 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (compatible; AuaciBot/1.0)',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.5'
      },
      timeout: 15000
    };
    
    const req = protocol.request(options, (res) => {
      // Handle redirects
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        const redirectUrl = new URL(res.headers.location, url).href;
        fetchUrl(redirectUrl, maxRedirects - 1).then(resolve).catch(reject);
        return;
      }
      
      if (res.statusCode !== 200) {
        reject(new Error(`HTTP ${res.statusCode}: ${res.statusMessage}`));
        return;
      }
      
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => {
        const body = Buffer.concat(chunks).toString('utf8');
        resolve({
          url: url,
          statusCode: res.statusCode,
          contentType: res.headers['content-type'] || '',
          body: body
        });
      });
    });
    
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('Request timeout'));
    });
    
    req.end();
  });
}

/**
 * Web fetch command handler
 * @param {Object} params - { url, mode?, search_phrase? }
 * mode: "full" | "truncated" (default) | "selective"
 */
module.exports = async function webFetchCmd(params) {
  const url = params.url;
  const mode = params.mode || 'truncated';
  const searchPhrase = params.search_phrase || params.searchPhrase;
  
  // Validate parameters
  if (!url) {
    return {
      success: false,
      error: 'Missing required parameter: url',
      error_code: 'ERR_INVALID_INPUT',
      url: url
    };
  }
  
  // Validate URL format
  let parsedUrl;
  try {
    parsedUrl = new URL(url);
  } catch {
    return {
      success: false,
      error: 'Invalid URL format',
      error_code: 'ERR_INVALID_INPUT',
      url: url
    };
  }
  
  // Only allow http/https
  if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
    return {
      success: false,
      error: 'Only HTTP and HTTPS URLs are supported',
      error_code: 'ERR_UNSUPPORTED',
      url: url
    };
  }
  
  await appendLog(`[tools.web_fetch] Fetching ${url} (mode: ${mode})`);
  
  try {
    const response = await fetchUrl(url);
    
    // Convert HTML to text
    let content = htmlToText(response.body);
    
    // Apply mode
    if (mode === 'truncated') {
      // Limit to 5KB
      content = truncateOutput(content);
    } else if (mode === 'selective' && searchPhrase) {
      // Find sections containing the search phrase
      const lines = content.split(/[.!?]\s+/);
      const matchingLines = lines.filter(line => 
        line.toLowerCase().includes(searchPhrase.toLowerCase())
      );
      
      if (matchingLines.length > 0) {
        content = matchingLines.join('. ');
        // Also truncate selective results
        content = truncateOutput(content);
      } else {
        content = `No content found matching "${searchPhrase}"`;
      }
    } else {
      // mode === 'full' - still cap at 5KB for GPT context
      content = truncateOutput(content);
    }
    
    await appendLog(`[tools.web_fetch] Fetched ${content.length} chars from ${url}`);
    
    return {
      success: true,
      url: url,
      content: content,
      content_length: content.length,
      display_content: `Fetched from: ${url}\nSize: ${content.length} characters\n\n${content.substring(0, 500)}${content.length > 500 ? '...' : ''}`
    };
    
  } catch (err) {
    await appendLog(`[tools.web_fetch] Error: ${err.message}`);
    
    return {
      success: false,
      url: url,
      error: err.message,
      error_code: 'ERR_NETWORK'
    };
  }
};
