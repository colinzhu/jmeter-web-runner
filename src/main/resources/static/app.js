// API Base URL
const API_BASE = '/api';

// State
let files = [];
let executions = [];
let filteredFiles = [];
let searchQuery = '';

// Check JMeter status on startup
async function checkJMeterStatus() {
    try {
        const response = await fetch(`${API_BASE}/setup/status`);
        if (!response.ok) return;
        
        const status = await response.json();
        
        if (!status.configured || !status.available) {
            showJMeterSetupPrompt();
        }
    } catch (error) {
        // Silently fail - setup check is optional
        console.warn('Could not check JMeter status:', error);
    }
}

// Show JMeter setup prompt
function showJMeterSetupPrompt() {
    const container = document.querySelector('.container');
    const setupPrompt = document.createElement('div');
    setupPrompt.className = 'notification info';
    setupPrompt.style.position = 'relative';
    setupPrompt.style.marginBottom = '20px';
    setupPrompt.innerHTML = `
        <strong>⚠️ JMeter Not Configured</strong><br>
        Please configure JMeter before executing tests.
        <a href="setup.html" style="color: white; text-decoration: underline; margin-left: 10px;">Go to Setup →</a>
    `;
    
    const header = container.querySelector('header');
    header.after(setupPrompt);
}

// Initialize app
document.addEventListener('DOMContentLoaded', () => {
    initializeUploadForm();
    initializeSearch();
    checkJMeterStatus();
    loadFiles();
    loadExecutions();
    startAutoRefresh();
});

// File Upload Form Handler
function initializeUploadForm() {
    const form = document.getElementById('uploadForm');
    const fileInput = document.getElementById('fileInput');
    const fileName = document.getElementById('fileName');
    const uploadButton = document.getElementById('uploadButton');
    const uploadStatus = document.getElementById('uploadStatus');

    // Update file name display
    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) {
            fileName.textContent = e.target.files[0].name;
        } else {
            fileName.textContent = 'No file chosen';
        }
    });

    // Handle form submission
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const file = fileInput.files[0];
        if (!file) {
            showUploadStatus('Please select a file', 'error');
            return;
        }

        // Validate file extension
        if (!file.name.endsWith('.jmx')) {
            showUploadStatus('Invalid file type. Only .jmx files are accepted.', 'error');
            return;
        }

        uploadButton.disabled = true;
        uploadButton.textContent = 'Uploading...';
        showUploadStatus('Uploading file...', 'info');

        try {
            const formData = new FormData();
            formData.append('file', file);

            const response = await fetch(`${API_BASE}/files`, {
                method: 'POST',
                body: formData
            });

            if (response.ok) {
                const result = await response.json();
                showUploadStatus(`File uploaded successfully: ${result.filename}`, 'success');
                form.reset();
                fileName.textContent = 'No file chosen';
                loadFiles(); // Refresh file list
            } else {
                const error = await response.json();
                showUploadStatus(error.error || 'Upload failed', 'error');
            }
        } catch (error) {
            showUploadStatus('Upload failed: ' + error.message, 'error');
        } finally {
            uploadButton.disabled = false;
            uploadButton.textContent = 'Upload';
        }
    });
}

function showUploadStatus(message, type) {
    const statusDiv = document.getElementById('uploadStatus');
    statusDiv.textContent = message;
    statusDiv.className = `status-message ${type}`;
    statusDiv.style.display = 'block';
    
    // Auto-hide success messages after 5 seconds
    if (type === 'success') {
        setTimeout(() => {
            statusDiv.style.display = 'none';
        }, 5000);
    }
}

// Load and display files
async function loadFiles() {
    const filesList = document.getElementById('filesList');
    
    try {
        const response = await fetch(`${API_BASE}/files`);
        if (!response.ok) throw new Error('Failed to load files');
        
        files = await response.json();
        filterFiles(); // This will call displayFiles with filtered results
    } catch (error) {
        filesList.innerHTML = `<p class="error">Error loading files: ${error.message}</p>`;
    }
}

// Initialize search functionality
function initializeSearch() {
    const searchInput = document.getElementById('fileSearch');
    const clearSearchBtn = document.getElementById('clearSearchBtn');
    
    searchInput.addEventListener('input', (e) => {
        searchQuery = e.target.value.toLowerCase().trim();
        filterFiles();
        
        if (searchQuery.length > 0) {
            clearSearchBtn.style.display = 'flex';
        } else {
            clearSearchBtn.style.display = 'none';
        }
    });
}

// Clear search
function clearSearch() {
    const searchInput = document.getElementById('fileSearch');
    searchInput.value = '';
    searchQuery = '';
    filterFiles();
    document.getElementById('clearSearchBtn').style.display = 'none';
}

// Filter files based on search query
function filterFiles() {
    if (!searchQuery) {
        filteredFiles = files;
    } else {
        filteredFiles = files.filter(file => 
            file.filename.toLowerCase().includes(searchQuery) ||
            file.id.toLowerCase().includes(searchQuery)
        );
    }
    displayFiles(filteredFiles);
}

function displayFiles(filesToDisplay) {
    const filesList = document.getElementById('filesList');
    
    if (files.length === 0) {
        filesList.innerHTML = '<p class="empty">No files uploaded yet</p>';
        return;
    }
    
    if (searchQuery && filteredFiles.length === 0) {
        filesList.innerHTML = `
            <div class="empty-search">
                <div class="empty-search-icon">🔍</div>
                <div class="empty-search-text">No files found</div>
                <div class="empty-search-hint">Try adjusting your search terms</div>
            </div>
        `;
        return;
    }

    filesList.innerHTML = filteredFiles.map(file => `
        <div class="file-item" data-id="${file.id}">
            <div class="file-info">
                <div class="file-name">${escapeHtml(file.filename)}</div>
                <div class="file-meta">
                    <span class="file-meta-item">ID: ${formatId(file.id)}</span>
                    <span class="file-meta-item">${formatFileSize(file.size)}</span>
                    <span class="file-meta-item">${formatDate(file.uploadedAt)}</span>
                </div>
            </div>
            <div class="file-actions">
                <button class="btn btn-success" onclick="executeTest('${file.id}')">Execute</button>
                <button class="btn btn-secondary" onclick="downloadFile('${file.id}', '${escapeHtml(file.filename)}')">Download</button>
                <button class="btn btn-danger" onclick="deleteFile('${file.id}')">Delete</button>
            </div>
        </div>
    `).join('');
}

// Execute test
async function executeTest(fileId) {
    try {
        const response = await fetch(`${API_BASE}/executions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ fileId })
        });

        if (response.ok) {
            const execution = await response.json();
            showNotification(`Test execution started (ID: ${execution.id})`, 'success');
            loadExecutions(); // Refresh execution list
        } else {
            const error = await response.json();
            showNotification(error.error || 'Failed to start execution', 'error');
        }
    } catch (error) {
        showNotification('Failed to start execution: ' + error.message, 'error');
    }
}

// Delete file
async function deleteFile(fileId) {
    try {
        const response = await fetch(`${API_BASE}/files/${fileId}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            showNotification('File deleted successfully', 'success');
            loadFiles(); // Refresh file list
        } else {
            const error = await response.json();
            showNotification(error.error || 'Failed to delete file', 'error');
        }
    } catch (error) {
        showNotification('Failed to delete file: ' + error.message, 'error');
    }
}

// Download file
async function downloadFile(fileId, filename) {
    try {
        const response = await fetch(`${API_BASE}/files/${fileId}/download`);
        if (!response.ok) throw new Error('Failed to download file');

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);

        showNotification('File downloaded successfully', 'success');
    } catch (error) {
        showNotification('Failed to download file: ' + error.message, 'error');
    }
}

// Cancel execution
async function cancelExecution(executionId) {
    try {
        const response = await fetch(`${API_BASE}/executions/${executionId}/cancel`, {
            method: 'DELETE'
        });

        if (response.ok) {
            const execution = await response.json();
            showNotification(`Execution cancelled successfully (ID: ${formatId(executionId)})`, 'success');
            loadExecutions(); // Refresh execution list
        } else {
            const error = await response.json();
            showNotification(error.error || 'Failed to cancel execution', 'error');
        }
    } catch (error) {
        showNotification('Failed to cancel execution: ' + error.message, 'error');
    }
}

// Clear history
async function clearHistory() {
    try {
        const response = await fetch(`${API_BASE}/executions/history`, {
            method: 'DELETE'
        });

        if (response.ok) {
            const result = await response.json();
            showNotification(`History cleared. ${result.deletedCount} execution(s) deleted.`, 'success');
            loadExecutions(); // Refresh execution list
        } else {
            const error = await response.json();
            showNotification(error.error || 'Failed to clear history', 'error');
        }
    } catch (error) {
        showNotification('Failed to clear history: ' + error.message, 'error');
    }
}

// Load and display executions
async function loadExecutions() {
    const executionsList = document.getElementById('executionsList');
    
    try {
        const response = await fetch(`${API_BASE}/executions`);
        if (!response.ok) throw new Error('Failed to load executions');
        
        executions = await response.json();
        displayExecutions(executions);
    } catch (error) {
        executionsList.innerHTML = `<p class="error">Error loading executions: ${error.message}</p>`;
    }
}

function displayExecutions(executions) {
    const executionsList = document.getElementById('executionsList');
    const clearHistoryBtn = document.getElementById('clearHistoryBtn');
    
    if (executions.length === 0) {
        executionsList.innerHTML = '<p class="empty">No test executions yet</p>';
        clearHistoryBtn.style.display = 'none';
        return;
    }

    // Sort by creation date (newest first)
    const sortedExecutions = [...executions].sort((a, b) => 
        new Date(b.createdAt) - new Date(a.createdAt)
    );

    // Check if there are clearable executions (completed, failed, or cancelled)
    const hasClearableExecutions = sortedExecutions.some(exec => 
        exec.status === 'completed' || exec.status === 'failed' || exec.status === 'cancelled'
    );
    clearHistoryBtn.style.display = hasClearableExecutions ? 'block' : 'none';

    executionsList.innerHTML = sortedExecutions.map(exec => {
        const statusClass = getStatusClass(exec.status);
        const queueInfo = exec.status === 'queued' && exec.queuePosition > 0 
            ? `<span class="queue-position">Queue position: ${exec.queuePosition}</span>` 
            : '';
        
        // Show Cancel button for queued or running executions
        const showCancelButton = exec.status === 'queued' || exec.status === 'running';
        
        const reportButtons = exec.status === 'completed' && exec.reportId ? `
            <button class="btn btn-info" onclick="viewReport('${exec.reportId}')">View Report</button>
            <button class="btn btn-secondary" onclick="downloadReport('${exec.reportId}')">Download Report</button>
        ` : '';
        
        const cancelButton = showCancelButton ? `
            <button class="btn btn-warning" onclick="cancelExecution('${exec.id}')">Cancel</button>
        ` : '';
        
        const actionButtons = reportButtons || cancelButton ? `
            <div class="execution-actions">
                ${reportButtons}
                ${cancelButton}
            </div>
        ` : '';
        
        return `
            <div class="execution-item ${statusClass}" data-id="${exec.id}">
                <div class="execution-header">
                    <div class="execution-header-left">
                        <span class="execution-id">Execution: ${formatId(exec.id)}</span>
                        <span class="execution-status status-${exec.status}">${exec.status.toUpperCase()}</span>
                    </div>
                    ${actionButtons}
                </div>
                <div class="execution-details">
                    ${exec.filename ? `<div>File: ${escapeHtml(exec.filename)}</div>` : ''}
                    <div>File ID: ${formatId(exec.fileId)}</div>
                </div>
                <div class="execution-details">
                    <div>Created: ${formatDate(exec.createdAt)}</div>
                    ${exec.startedAt ? `<div>Started: ${formatDate(exec.startedAt)}</div>` : ''}
                    ${exec.completedAt ? `<div>Completed: ${formatDate(exec.completedAt)}</div>` : ''}
                    ${exec.duration ? `<div>Duration: ${exec.duration}s</div>` : ''}
                    ${queueInfo}
                    ${exec.error ? `<div class="error-message">Error: ${escapeHtml(exec.error)}</div>` : ''}
                </div>
            </div>
        `;
    }).join('');
}

function getStatusClass(status) {
    const statusMap = {
        'queued': 'status-queued',
        'running': 'status-running',
        'completed': 'status-completed',
        'failed': 'status-failed',
        'cancelled': 'status-cancelled'
    };
    return statusMap[status] || '';
}

// View report
function viewReport(reportId) {
    window.open(`${API_BASE}/reports/${reportId}/`, '_blank');
}

// Download report
async function downloadReport(reportId) {
    try {
        const response = await fetch(`${API_BASE}/reports/${reportId}/download`);
        if (!response.ok) throw new Error('Failed to download report');
        
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `report-${reportId}.zip`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        
        showNotification('Report downloaded successfully', 'success');
    } catch (error) {
        showNotification('Failed to download report: ' + error.message, 'error');
    }
}

// Auto-refresh executions
function startAutoRefresh() {
    setInterval(() => {
        loadExecutions();
    }, 5000); // Refresh every 5 seconds
}

// Utility functions
function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

function formatDate(dateString) {
    const date = new Date(dateString);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatId(id) {
    // For timestamp-based IDs (format: 2026-01-10T11-02-50-123456), show date and time
    // For UUID-based IDs, show first 8 characters
    if (id && id.includes('T') && id.length > 19) {
        return id;
    }
    // Fallback for UUID or other formats
    return id ? id.substring(0, 8) : id;
}

function showNotification(message, type) {
    // Create notification element
    const notification = document.createElement('div');
    notification.className = `notification ${type}`;
    notification.textContent = message;
    document.body.appendChild(notification);
    
    // Auto-remove after 5 seconds
    setTimeout(() => {
        notification.remove();
    }, 5000);
}
