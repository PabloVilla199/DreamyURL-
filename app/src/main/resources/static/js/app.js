/**
 * Initializes the document and sets up the form submission handler.
 * Uses modern JavaScript features with vanilla DOM manipulation.
 */
document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('shortener');
    const urlInput = document.getElementById('urlInput');

    /**
     * Handles the form submission event.
     * Prevents the default form submission and sends an AJAX POST request.
     * @param {Event} event - The form submission event.
     */
    form.addEventListener('submit', function(event) {
        event.preventDefault();
        
        const url = urlInput.value.trim();
        
        if (!isValidURL(url)) {
            showError('Please enter a valid URL');
            return;
        }
        
        setLoading(true);
        clearResult();
        
        // Use modern FormData with multipart/form-data
        const formData = new FormData();
        formData.append('url', url);
        
        fetch('/api/link', {
            method: 'POST',
            body: formData,
            headers: {
                'Accept': 'application/json'
            }
        })
        .then(response => {
            if (response.status === 202) {
                return response.json().then(data => {
                    const jobId = data?.jobId;
                    if (jobId) {
                        const resultEl = document.getElementById('result');
                        resultEl.innerHTML = `\n                            <div class='alert alert-warning lead' id='errorDetail'>\n                                <strong>Notice:</strong> Your URL is being processed asynchronously. Job id: ${jobId}\n                            </div>\n                        `;
                        const jobCheckContainer = document.getElementById('job-check');
                        const jobInput = document.getElementById('jobInput');
                        jobInput.value = jobId;
                        jobCheckContainer.style.display = 'block';
                        checkJob(jobId);
                    }
                });
            } else if (response.ok) {
                return response.json().then(data => {
                    const shortURL = response.headers.get('Location') || data.url;
                    showSuccess(shortURL);

                    // If the backend included a job id in the validationResult (pending check),
                    // show the job-check UI so the user can consult the job status.
                    try {
                        const err = data?.properties?.validationResult?.errorType;
                        const m = /jobId=([0-9a-fA-F\-]{36})/.exec(err || '');
                        if (m) {
                            const jobId = m[1];
                            const jobCheckContainer = document.getElementById('job-check');
                            const jobInput = document.getElementById('jobInput');
                            jobInput.value = jobId;
                            jobCheckContainer.style.display = 'block';
                            // Auto-check once
                            checkJob(jobId);
                        }
                    } catch (e) {
                        // ignore parsing errors
                    }
                });
            } else {
                return response.json().then(errorData => {
                    // Handle Problem Details format (RFC 9457)
                    const errorMessage = errorData.detail || errorData.title || 'Failed to shorten URL';
                    showError(errorMessage);
                }).catch(() => {
                    showError('Failed to shorten URL');
                });
            }
        })
        .catch(error => {
            console.error('Error shortening URL:', error);
            showError('Network error. Please try again.');
        })
        .finally(() => {
            setLoading(false);
        });
    });
    
    // Add input validation
    urlInput.addEventListener('input', function() {
        const url = this.value.trim();
        const isValid = !url || isValidURL(url);
        
        if (url && isValid) {
            this.classList.remove('is-invalid');
            this.classList.add('is-valid');
        } else if (url && !isValid) {
            this.classList.remove('is-valid');
            this.classList.add('is-invalid');
        } else {
            this.classList.remove('is-valid', 'is-invalid');
        }
    });

    // Job check UI bindings
    const jobCheckContainer = document.getElementById('job-check');
    const jobInput = document.getElementById('jobInput');
    const checkJobBtn = document.getElementById('checkJobBtn');
    const jobResult = document.getElementById('jobResult');
    const toggleJobCheck = document.getElementById('toggleJobCheck');

    // Toggle job-check visibility when user clicks the control
    if (toggleJobCheck) {
        toggleJobCheck.addEventListener('click', function() {
            if (jobCheckContainer.style.display === 'none' || !jobCheckContainer.style.display) {
                jobCheckContainer.style.display = 'block';
            } else {
                jobCheckContainer.style.display = 'none';
            }
        });
    }

    checkJobBtn.addEventListener('click', function() {
        const jobId = jobInput.value.trim();
        if (!jobId) {
            jobResult.innerHTML = `<div class='alert alert-warning'>Please enter a jobId</div>`;
            return;
        }
        checkJob(jobId);
    });
});

/**
 * Check if URL is valid
 * @param {string} url - The URL to validate
 * @returns {boolean} True if valid
 */
function isValidURL(url) {
    try {
        new URL(url);
        return true;
    } catch {
        return false;
    }
}

/**
 * Set loading state
 * @param {boolean} isLoading - Whether to show loading
 */
function setLoading(isLoading) {
    const submitBtn = document.getElementById('submitBtn');
    if (isLoading) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Shortening...';
    } else {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Short me!';
    }
}

/**
 * Show success message with shortened URL
 * @param {string} shortURL - The shortened URL
 */
function showSuccess(shortURL) {
    document.getElementById('result').innerHTML = `
        <div class='alert alert-success lead'>
            <strong>Success!</strong> Your URL has been shortened:
            <br><br>
            <a target='_blank' href='${shortURL}' style="color: white; text-decoration: underline; font-weight: 600;">
                ${shortURL}
            </a>
            <br><br>
            <button class="btn btn-sm btn-outline-secondary" onclick="copyToClipboard('${shortURL}')">
                Copy URL
            </button>
        </div>
    `;
    const urlInput = document.getElementById('urlInput');
    urlInput.value = '';
    urlInput.classList.remove('is-valid', 'is-invalid');
    
    // Focus back to input for next URL
    setTimeout(() => urlInput.focus(), 100);
}

/**
 * Show error message
 * @param {string} message - The error message
 */
function showError(message) {
    const resultEl = document.getElementById('result');
    // If message contains jobId=..., show as warning + job check UI
    const m = /jobId=([0-9a-fA-F\-]{36})/.exec(message);
    if (m) {
        const jobId = m[1];
        resultEl.innerHTML = `
            <div class='alert alert-warning lead' id='errorDetail'>
                <strong>Notice:</strong> ${message}
            </div>
        `;
        const jobCheckContainer = document.getElementById('job-check');
        const jobInput = document.getElementById('jobInput');
        jobInput.value = jobId;
        jobCheckContainer.style.display = 'block';
        // auto-check once
        checkJob(jobId);
        return;
    }

    // regular error
    resultEl.innerHTML = `
        <div class='alert alert-danger lead' id='errorDetail'>
            <strong>Error:</strong> ${message}
        </div>
    `;
}

/**
 * Clear result area
 */
function clearResult() {
    document.getElementById('result').innerHTML = '';
}

/**
 * Copy text to clipboard
 * @param {string} text - The text to copy
 */
function copyToClipboard(text) {
    if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(() => {
            // Show feedback
            const btn = event.target;
            const originalText = btn.textContent;
            btn.textContent = 'Copied!';
            setTimeout(() => {
                btn.textContent = originalText;
            }, 2000);
        }).catch(() => {
            // Fallback for older browsers
            fallbackCopyToClipboard(text);
        });
    } else {
        fallbackCopyToClipboard(text);
    }
}

/**
 * Fallback copy to clipboard for older browsers
 * @param {string} text - The text to copy
 */
function fallbackCopyToClipboard(text) {
    const textArea = document.createElement('textarea');
    textArea.value = text;
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
        document.execCommand('copy');
        const btn = event.target;
        const originalText = btn.textContent;
        btn.textContent = 'Copied!';
        setTimeout(() => {
            btn.textContent = originalText;
        }, 2000);
    } catch (err) {
        console.error('Failed to copy: ', err);
    }
    document.body.removeChild(textArea);
}

/**
 * Query job status endpoint and show result
 */
function checkJob(jobId) {
    const jobResult = document.getElementById('jobResult');
    jobResult.innerHTML = `<div class='text-muted'>Checking job ${jobId}...</div>`;

    // Normalizes the job response from the server into a stable shape
    async function fetchJobNormalized(jobId) {
        const resp = await fetch(`/api/safebrowsing/job/${jobId}`, { method: 'GET', headers: { 'Accept': 'application/json' } });
        const locationHeader = resp.headers.get('Location');
        let body = null;
        try { body = await resp.json(); } catch (_) { body = null; }

        const first = (...vals) => { for (const v of vals) if (v !== undefined && v !== null && v !== '') return v; return null; };
        const stringifyStatus = s => {
            if (!s) return null;
            if (typeof s === 'string') return s;
            if (typeof s === 'object') {
                try {
                    const keys = Object.keys(s);
                            if (keys.length === 1) return String(keys[0]).replace(/^Object\s+/i, '').trim();
                            if (s.type) return String(s.type).replace(/^Object\s+/i, '').trim();
                            if (s.name) return String(s.name).replace(/^Object\s+/i, '').trim();
                    // detect common markers in object keys
                            const joined = keys.join(' ').toLowerCase();
                    if (joined.includes('safe')) return 'Safe';
                    if (joined.includes('unsafe')) return 'Unsafe';
                } catch (_) { /* fallthrough */ }
            }
                    if (s && s.constructor && s.constructor.name) return String(s.constructor.name).replace(/^Object\s+/i, '').split('.').pop();
                    // Fallback: try to parse the string value for known tokens
                    try {
                        const sStr = String(s);
                        // Look for common status tokens
                        const m = sStr.match(/(Safe|Unsafe|Pending|Unknown|Error)/i);
                        if (m) return m[0].charAt(0).toUpperCase() + m[0].slice(1).toLowerCase();
                        // Strip leading 'Object' and delimiters, take last meaningful token
                        const cleaned = sStr.replace(/^Object\s+/i, '').trim();
                        const parts = cleaned.split(/[\s.@\$]+/).filter(p => p.length > 0);
                        if (parts.length) return parts[parts.length - 1];
                    } catch (_) { /* fallthrough */ }
                    try { return JSON.stringify(s); } catch (_) { return String(s); }
        };
        const extractUrl = b => {
            if (!b) return null;
            if (typeof b === 'string' && b.startsWith('http')) return b;
            if (b.url && typeof b.url === 'object' && b.url.value) return b.url.value;
            if (b.url && typeof b.url === 'string') return b.url;
            if (b.redirection && b.redirection.target && b.redirection.target.value) return b.redirection.target.value;
            return null;
        };
        const deriveSafe = statusStr => {
            if (!statusStr) return null;
            const s = String(statusStr).toLowerCase();
            if (s.includes('safe')) return true;
            if (s.includes('unsafe') || s.includes('error')) return false;
            return null;
        };

        const result = { jobId, status: null, safe: null, url: null, created: null, updated: null, raw: body };

        if (body) {
            if (body.jobId) {
                result.jobId = body.jobId;
                result.status = typeof body.status === 'string' ? body.status : stringifyStatus(body.status);
                result.safe = typeof body.safe === 'boolean' ? body.safe : deriveSafe(result.status);
                result.url = first(body.url, extractUrl(body), locationHeader);
                result.created = first(body.created, body.createdAt, body.validatedAt);
                result.updated = first(body.updated, body.updatedAt);
                return result;
            }

            // Domain object case (SafeBrowsingJob)
            result.jobId = first(body.id, body.jobId, jobId);
            if (typeof body.status === 'string') result.status = body.status;
            else if (body.status) result.status = body.status.type || body.status.name || stringifyStatus(body.status);
            result.safe = typeof body.safe === 'boolean' ? body.safe : deriveSafe(result.status);
            result.url = first(extractUrl(body), locationHeader);
            result.created = first(body.createdAt, body.created, body.validatedAt);
            result.updated = first(body.updatedAt, body.updated);
            return result;
        }

        // No body JSON -> fallback to Location header
        result.url = locationHeader;
        result.status = 'Unknown';
        result.safe = null;
        return result;
    }

    // Poll until we have final information (url or explicit safe/unsafe) or timeout
    async function pollJobUntilDone(jobId, tries = 20, delayMs = 200) {
        for (let i = 0; i < tries; i++) {
            const r = await fetchJobNormalized(jobId);
            renderJobResult(r);
            const s = (r.status || '').toString().toLowerCase();
            if (r.url || s.includes('safe') || s.includes('unsafe')) return r;
            await new Promise(res => setTimeout(res, delayMs));
        }
        return await fetchJobNormalized(jobId);
    }

    // Render the normalized job result into the UI
    function renderJobResult(r) {
        const jobResult = document.getElementById('jobResult');
        const jobIdDisplayed = r.jobId || jobId;
        const status = r.status || 'Unknown';
        const created = r.created || '';
        const updated = r.updated || '';
        const url = r.url || '';

        let urlHtml = '';
        if (url) {
            urlHtml = `<a target="_blank" href="${url}" style="font-weight:600;">${url}</a>`;
            urlHtml += ` <button class="btn btn-sm btn-outline-secondary" onclick="copyToClipboard('${url}')">Copy URL</button>`;
        }

        const safeBadge = (r.safe === true) ? '<span class="badge bg-success">Safe</span>' : (r.safe === false) ? '<span class="badge bg-danger">Unsafe</span>' : '<span class="badge bg-secondary">Pending</span>';

        jobResult.innerHTML = `
            <div class='alert alert-info'>
                <strong>Job ${jobIdDisplayed}</strong><br>
                Status: <strong>${status}</strong> ${safeBadge}<br>
                URL: ${urlHtml || 'N/A'}<br>
                Created: ${created}<br>
                Updated: ${updated}
            </div>
        `;
    }

    // Start polling; when finished, final result already rendered
    pollJobUntilDone(jobId).catch(err => {
        const jobResult = document.getElementById('jobResult');
        jobResult.innerHTML = `<div class='alert alert-warning'>${err.message}</div>`;
    });
}