// Dashboard API integration and UI Logic

const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1' || window.location.hostname === '';
const BACKEND_URL = (isLocalhost && window.location.port !== '8081') ? 'http://localhost:8081' : '';
const API_JOBS = `${BACKEND_URL}/api/jobs`;
const API_OPENINGS = `${BACKEND_URL}/api/jobs/openings`;
let allJobs = [];
let editJobId = null; // Stores job ID when editing
let currentCalendarDate = new Date();
let chartInstances = {}; // Holds Chart.js objects to prevent overlap redraws

// Validate session on load
function checkSession() {
    const token = localStorage.getItem('jwt_token');
    const userId = localStorage.getItem('user_id');
    const username = localStorage.getItem('username');

    if (!token || !userId || !username) {
        localStorage.clear();
        window.location.href = '/login.html';
        return false;
    }
    return { token, userId, username };
}

const session = checkSession();

// Show alert banner
function showAlert(message, type = 'success') {
    const existing = document.querySelector('.alert');
    if (existing) existing.remove();

    const alertEl = document.createElement('div');
    alertEl.className = `alert alert-${type} glass`;
    
    const icon = type === 'success' ? 
        `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>` :
        `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>`;

    alertEl.innerHTML = `${icon} <span>${message}</span>`;
    document.body.appendChild(alertEl);

    setTimeout(() => alertEl.classList.add('show'), 50);

    setTimeout(() => {
        alertEl.classList.remove('show');
        setTimeout(() => alertEl.remove(), 400);
    }, 4000);
}

// Fetch jobs from backend
async function fetchJobs() {
    if (!session) return;
    
    try {
        const response = await fetch(`${API_JOBS}?userId=${session.userId}`, {
            headers: {
                'Authorization': `Bearer ${session.token}`
            }
        });

        if (response.status === 401) {
            showAlert('Session expired. Logging out...', 'error');
            setTimeout(logout, 1500);
            return;
        }

        if (response.ok) {
            allJobs = await response.json();
            updateStatistics();
            
            // Refresh currently active main tab view
            const activeTab = document.querySelector('.tab-btn.active').dataset.tab;
            if (activeTab === 'applications') {
                renderJobs();
            } else if (activeTab === 'calendar') {
                renderCalendar();
            } else if (activeTab === 'analytics') {
                renderAnalytics();
            }
        } else {
            showAlert('Failed to retrieve job applications', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Could not load jobs from server', 'error');
    }
}

// Update summary stats cards
function updateStatistics() {
    const activeJobs = allJobs.filter(j => j.status.toLowerCase() !== 'saved');
    const total = activeJobs.length;
    const applied = activeJobs.filter(j => j.status.toLowerCase() === 'applied').length;
    const interviewing = activeJobs.filter(j => j.status.toLowerCase() === 'interviewing').length;
    const offered = activeJobs.filter(j => j.status.toLowerCase() === 'offered').length;
    const rejected = activeJobs.filter(j => j.status.toLowerCase() === 'rejected').length;

    document.getElementById('stat-total').innerText = total;
    document.getElementById('stat-applied').innerText = applied;
    document.getElementById('stat-interviewing').innerText = interviewing;
    document.getElementById('stat-offered').innerText = offered;
    document.getElementById('stat-rejected').innerText = rejected;
}

// Render jobs list
function renderJobs() {
    const listEl = document.getElementById('jobs-list');
    listEl.innerHTML = '';

    const activeFilterBtn = document.querySelector('.filter-btn.active');
    const filter = activeFilterBtn ? activeFilterBtn.dataset.filter : 'all';
    
    const searchKeyword = document.getElementById('dashboardSearch') ? document.getElementById('dashboardSearch').value.toLowerCase().trim() : '';
    const searchLocation = document.getElementById('dashboardLocationSearch') ? document.getElementById('dashboardLocationSearch').value.toLowerCase().trim() : '';

    let filtered = allJobs.filter(j => j.status.toLowerCase() !== 'saved');

    // Status filter
    if (filter !== 'all') {
        filtered = filtered.filter(j => j.status.toLowerCase() === filter);
    }

    // Keyword filter (company name or role)
    if (searchKeyword) {
        filtered = filtered.filter(j => 
            (j.companyName && j.companyName.toLowerCase().includes(searchKeyword)) ||
            (j.role && j.role.toLowerCase().includes(searchKeyword))
        );
    }

    // Location filter
    if (searchLocation) {
        filtered = filtered.filter(j => 
            j.location && j.location.toLowerCase().includes(searchLocation)
        );
    }

    if (filtered.length === 0) {
        listEl.innerHTML = `
            <div class="empty-state glass">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
                </svg>
                <h3>No Applications Found</h3>
                <p>Click "Add Application" above to begin tracking your job hunt.</p>
            </div>
        `;
        return;
    }

    filtered.forEach(job => {
        const card = document.createElement('div');
        card.className = 'job-card glass';

        const statusClass = `badge badge-${job.status.toLowerCase()}`;
        const formattedDate = job.appliedDate ? new Date(job.appliedDate).toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'}) : 'N/A';
        const formattedInterviewDate = job.interviewDate ? new Date(job.interviewDate).toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'}) : '—';
        
        let locationSalaryHtml = '';
        if (job.location || job.salary) {
            locationSalaryHtml = `
                <div style="font-size: 11px; color: var(--text-secondary); margin-top: 4px; display: flex; gap: 8px;">
                    ${job.location ? `<span>📍 ${escapeHtml(job.location)}</span>` : ''}
                    ${job.salary ? `<span>💰 ${escapeHtml(job.salary)}</span>` : ''}
                </div>
            `;
        }

        // Show ATS matching score indicator if cached
        let scoreBadgeHtml = '';
        if (job.resumeScore !== null && job.resumeScore !== undefined && job.resumeScore >= 0) {
            scoreBadgeHtml = `
                <span class="badge" style="background: rgba(6, 182, 212, 0.12); color: var(--accent); border: 1px solid rgba(6, 182, 212, 0.25); margin-left: 6px;">
                    ATS: ${job.resumeScore}%
                </span>
            `;
        }

        card.innerHTML = `
            <div class="job-main-info">
                <span class="job-company">${escapeHtml(job.companyName)}</span>
                <span class="job-role">${escapeHtml(job.role)}</span>
                ${locationSalaryHtml}
            </div>
            <div class="job-date-container">
                <span class="form-label" style="display:inline;font-size:11px;">Applied:</span> ${formattedDate}
            </div>
            <div class="job-date-container">
                <span class="form-label" style="display:inline;font-size:11px;">Interview:</span> ${formattedInterviewDate}
            </div>
            <div>
                <span class="${statusClass}">${job.status}</span>
                ${scoreBadgeHtml}
            </div>
            <div class="job-actions">
                <button class="action-btn" onclick="openEditModal('${job.id}')" title="View / Edit Details">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
                </button>
                <button class="action-btn action-btn-danger" onclick="deleteJob('${job.id}')" title="Delete Application">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>
                </button>
            </div>
        `;
        listEl.appendChild(card);
    });
}

// Submit Job Data to backend
async function submitJobData(payload) {
    const saveJobBtn = document.getElementById('saveJobBtn');
    if (saveJobBtn) saveJobBtn.disabled = true;

    try {
        let response;
        if (editJobId) {
            // Update
            response = await fetch(`${API_JOBS}/${editJobId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${session.token}`
                },
                body: JSON.stringify(payload)
            });
        } else {
            // Create
            response = await fetch(`${API_JOBS}?userId=${session.userId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${session.token}`
                },
                body: JSON.stringify(payload)
            });
        }

        if (response.ok) {
            showAlert(editJobId ? 'Application updated successfully' : 'Application added successfully', 'success');
            closeModal();
            fetchJobs();
        } else {
            showAlert('Failed to save application', 'error');
            if (saveJobBtn) saveJobBtn.disabled = false;
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred', 'error');
        if (saveJobBtn) saveJobBtn.disabled = false;
    }
}

// Add or Update Job (via Save Application button)
async function handleFormSubmit(e) {
    e.preventDefault();
    if (!session) return;

    const companyName = document.getElementById('companyName').value.trim();
    const role = document.getElementById('role').value.trim();
    let status = document.getElementById('status').value;
    let appliedDate = document.getElementById('appliedDate').value;
    const interviewDate = document.getElementById('interviewDate').value || null;
    const notes = document.getElementById('notes').value.trim();
    const salary = document.getElementById('salary').value.trim();
    const location = document.getElementById('location').value.trim();
    const jobDescription = document.getElementById('jobDescription').value.trim();
    const interviewNotes = document.getElementById('interviewNotes').value.trim();

    if (!companyName || !role || !appliedDate) {
        if (!companyName && !role) {
            showAlert('Please enter at least a Company Name or Job Role to save.', 'error');
            return;
        }

        const confirmDraft = confirm('Some required fields (like Applied Date) are empty. Would you like to save this application as a Draft?');
        if (!confirmDraft) {
            return;
        }

        status = 'Draft';
        appliedDate = null;
    }

    // Keep cached resume data, but include inputs
    const payload = {
        companyName: companyName || "Unnamed Company",
        role: role || "Unnamed Role",
        status,
        appliedDate,
        interviewDate,
        notes,
        salary,
        location,
        jobDescription,
        interviewNotes
    };
    
    // Copy existing properties if we are editing
    if (editJobId) {
        const existing = allJobs.find(j => j.id === editJobId);
        if (existing) {
            payload.resumePath = existing.resumePath;
            payload.resumeFilename = existing.resumeFilename;
            payload.resumeScore = existing.resumeScore;
            payload.aiAnalysisJson = existing.aiAnalysisJson;
        }
    }

    await submitJobData(payload);
}


// Delete Job
async function deleteJob(id) {
    if (!session) return;
    if (!confirm('Are you sure you want to delete this application?')) return;

    try {
        const response = await fetch(`${API_JOBS}/${id}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${session.token}`
            }
        });

        if (response.ok) {
            showAlert('Application deleted', 'success');
            fetchJobs();
        } else {
            showAlert('Failed to delete application', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred', 'error');
    }
}

// Modal control
function openAddModal() {
    editJobId = null;
    document.getElementById('modal-title').innerText = 'Add Job Application';
    document.getElementById('jobForm').reset();
    document.getElementById('appliedDate').value = new Date().toISOString().split('T')[0];
    
    // Hide Resume upload since the job hasn't been saved to DB yet
    document.getElementById('modal-resume-section').classList.add('hidden');
    
    document.getElementById('modal-overlay').classList.add('show');
}

function openEditModal(id) {
    editJobId = id;
    const job = allJobs.find(j => j.id === id);
    if (!job) return;

    document.getElementById('modal-title').innerText = 'Edit Job Application';
    document.getElementById('companyName').value = job.companyName;
    document.getElementById('role').value = job.role;
    document.getElementById('status').value = job.status;
    document.getElementById('appliedDate').value = job.appliedDate;
    document.getElementById('interviewDate').value = job.interviewDate || '';
    document.getElementById('notes').value = job.notes || '';
    document.getElementById('salary').value = job.salary || '';
    document.getElementById('location').value = job.location || '';
    document.getElementById('interviewNotes').value = job.interviewNotes || '';

    // Show Resume section since the job application exists
    document.getElementById('modal-resume-section').classList.remove('hidden');

    // Populate resume file display
    updateResumeUI(job);

    document.getElementById('modal-overlay').classList.add('show');
}

function closeModal() {
    document.getElementById('modal-overlay').classList.remove('show');
    document.getElementById('saveJobBtn').disabled = false;
}

// Switch modal tab panes
function switchModalPane(paneId) {
    // Buttons
    const buttons = document.querySelectorAll('.modal-tab-btn');
    buttons.forEach(btn => {
        if (btn.dataset.modalTab === paneId) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });

    // Panes
    const panes = document.querySelectorAll('.modal-tab-pane');
    panes.forEach(pane => {
        if (pane.id === paneId) {
            pane.classList.remove('hidden');
        } else {
            pane.classList.add('hidden');
        }
    });
}

// Update resume section layout based on file details
function updateResumeUI(job) {
    const uploadZone = document.getElementById('resume-upload-zone');
    const fileInfo = document.getElementById('resume-file-info');
    const fileName = document.getElementById('resume-file-name');
    const downloadBtn = document.getElementById('download-resume-btn');

    if (job.resumeFilename && job.resumePath) {
        uploadZone.classList.add('hidden');
        fileInfo.classList.remove('hidden');
        fileName.innerText = job.resumeFilename;
        downloadBtn.href = `/api/jobs/${job.id}/resume`;
    } else {
        uploadZone.classList.remove('hidden');
        fileInfo.classList.add('hidden');
    }
}

// Render dynamic AI panel
function renderAIAnalysis(job) {
    const resultsPanel = document.getElementById('ai-scan-results');
    const questionsContainer = document.getElementById('ai-questions-list');
    
    if (!job.aiAnalysisJson) {
        resultsPanel.classList.add('hidden');
        questionsContainer.innerHTML = `
            <p style="font-size: 13px; color: var(--text-secondary); text-align: center; padding: 20px 0;">
                Run the Gemini AI scan in the Resume tab to generate interview prep materials.
            </p>
        `;
        return;
    }

    try {
        const analysis = JSON.parse(job.aiAnalysisJson);
        resultsPanel.classList.remove('hidden');
        
        // Match rate score
        const score = analysis.score || 0;
        document.getElementById('ai-match-score').innerText = `${score}%`;
        
        // Update SVG circle stroke-dasharray
        const circleProgress = document.getElementById('ats-circle-progress');
        if (circleProgress) {
            circleProgress.setAttribute('stroke-dasharray', `${score}, 100`);
            // Color circle based on score
            if (score < 40) {
                circleProgress.setAttribute('stroke', 'var(--danger)');
            } else if (score < 70) {
                circleProgress.setAttribute('stroke', '#f59e0b');
            } else {
                circleProgress.setAttribute('stroke', 'var(--success)');
            }
        }

        // Update Rating Text
        const ratingEl = document.getElementById('ats-score-rating');
        if (ratingEl) {
            if (score < 45) {
                ratingEl.innerText = '⚠️ Low Match / High Gaps';
                ratingEl.style.color = 'var(--danger)';
            } else if (score < 75) {
                ratingEl.innerText = '⚡ Good Match / Some Gaps';
                ratingEl.style.color = '#f59e0b';
            } else {
                ratingEl.innerText = '🏆 Excellent Match / ATS Approved';
                ratingEl.style.color = 'var(--success)';
            }
        }
        
        // Keywords Matched
        const matchedList = document.getElementById('ai-matched-keywords');
        matchedList.innerHTML = '';
        if (analysis.matchedKeywords && analysis.matchedKeywords.length > 0) {
            analysis.matchedKeywords.forEach(kw => {
                matchedList.innerHTML += `<span class="keyword-badge keyword-badge-matched">${escapeHtml(kw)}</span>`;
            });
        } else {
            matchedList.innerHTML = '<span style="font-size:12px;color:var(--text-muted);">None detected</span>';
        }

        // Keywords Missing
        const missingList = document.getElementById('ai-missing-keywords');
        missingList.innerHTML = '';
        if (analysis.missingKeywords && analysis.missingKeywords.length > 0) {
            analysis.missingKeywords.forEach(kw => {
                missingList.innerHTML += `<span class="keyword-badge keyword-badge-missing">${escapeHtml(kw)}</span>`;
            });
        } else {
            missingList.innerHTML = '<span style="font-size:12px;color:var(--text-muted);">None detected</span>';
        }

        // Weaknesses / Gaps
        const weaknessesList = document.getElementById('ai-weaknesses');
        if (weaknessesList) {
            weaknessesList.innerHTML = '';
            const weaknessesArr = analysis.weaknesses || [];
            if (weaknessesArr.length > 0) {
                weaknessesArr.forEach(weak => {
                    weaknessesList.innerHTML += `<li style="margin-bottom:4px;">${escapeHtml(weak)}</li>`;
                });
            } else {
                weaknessesList.innerHTML = '<li style="color:var(--text-muted); list-style:none;">No weaknesses logged. Run AI scan to refresh details.</li>';
            }
        }

        // Optimization suggestions
        const suggestionsList = document.getElementById('ai-suggestions');
        suggestionsList.innerHTML = '';
        if (analysis.suggestions && analysis.suggestions.length > 0) {
            analysis.suggestions.forEach(sug => {
                suggestionsList.innerHTML += `<li>${escapeHtml(sug)}</li>`;
            });
        } else {
            suggestionsList.innerHTML = '<li style="color:var(--text-muted);">No suggestions: Resume is highly optimized.</li>';
        }

        // AI interview questions
        questionsContainer.innerHTML = '';
        if (analysis.interviewQuestions && analysis.interviewQuestions.length > 0) {
            analysis.interviewQuestions.forEach((q, idx) => {
                const qCard = document.createElement('div');
                qCard.className = 'ai-question-card';
                qCard.innerHTML = `
                    <div class="ai-question-header" onclick="toggleQuestionBody(${idx})">
                        <span>Q${idx+1}: ${escapeHtml(q.question)}</span>
                        <svg id="q-arrow-${idx}" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="transition:transform 0.2s;"><polyline points="6 9 12 15 18 9"></polyline></svg>
                    </div>
                    <div id="q-body-${idx}" class="ai-question-body hidden">
                        <strong style="color: var(--accent); display: block; margin-bottom: 4px;">Talking Points & Strategy:</strong>
                        ${escapeHtml(q.talkingPoints)}
                    </div>
                `;
                questionsContainer.appendChild(qCard);
            });
        } else {
            questionsContainer.innerHTML = `<p style="font-size:13px;color:var(--text-secondary);text-align:center;">No questions generated.</p>`;
        }

    } catch (e) {
        console.error('Failed to parse AI Analysis JSON', e);
        resultsPanel.classList.add('hidden');
    }
}

// Toggle display of AI question answers
function toggleQuestionBody(idx) {
    const body = document.getElementById(`q-body-${idx}`);
    const arrow = document.getElementById(`q-arrow-${idx}`);
    if (body.classList.contains('hidden')) {
        body.classList.remove('hidden');
        arrow.style.transform = 'rotate(180deg)';
    } else {
        body.classList.add('hidden');
        arrow.style.transform = 'rotate(0deg)';
    }
}

// Upload resume PDF
async function handleResumeUpload(file) {
    if (!editJobId) return;
    
    const loader = document.getElementById('upload-loader');
    const statusText = document.getElementById('upload-status-text');
    
    const formData = new FormData();
    formData.append('file', file);
    
    loader.classList.remove('hidden');
    statusText.innerText = 'Uploading...';
    
    try {
        const response = await fetch(`${API_JOBS}/${editJobId}/resume`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${session.token}`
            },
            body: formData
        });

        if (response.ok) {
            const updatedJob = await response.json();
            showAlert('Resume PDF uploaded successfully', 'success');
            
            // Update local object array
            const idx = allJobs.findIndex(j => j.id === editJobId);
            if (idx !== -1) allJobs[idx] = updatedJob;
            
            updateResumeUI(updatedJob);
            renderAIAnalysis(updatedJob);
            fetchJobs(); // Refresh list to update score badges
        } else {
            const errorText = await response.text();
            showAlert(errorText || 'Failed to upload PDF', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred during upload', 'error');
    } finally {
        loader.classList.add('hidden');
        statusText.innerText = 'Click to choose or Drag PDF resume here';
    }
}

// Download resume PDF with JWT authorization
async function handleResumeDownload(e) {
    e.preventDefault();
    if (!editJobId) return;

    const currentJob = allJobs.find(j => j.id === editJobId);
    if (!currentJob || !currentJob.resumeFilename) return;

    try {
        const response = await fetch(`${API_JOBS}/${editJobId}/resume`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${session.token}`
            }
        });

        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = currentJob.resumeFilename || 'resume.pdf';
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        } else {
            showAlert('Failed to download resume from server', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred during download', 'error');
    }
}

// Delete resume PDF
async function handleResumeDelete() {
    if (!editJobId) return;
    if (!confirm('Are you sure you want to remove the linked resume PDF? This will also delete cached AI scans.')) return;
    
    try {
        const response = await fetch(`${API_JOBS}/${editJobId}/resume`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${session.token}`
            }
        });

        if (response.ok) {
            const updatedJob = await response.json();
            showAlert('Resume removed', 'success');
            
            const idx = allJobs.findIndex(j => j.id === editJobId);
            if (idx !== -1) allJobs[idx] = updatedJob;
            
            updateResumeUI(updatedJob);
            renderAIAnalysis(updatedJob);
            fetchJobs(); // Update dashboard listings
        } else {
            showAlert('Could not delete resume from server', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred', 'error');
    }
}

// Analyze with Gemini AI
async function handleResumeAnalysis() {
    if (!editJobId) return;
    
    const analyzeBtn = document.getElementById('analyze-resume-btn');
    const jobDescriptionVal = document.getElementById('jobDescription').value.trim();
    
    if (!jobDescriptionVal) {
        showAlert('Please paste the Job Description first', 'error');
        return;
    }

    // Disable button during analysis
    analyzeBtn.disabled = true;
    const btnSpan = analyzeBtn.querySelector('span');
    const origText = btnSpan.innerText;
    btnSpan.innerText = 'Scanning with Gemini AI...';

    try {
        // Step 1: Save the updated Job Description text to DB first
        const currentJob = allJobs.find(j => j.id === editJobId);
        if (currentJob) {
            currentJob.jobDescription = jobDescriptionVal;
            currentJob.interviewNotes = document.getElementById('interviewNotes').value.trim();
            
            await fetch(`${API_JOBS}/${editJobId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${session.token}`
                },
                body: JSON.stringify(currentJob)
            });
        }

        // Step 2: Trigger AI analysis API call
        const response = await fetch(`${API_JOBS}/${editJobId}/analyze`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${session.token}`
            }
        });

        if (response.ok) {
            const updatedJob = await response.json();
            showAlert('Gemini AI match analysis completed!', 'success');
            
            const idx = allJobs.findIndex(j => j.id === editJobId);
            if (idx !== -1) allJobs[idx] = updatedJob;
            
            renderAIAnalysis(updatedJob);
            fetchJobs(); // Refresh dashboard listing scores
        } else {
            const errorText = await response.text();
            showAlert(errorText || 'AI scan failed', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error during AI scan', 'error');
    } finally {
        analyzeBtn.disabled = false;
        btnSpan.innerText = origText;
    }
}

// Send test email
async function handleSendTestEmail() {
    if (!editJobId) return;
    
    const testEmailBtn = document.getElementById('test-email-btn');
    testEmailBtn.disabled = true;
    
    try {
        const response = await fetch(`${API_JOBS}/${editJobId}/test-email`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${session.token}`
            }
        });

        if (response.ok) {
            showAlert('Test email alert sent (check email or console logs!)', 'success');
        } else {
            const errorMsg = await response.text();
            showAlert(errorMsg || 'Failed to dispatch email', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred', 'error');
    } finally {
        testEmailBtn.disabled = false;
    }
}

// Logout
function logout() {
    localStorage.clear();
    window.location.href = '/login.html';
}

// Utility HTML escape helper
function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;')
              .replace(/'/g, '&#039;');
}

/* ==========================================================================
   Main Tab Navigation Routing
   ========================================================================== */
function setupTabNavigation() {
    const tabBtns = document.querySelectorAll('.tab-btn');
    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tabBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            
            const targetTab = btn.dataset.tab;
            
            // Hide all tabs
            const panes = document.querySelectorAll('.tab-pane');
            panes.forEach(pane => pane.classList.add('hidden'));
            
            // Show selected tab
            document.getElementById(`${targetTab}-view`).classList.remove('hidden');
            
            if (targetTab === 'applications') {
                renderJobs();
            } else if (targetTab === 'openings') {
                renderOpenings();
            } else if (targetTab === 'calendar') {
                renderCalendar();
            } else if (targetTab === 'analytics') {
                renderAnalytics();
            }
        });
    });
}

/* ==========================================================================
   Calendar Render Engine
   ========================================================================== */
function renderCalendar() {
    const calendarGrid = document.getElementById('calendar-grid');
    const calendarTitle = document.getElementById('calendar-title');
    calendarGrid.innerHTML = '';
    
    const year = currentCalendarDate.getFullYear();
    const month = currentCalendarDate.getMonth();
    
    calendarTitle.innerText = currentCalendarDate.toLocaleDateString(undefined, {month: 'long', year: 'numeric'});
    
    const firstDay = new Date(year, month, 1).getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    
    // Render blank leading cells
    for (let i = 0; i < firstDay; i++) {
        const emptyCell = document.createElement('div');
        emptyCell.className = 'calendar-day empty';
        calendarGrid.appendChild(emptyCell);
    }
    
    const today = new Date();
    
    // Render active day cells
    for (let day = 1; day <= daysInMonth; day++) {
        const cell = document.createElement('div');
        cell.className = 'calendar-day';
        
        const dateStr = `${year}-${String(month+1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
        
        // Highlight today
        if (today.getFullYear() === year && today.getMonth() === month && today.getDate() === day) {
            cell.classList.add('today');
        }
        
        // Filter jobs having interview scheduled for this date
        const dayInterviews = allJobs.filter(j => j.interviewDate === dateStr);
        
        if (dayInterviews.length > 0) {
            cell.classList.add('has-interview');
            cell.innerHTML = `
                <span class="calendar-day-num">${day}</span>
                <span class="calendar-day-indicator"></span>
            `;
            cell.addEventListener('click', () => showCalendarDetails(dateStr, dayInterviews));
        } else {
            cell.innerHTML = `<span class="calendar-day-num">${day}</span>`;
            cell.addEventListener('click', () => showCalendarDetails(dateStr, []));
        }
        
        calendarGrid.appendChild(cell);
    }
}

// Show interviews scheduled for selected calendar date
function showCalendarDetails(dateStr, interviews) {
    const detailsCard = document.getElementById('calendar-details-card');
    const selectedDateEl = document.getElementById('calendar-selected-date');
    const listEl = document.getElementById('calendar-interviews-list');
    
    detailsCard.classList.remove('hidden');
    
    const formattedSelectedDate = new Date(dateStr).toLocaleDateString(undefined, {month: 'long', day: 'numeric', year: 'numeric'});
    selectedDateEl.innerText = formattedSelectedDate;
    
    listEl.innerHTML = '';
    
    if (interviews.length === 0) {
        listEl.innerHTML = `<p style="font-size:14px; color:var(--text-muted);">No interviews scheduled on this day.</p>`;
        return;
    }
    
    interviews.forEach(job => {
        const item = document.createElement('div');
        item.className = 'calendar-interview-item';
        
        // Get details
        item.innerHTML = `
            <div>
                <strong style="color:var(--text-primary); font-size:15px;">${escapeHtml(job.companyName)}</strong>
                <div style="font-size:12px; color:var(--text-secondary); margin-top:2px;">${escapeHtml(job.role)}</div>
            </div>
            <div style="display:flex; align-items:center; gap:8px;">
                <span class="badge badge-interviewing" style="font-size:10px;">${job.status}</span>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
            </div>
        `;
        
        item.addEventListener('click', () => {
            openEditModal(job.id);
        });
        
        listEl.appendChild(item);
    });
}

function setupCalendarNavigation() {
    document.getElementById('prev-month-btn').addEventListener('click', () => {
        currentCalendarDate.setMonth(currentCalendarDate.getMonth() - 1);
        renderCalendar();
    });
    
    document.getElementById('next-month-btn').addEventListener('click', () => {
        currentCalendarDate.setMonth(currentCalendarDate.getMonth() + 1);
        renderCalendar();
    });
}

/* ==========================================================================
   Analytics Dashboard Render Engine (Chart.js)
   ========================================================================== */
function renderAnalytics() {
    // Clear old charts to avoid redraw issues
    Object.keys(chartInstances).forEach(key => {
        if (chartInstances[key]) chartInstances[key].destroy();
    });

    if (allJobs.length === 0) {
        return;
    }

    // Chart Options Configuration
    const baseChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                labels: {
                    color: '#9ca3af',
                    font: { family: 'Inter', size: 12 }
                }
            }
        },
        scales: {
            x: {
                grid: { color: 'rgba(255,255,255,0.05)' },
                ticks: { color: '#9ca3af' }
            },
            y: {
                grid: { color: 'rgba(255,255,255,0.05)' },
                ticks: { color: '#9ca3af', precision: 0 }
            }
        }
    };

    // 1. Applications per Month (Line Chart)
    const monthlyCounts = {};
    allJobs.forEach(job => {
        if (job.appliedDate) {
            const date = new Date(job.appliedDate);
            // Group by Year-Month e.g. "2026-06"
            const label = date.toLocaleDateString(undefined, { month: 'short', year: '2-digit' });
            monthlyCounts[label] = (monthlyCounts[label] || 0) + 1;
        }
    });

    const monthlyLabels = Object.keys(monthlyCounts);
    const monthlyValues = Object.values(monthlyCounts);

    const ctxMonthly = document.getElementById('chart-monthly').getContext('2d');
    chartInstances['monthly'] = new Chart(ctxMonthly, {
        type: 'line',
        data: {
            labels: monthlyLabels.length > 0 ? monthlyLabels : ['No Data'],
            datasets: [{
                label: 'Applications',
                data: monthlyValues.length > 0 ? monthlyValues : [0],
                borderColor: '#6366f1',
                backgroundColor: 'rgba(99, 102, 241, 0.15)',
                borderWidth: 2,
                fill: true,
                tension: 0.3
            }]
        },
        options: baseChartOptions
    });

    // 2. Success Rate Breakdown (Doughnut Chart)
    const statusCounts = {
        'applied': 0,
        'interviewing': 0,
        'offered': 0,
        'rejected': 0
    };
    
    allJobs.forEach(job => {
        const stat = job.status.toLowerCase();
        if (statusCounts[stat] !== undefined) {
            statusCounts[stat]++;
        }
    });

    const ctxSuccess = document.getElementById('chart-success').getContext('2d');
    chartInstances['success'] = new Chart(ctxSuccess, {
        type: 'doughnut',
        data: {
            labels: ['Applied', 'Interviewing', 'Offered', 'Rejected'],
            datasets: [{
                data: [statusCounts.applied, statusCounts.interviewing, statusCounts.offered, statusCounts.rejected],
                backgroundColor: [
                    '#3b82f6', // applied - Blue
                    '#f59e0b', // interviewing - Yellow
                    '#10b981', // offered - Green
                    '#ef4444'  // rejected - Red
                ],
                borderWidth: 1,
                borderColor: 'rgba(25, 33, 56, 0.45)'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'right',
                    labels: { color: '#9ca3af', font: { family: 'Inter', size: 12 } }
                }
            }
        }
    });

    // 3. Company-wise Breakdown (Horizontal Bar Chart)
    const companyCounts = {};
    allJobs.forEach(job => {
        const comp = job.companyName.trim();
        companyCounts[comp] = (companyCounts[comp] || 0) + 1;
    });

    // Sort companies by count descending and take top 5
    const topCompanies = Object.entries(companyCounts)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5);

    const companyLabels = topCompanies.map(c => c[0]);
    const companyValues = topCompanies.map(c => c[1]);

    const ctxCompany = document.getElementById('chart-company').getContext('2d');
    chartInstances['company'] = new Chart(ctxCompany, {
        type: 'bar',
        data: {
            labels: companyLabels.length > 0 ? companyLabels : ['No Data'],
            datasets: [{
                label: 'Applications Count',
                data: companyValues.length > 0 ? companyValues : [0],
                backgroundColor: 'rgba(6, 182, 212, 0.5)',
                borderColor: '#06b6d4',
                borderWidth: 1.5,
                borderRadius: 4
            }]
        },
        options: {
            indexAxis: 'y', // Makes it horizontal
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                x: {
                    grid: { color: 'rgba(255,255,255,0.05)' },
                    ticks: { color: '#9ca3af', precision: 0 }
                },
                y: {
                    grid: { display: false },
                    ticks: { color: '#9ca3af' }
                }
            }
        }
    });
}

/* ==========================================================================
   Page Setup & Events bindings
   ========================================================================== */
document.addEventListener('DOMContentLoaded', () => {
    // Populate username display
    if (session) {
        document.getElementById('nav-username').innerText = session.username;
        document.getElementById('user-initial').innerText = session.username.charAt(0).toUpperCase();
        fetchJobs();
    }

    // Tab view listeners
    setupTabNavigation();

    // Calendar Navigation Setup
    setupCalendarNavigation();

    // Modal events
    document.getElementById('addJobBtn').addEventListener('click', openAddModal);
    document.getElementById('closeModalBtn').addEventListener('click', closeModal);
    document.getElementById('cancelModalBtn').addEventListener('click', closeModal);
    document.getElementById('jobForm').addEventListener('submit', handleFormSubmit);

    document.getElementById('logoutBtn').addEventListener('click', logout);

    // Filter events
    const filterBtns = document.querySelectorAll('.filter-btn');
    filterBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            filterBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            renderJobs();
        });
    });

    // Dashboard search live input event listeners
    if (document.getElementById('dashboardSearch')) {
        document.getElementById('dashboardSearch').addEventListener('input', () => renderJobs());
    }
    if (document.getElementById('dashboardLocationSearch')) {
        document.getElementById('dashboardLocationSearch').addEventListener('input', () => renderJobs());
    }

    // Job Openings search listener
    if (document.getElementById('searchOpeningsBtn')) {
        document.getElementById('searchOpeningsBtn').addEventListener('click', searchOpenings);
        
        // Trigger search on enter key in search inputs
        const triggerSearchOnEnter = (e) => {
            if (e.key === 'Enter') searchOpenings();
        };
        document.getElementById('openingsSearchRole').addEventListener('keydown', triggerSearchOnEnter);
        document.getElementById('openingsSearchLoc').addEventListener('keydown', triggerSearchOnEnter);
    }

    // Initialize Light/Dark theme toggle
    initTheme();

    // Profile / Saved Jobs Modal event listeners
    const userBadge = document.querySelector('.user-badge');
    const profileOverlay = document.getElementById('profile-modal-overlay');
    const closeProfileBtn = document.getElementById('closeProfileModalBtn');
    const closeProfileFooterBtn = document.getElementById('closeProfileModalFooterBtn');

    if (userBadge && profileOverlay) {
        userBadge.addEventListener('click', () => {
            profileOverlay.classList.add('show');
            renderSavedJobs();
        });
        
        const closeProfileModal = () => {
            profileOverlay.classList.remove('show');
        };
        
        if (closeProfileBtn) {
            closeProfileBtn.addEventListener('click', closeProfileModal);
        }
        if (closeProfileFooterBtn) {
            closeProfileFooterBtn.addEventListener('click', closeProfileModal);
        }
        
        profileOverlay.addEventListener('click', (e) => {
            if (e.target === profileOverlay) {
                closeProfileModal();
            }
        });
    }

    // Resume drag & drop and file pick listeners
    const fileInput = document.getElementById('resumeFile');
    const uploadZone = document.getElementById('resume-upload-zone');
    
    uploadZone.addEventListener('click', () => fileInput.click());
    
    uploadZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadZone.style.borderColor = 'var(--primary)';
        uploadZone.style.background = 'rgba(99, 102, 241, 0.08)';
    });

    uploadZone.addEventListener('dragleave', () => {
        uploadZone.style.borderColor = 'var(--card-border)';
        uploadZone.style.background = 'transparent';
    });

    uploadZone.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadZone.style.borderColor = 'var(--card-border)';
        uploadZone.style.background = 'transparent';
        
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            const file = files[0];
            const isPdf = file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf';
            if (isPdf) {
                handleResumeUpload(file);
            } else {
                showAlert('Please drop a valid PDF file', 'error');
            }
        }
    });

    fileInput.addEventListener('change', (e) => {
        if (fileInput.files.length > 0) {
            const file = fileInput.files[0];
            const isPdf = file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf';
            if (isPdf) {
                handleResumeUpload(file);
            } else {
                showAlert('Please select a valid PDF file', 'error');
            }
        }
    });

    // Resume download listener
    document.getElementById('download-resume-btn').addEventListener('click', handleResumeDownload);

    // Resume delete listener
    document.getElementById('delete-resume-btn').addEventListener('click', handleResumeDelete);

    // Test email listener
    document.getElementById('test-email-btn').addEventListener('click', handleSendTestEmail);
});

/* ==========================================================================
   Light/Dark Mode Theme Initialization and Switcher
   ========================================================================== */
function initTheme() {
    const themeToggleBtn = document.getElementById('themeToggleBtn');
    const sunIcon = document.getElementById('theme-sun-icon');
    const moonIcon = document.getElementById('theme-moon-icon');
    
    if (!themeToggleBtn) return;

    // Check saved theme
    const savedTheme = localStorage.getItem('theme');
    const systemPrefersLight = window.matchMedia('(prefers-color-scheme: light)').matches;
    
    if (savedTheme === 'light' || (!savedTheme && systemPrefersLight)) {
        document.body.classList.add('light-theme');
        sunIcon.classList.add('hidden');
        moonIcon.classList.remove('hidden');
    } else {
        document.body.classList.remove('light-theme');
        sunIcon.classList.remove('hidden');
        moonIcon.classList.add('hidden');
    }

    themeToggleBtn.addEventListener('click', () => {
        document.body.classList.toggle('light-theme');
        const isLight = document.body.classList.contains('light-theme');
        localStorage.setItem('theme', isLight ? 'light' : 'dark');
        
        if (isLight) {
            sunIcon.classList.add('hidden');
            moonIcon.classList.remove('hidden');
        } else {
            sunIcon.classList.remove('hidden');
            moonIcon.classList.add('hidden');
        }
    });
}

/* ==========================================================================
   Job Openings Search and Save Module
   ========================================================================== */
let currentOpenings = []; // Store search results globally

async function searchOpenings() {
    const role = document.getElementById('openingsSearchRole').value.trim();
    const location = document.getElementById('openingsSearchLoc').value.trim();
    const listEl = document.getElementById('openings-list');
    
    listEl.innerHTML = `
        <div class="empty-state glass">
            <span class="spinner"></span>
            <h3>Searching Openings...</h3>
            <p>Fetching latest job openings from database & API...</p>
        </div>
    `;

    try {
        const response = await fetch(`${API_OPENINGS}?role=${encodeURIComponent(role)}&location=${encodeURIComponent(location)}`, {
            headers: {
                'Authorization': `Bearer ${session.token}`
            }
        });

        if (response.ok) {
            currentOpenings = await response.json();
            renderOpenings(currentOpenings);
        } else {
            showAlert('Failed to retrieve job openings', 'error');
            listEl.innerHTML = `
                <div class="empty-state glass">
                    <h3>Search Failed</h3>
                    <p>There was an error communicating with the job search service.</p>
                </div>
            `;
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred during job search', 'error');
        listEl.innerHTML = `
            <div class="empty-state glass">
                <h3>Search Error</h3>
                <p>Could not connect to the job search server.</p>
            </div>
        `;
    }
}

function renderOpenings(openings = []) {
    const listEl = document.getElementById('openings-list');
    listEl.innerHTML = '';

    if (openings.length === 0) {
        listEl.innerHTML = `
            <div class="empty-state glass">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="8" y1="12" x2="16" y2="12"></line>
                </svg>
                <h3>No Openings Found</h3>
                <p>Try refining your search terms or location.</p>
            </div>
        `;
        return;
    }

    openings.forEach((job, idx) => {
        const card = document.createElement('div');
        card.className = 'job-card glass';

        let locationSalaryHtml = '';
        if (job.location || job.salary) {
            locationSalaryHtml = `
                <div style="font-size: 11px; color: var(--text-secondary); margin-top: 4px; display: flex; gap: 8px;">
                    ${job.location ? `<span>📍 ${escapeHtml(job.location)}</span>` : ''}
                    ${job.salary ? `<span>💰 ${escapeHtml(job.salary)}</span>` : ''}
                </div>
            `;
        }

        card.innerHTML = `
            <div class="job-main-info" style="grid-column: span 2;">
                <span class="job-company">${escapeHtml(job.companyName)}</span>
                <span class="job-role">${escapeHtml(job.role)}</span>
                ${locationSalaryHtml}
            </div>
            <div>
                <span class="badge" style="background: rgba(99, 102, 241, 0.1); color: var(--primary); border: 1px solid rgba(99, 102, 241, 0.25);">
                    ${escapeHtml(job.source)}
                </span>
            </div>
            <div>
                <a href="${escapeHtml(job.url)}" target="_blank" class="btn btn-secondary" style="padding: 6px 12px; font-size: 12px; height: 32px; width: fit-content; text-decoration: none; display: inline-flex; align-items: center; gap: 4px;">
                    View Job ↗
                </a>
            </div>
            <div class="job-actions" style="justify-content: flex-end; gap: 8px;">
                <button class="btn btn-secondary" onclick="saveOpening(${idx}, false)" style="padding: 6px 12px; font-size: 12px; height: 32px;">Save Job</button>
                <button class="btn btn-primary" onclick="saveOpening(${idx}, true)" style="padding: 6px 12px; font-size: 12px; height: 32px; width: auto;">Mark Applied</button>
            </div>
        `;
        listEl.appendChild(card);
    });
}

async function saveOpening(index, isApplied) {
    if (!session) return;
    const opening = currentOpenings[index];
    if (!opening) return;

    const payload = {
        companyName: opening.companyName,
        role: opening.role,
        status: isApplied ? "Applied" : "Saved",
        appliedDate: new Date().toISOString().split('T')[0],
        interviewDate: null,
        notes: `Saved from ${opening.source}. Job Link: ${opening.url}`,
        salary: opening.salary,
        location: opening.location,
        jobDescription: "",
        interviewNotes: ""
    };

    if (!isApplied) {
        payload.notes = `Saved opportunity from ${opening.source}. Link: ${opening.url} (Not yet applied)`;
    }

    try {
        const response = await fetch(`${API_JOBS}?userId=${session.userId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${session.token}`
            },
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            showAlert(isApplied ? `Successfully marked "${opening.role}" as Applied!` : `Successfully saved "${opening.role}" to Saved Opportunities!`, 'success');
            fetchJobs(); // reload user's tracker
        } else {
            showAlert('Failed to save job opening to tracker', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred while saving', 'error');
    }
}

/* ==========================================================================
   Saved Jobs / Opportunities Module
   ========================================================================== */
function renderSavedJobs() {
    const listEl = document.getElementById('profile-saved-jobs-list');
    if (!listEl) return;
    listEl.innerHTML = '';

    const savedJobs = allJobs.filter(j => j.status.toLowerCase() === 'saved');

    if (savedJobs.length === 0) {
        listEl.innerHTML = `
            <div class="empty-state glass" style="padding: 20px;">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="width: 36px; height: 36px; stroke: var(--text-secondary); margin: 0 auto;">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="8" y1="12" x2="16" y2="12"></line>
                </svg>
                <h4 style="margin: 8px 0 4px; font-size: 15px; color: var(--text-primary);">No Saved Opportunities</h4>
                <p style="font-size: 12px; color: var(--text-secondary);">Go to "Job Openings" to search and bookmark opportunities.</p>
            </div>
        `;
        return;
    }

    savedJobs.forEach(job => {
        const card = document.createElement('div');
        card.className = 'saved-job-card glass';
        card.style.cssText = `
            padding: 16px; 
            border: 1px solid var(--card-border); 
            border-radius: 12px; 
            display: flex; 
            flex-direction: column; 
            gap: 12px; 
            background: rgba(255, 255, 255, 0.02);
            transition: all 0.2s ease;
        `;

        let locationSalaryHtml = '';
        if (job.location || job.salary) {
            locationSalaryHtml = `
                <div style="font-size: 11px; color: var(--text-secondary); margin-top: 4px; display: flex; gap: 8px; flex-wrap: wrap;">
                    ${job.location ? `<span>📍 ${escapeHtml(job.location)}</span>` : ''}
                    ${job.salary ? `<span>💰 ${escapeHtml(job.salary)}</span>` : ''}
                </div>
            `;
        }

        let sourceTagHtml = '';
        let urlLinkHtml = '';
        if (job.notes) {
            if (job.notes.includes('The Muse API')) {
                sourceTagHtml = `<span class="badge" style="font-size: 10px; padding: 2px 6px; background: rgba(99, 102, 241, 0.1); color: var(--primary); border: 1px solid rgba(99, 102, 241, 0.25);">The Muse API</span>`;
            } else if (job.notes.includes('Himalayas API')) {
                sourceTagHtml = `<span class="badge" style="font-size: 10px; padding: 2px 6px; background: rgba(99, 102, 241, 0.1); color: var(--primary); border: 1px solid rgba(99, 102, 241, 0.25);">Himalayas API</span>`;
            } else if (job.notes.includes('Premium Database')) {
                sourceTagHtml = `<span class="badge" style="font-size: 10px; padding: 2px 6px; background: rgba(99, 102, 241, 0.1); color: var(--primary); border: 1px solid rgba(99, 102, 241, 0.25);">Premium Database</span>`;
            }
            
            const linkMatch = job.notes.match(/https?:\/\/[^\s\)]+/);
            if (linkMatch) {
                urlLinkHtml = `
                    <a href="${escapeHtml(linkMatch[0])}" target="_blank" class="btn btn-secondary" style="padding: 4px 8px; font-size: 11px; height: 26px; text-decoration: none; display: inline-flex; align-items: center; gap: 2px; width: fit-content;">
                        View Job ↗
                    </a>
                `;
            }
        }

        card.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 12px;">
                <div style="display: flex; flex-direction: column; text-align: left;">
                    <span style="font-weight: 600; font-size: 15px; color: var(--text-primary);">${escapeHtml(job.companyName)}</span>
                    <span style="font-weight: 500; font-size: 13px; color: var(--primary); margin-top: 2px;">${escapeHtml(job.role)}</span>
                    ${locationSalaryHtml}
                </div>
                <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 6px; flex-shrink: 0;">
                    ${sourceTagHtml}
                    ${urlLinkHtml}
                </div>
            </div>
            
            <div style="display: flex; justify-content: flex-end; gap: 8px; border-top: 1px dashed var(--card-border); padding-top: 10px; margin-top: 4px;">
                <button class="btn btn-secondary" onclick="deleteSavedJob('${job.id}')" style="padding: 6px 10px; font-size: 11px; height: 28px; display: inline-flex; align-items: center; gap: 4px;">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                    Delete
                </button>
                <button class="btn btn-primary" onclick="markSavedJobAsApplied('${job.id}')" style="padding: 6px 10px; font-size: 11px; height: 28px; display: inline-flex; align-items: center; gap: 4px; width: auto;">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg>
                    Mark Applied
                </button>
            </div>
        `;
        listEl.appendChild(card);
    });
}

async function markSavedJobAsApplied(id) {
    if (!session) return;
    const existing = allJobs.find(j => j.id === id);
    if (!existing) return;

    const payload = {
        companyName: existing.companyName,
        role: existing.role,
        status: "Applied",
        appliedDate: new Date().toISOString().split('T')[0],
        interviewDate: existing.interviewDate,
        notes: existing.notes ? existing.notes.replace("(Not yet applied)", "").trim() : "",
        salary: existing.salary,
        location: existing.location,
        jobDescription: existing.jobDescription,
        interviewNotes: existing.interviewNotes,
        resumePath: existing.resumePath,
        resumeFilename: existing.resumeFilename,
        resumeScore: existing.resumeScore,
        aiAnalysisJson: existing.aiAnalysisJson
    };

    try {
        const response = await fetch(`${API_JOBS}/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${session.token}`
            },
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            showAlert(`Successfully marked "${existing.role}" as Applied!`, 'success');
            await fetchJobs();
            renderSavedJobs();
        } else {
            showAlert('Failed to update job status', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred', 'error');
    }
}

async function deleteSavedJob(id) {
    if (!session) return;
    if (!confirm('Are you sure you want to delete this saved opportunity?')) return;

    try {
        const response = await fetch(`${API_JOBS}/${id}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${session.token}`
            }
        });

        if (response.ok) {
            showAlert('Saved opportunity deleted', 'success');
            await fetchJobs();
            renderSavedJobs();
        } else {
            showAlert('Failed to delete saved opportunity', 'error');
        }
    } catch (err) {
        console.error(err);
        showAlert('Network error occurred', 'error');
    }
}

// Bind to window to allow access from onclick handler attributes
window.renderSavedJobs = renderSavedJobs;
window.markSavedJobAsApplied = markSavedJobAsApplied;
window.deleteSavedJob = deleteSavedJob;
