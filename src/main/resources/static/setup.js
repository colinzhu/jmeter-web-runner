// API Base URL
const API_BASE = '/api';

// State
let selectedFile = null;
let currentStatus = null;
let pendingUpload = false;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    initializeDropZone();
    checkStatus();
});

// Initialize drag-and-drop functionality
function initializeDropZone() {
    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('zipFileInput');

    // Click to select file
    dropZone.addEventListener('click', (e) => {
        if (e.target.id !== 'clearFileBtn' && !selectedFile) {
            fileInput.click();
        }
    });

    // File input change
    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) {
            handleFileSelection(e.target.files[0]);
        }
    });

    // Drag and drop events
    dropZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropZone.classList.add('drag-over');
    });

    dropZone.addEventListener('dragleave', (e) => {
        e.preventDefault();
        dropZone.classList.remove('drag-over');
    });

    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.classList.remove('drag-over');
        
        if (e.dataTransfer.files.length > 0) {
            handleFileSelection(e.dataTransfer.files[0]);
        }
    });
}

// Handle file selection
function handleFileSelection(file) {
    // Validate file extension
    if (!file.name.endsWith('.zip')) {
        showUploadStatus('Invalid file type. Only .zip files are accepted.', 'error');
        return;
    }

    // Validate file size (200MB max)
    const maxSize = 200 * 1024 * 1024; // 200MB in bytes
    if (file.size > maxSize) {
        showUploadStatus('File size exceeds maximum limit of 200MB.', 'error');
        return;
    }

    selectedFile = file;
    displaySelectedFile();
}

// Display selected file
function displaySelectedFile() {
    const dropZoneContent = document.querySelector('.drop-zone-content');
    const selectedFileDiv = document.getElementById('selectedFile');
    const selectedFileName = document.getElementById('selectedFileName');
    const uploadActions = document.getElementById('uploadActions');

    dropZoneContent.style.display = 'none';
    selectedFileDiv.style.display = 'flex';
    selectedFileName.textContent = `${selectedFile.name} (${formatFileSize(selectedFile.size)})`;
    uploadActions.style.display = 'flex';
    
    // Clear any previous status messages
    hideUploadStatus();
}

// Clear selected file
function clearSelectedFile() {
    selectedFile = null;
    const dropZoneContent = document.querySelector('.drop-zone-content');
    const selectedFileDiv = document.getElementById('selectedFile');
    const uploadActions = document.getElementById('uploadActions');
    const fileInput = document.getElementById('zipFileInput');

    dropZoneContent.style.display = 'flex';
    selectedFileDiv.style.display = 'none';
    uploadActions.style.display = 'none';
    fileInput.value = '';
    
    hideUploadStatus();
}

// Check JMeter status
async function checkStatus() {
    const statusDisplay = document.getElementById('statusDisplay');
    const refreshBtn = document.getElementById('refreshStatusBtn');
    
    refreshBtn.disabled = true;
    statusDisplay.innerHTML = '<p class="loading">Checking JMeter status...</p>';

    try {
        const response = await fetch(`${API_BASE}/setup/status`);
        
        if (!response.ok) {
            throw new Error('Failed to check status');
        }

        currentStatus = await response.json();
        displayStatus(currentStatus);
    } catch (error) {
        statusDisplay.innerHTML = `<p class="error">Error checking status: ${escapeHtml(error.message)}</p>`;
        statusDisplay.className = 'status-display';
    } finally {
        refreshBtn.disabled = false;
    }
}

// Display status
function displayStatus(status) {
    const statusDisplay = document.getElementById('statusDisplay');
    
    if (status.configured && status.available) {
        statusDisplay.className = 'status-display configured';
        statusDisplay.innerHTML = `
            <div class="status-info">
                <div class="status-icon success">✓</div>
                <div class="status-label">JMeter is configured and ready</div>
                <div class="status-detail"><strong>Version:</strong> ${escapeHtml(status.version)}</div>
                <div class="status-detail"><strong>Path:</strong> ${escapeHtml(status.path)}</div>
            </div>
        `;
    } else if (status.configured && !status.available) {
        statusDisplay.className = 'status-display not-configured';
        statusDisplay.innerHTML = `
            <div class="status-info">
                <div class="status-icon error">⚠</div>
                <div class="status-label">JMeter is configured but not available</div>
                <div class="status-detail">${escapeHtml(status.error || 'JMeter binary not found at configured path')}</div>
                <div class="status-detail">Please upload a new JMeter distribution.</div>
            </div>
        `;
    } else {
        statusDisplay.className = 'status-display not-configured';
        statusDisplay.innerHTML = `
            <div class="status-info">
                <div class="status-icon error">✗</div>
                <div class="status-label">JMeter is not configured</div>
                <div class="status-detail">Please upload a JMeter distribution ZIP file to get started.</div>
            </div>
        `;
    }
}

// Upload JMeter distribution
async function uploadJMeter() {
    if (!selectedFile) {
        showUploadStatus('Please select a file first.', 'error');
        return;
    }

    // Check if replacement is needed
    if (currentStatus && currentStatus.configured) {
        showReplacementDialog();
        return;
    }

    // Proceed with upload
    await performUpload();
}

// Show replacement confirmation dialog
function showReplacementDialog() {
    const dialog = document.getElementById('confirmDialog');
    const currentInstallInfo = document.getElementById('currentInstallInfo');
    
    currentInstallInfo.innerHTML = `
        <p><strong>Current Version:</strong> ${escapeHtml(currentStatus.version)}</p>
        <p><strong>Current Path:</strong> ${escapeHtml(currentStatus.path)}</p>
    `;
    
    dialog.style.display = 'flex';
}

// Confirm replacement
async function confirmReplace() {
    const dialog = document.getElementById('confirmDialog');
    dialog.style.display = 'none';
    await performUpload();
}

// Cancel replacement
function cancelReplace() {
    const dialog = document.getElementById('confirmDialog');
    dialog.style.display = 'none';
}

// Perform the actual upload
async function performUpload() {
    if (pendingUpload) return;
    
    pendingUpload = true;
    const uploadBtn = document.getElementById('uploadBtn');
    const progressContainer = document.getElementById('progressContainer');
    const progressBar = document.getElementById('progressBar');
    const progressText = document.getElementById('progressText');
    
    uploadBtn.disabled = true;
    progressContainer.style.display = 'block';
    hideUploadStatus();

    try {
        // Create form data
        const formData = new FormData();
        formData.append('file', selectedFile);

        // Show upload progress
        progressBar.style.width = '30%';
        progressText.textContent = 'Uploading file...';

        const response = await fetch(`${API_BASE}/setup/upload`, {
            method: 'POST',
            body: formData
        });

        // Update progress for extraction
        progressBar.style.width = '60%';
        progressText.textContent = 'Extracting and configuring...';

        const result = await response.json();

        if (response.ok && result.success) {
            // Complete progress
            progressBar.style.width = '100%';
            progressText.textContent = 'Configuration complete!';

            // Show success message
            setTimeout(() => {
                showSuccessMessage(result);
                progressContainer.style.display = 'none';
                clearSelectedFile();
                checkStatus();
            }, 1000);
        } else {
            throw new Error(result.message || result.error || 'Upload failed');
        }
    } catch (error) {
        progressContainer.style.display = 'none';
        showUploadStatus(`Upload failed: ${escapeHtml(error.message)}`, 'error');
    } finally {
        uploadBtn.disabled = false;
        pendingUpload = false;
    }
}

// Show success message
function showSuccessMessage(result) {
    const successMessage = document.getElementById('successMessage');
    const successDetails = document.getElementById('successDetails');
    
    successDetails.innerHTML = `
        <p><strong>Version:</strong> ${escapeHtml(result.version)}</p>
        <p><strong>Installation Path:</strong> ${escapeHtml(result.path)}</p>
        <p><strong>Message:</strong> ${escapeHtml(result.message)}</p>
    `;
    
    successMessage.style.display = 'block';
    
    // Scroll to success message
    successMessage.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

// Show upload status message
function showUploadStatus(message, type) {
    const statusDiv = document.getElementById('uploadStatus');
    statusDiv.textContent = message;
    statusDiv.className = `status-message ${type}`;
    statusDiv.style.display = 'block';
    
    // Auto-hide success messages after 5 seconds
    if (type === 'success') {
        setTimeout(() => {
            hideUploadStatus();
        }, 5000);
    }
}

// Hide upload status message
function hideUploadStatus() {
    const statusDiv = document.getElementById('uploadStatus');
    statusDiv.style.display = 'none';
}

// Utility functions
function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
