// API Base URL
const API_BASE = '/api';

// State
let reports = [];

// Initialize app
document.addEventListener('DOMContentLoaded', () => {
    loadReports();
});

// Load and display reports
async function loadReports() {
    const reportsList = document.getElementById('reportsList');
    
    try {
        const response = await fetch(`${API_BASE}/reports`);
        if (!response.ok) throw new Error('Failed to load reports');
        
        reports = await response.json();
        displayReports(reports);
    } catch (error) {
        reportsList.innerHTML = `<p class="error">Error loading reports: ${error.message}</p>`;
    }
}

function displayReports(reports) {
    const reportsList = document.getElementById('reportsList');
    
    if (reports.length === 0) {
        reportsList.innerHTML = '<p class="empty">No reports available yet</p>';
        return;
    }

    // Sort by creation date (newest first)
    const sortedReports = [...reports].sort((a, b) => 
        new Date(b.createdAt) - new Date(a.createdAt)
    );

    reportsList.innerHTML = sortedReports.map(report => `
        <div class="report-item" data-id="${report.id}">
            <div class="report-info">
                <div class="report-id">Report ID: ${formatId(report.id)}</div>
                <div class="report-meta">
                    <span>Execution ID: ${formatId(report.executionId)}</span>
                    <span>Size: ${formatFileSize(report.size)}</span>
                    <span>Created: ${formatDate(report.createdAt)}</span>
                </div>
            </div>
            <div class="report-actions">
                <button class="btn btn-info" onclick="viewReport('${report.id}')">View Report</button>
                <button class="btn btn-secondary" onclick="downloadReport('${report.id}')">Download</button>
                <button class="btn btn-danger" onclick="deleteReport('${report.id}')">Delete</button>
            </div>
        </div>
    `).join('');
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

// Delete report
async function deleteReport(reportId) {
    try {
        const response = await fetch(`${API_BASE}/reports/${reportId}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            showNotification('Report deleted successfully', 'success');
            loadReports(); // Refresh report list
        } else {
            const error = await response.json();
            showNotification(error.error || 'Failed to delete report', 'error');
        }
    } catch (error) {
        showNotification('Failed to delete report: ' + error.message, 'error');
    }
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
