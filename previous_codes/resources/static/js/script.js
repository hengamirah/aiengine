console.log("Static resources base URL:", window.location.origin);

document.addEventListener('DOMContentLoaded', function() {
    // Wait for DOM to be fully loaded before adding event listeners
    const form = document.getElementById('verification-form');
    if (form) {
        console.log("✅ Form found and event listener attached");
        form.addEventListener('submit', handleFormSubmit);
    } else {
        console.error('❌ Verification form not found');
    }
    
    // Add file preview listeners
    const fileInputs = document.querySelectorAll('input[type="file"]');
    fileInputs.forEach(input => {
        input.addEventListener('change', function(e) {
            const file = e.target.files[0];
            const previewDiv = document.getElementById(e.target.id + '_preview');
            
            if (previewDiv) {
                previewDiv.innerHTML = ''; // Clear previous preview
                
                if (file && file.type.startsWith('image/')) {
                    const img = document.createElement('img');
                    img.src = URL.createObjectURL(file);
                    img.style.maxWidth = '200px';
                    img.style.maxHeight = '200px';
                    img.style.margin = '10px 0';
                    img.style.border = '1px solid #ddd';
                    img.style.borderRadius = '4px';
                    previewDiv.appendChild(img);
                }
            }
        });
    });
});

async function handleFormSubmit(e) {
    e.preventDefault();
    
    console.log("🚀 Form submission started");
    
    // Show loading state
    const submitBtn = document.querySelector('button[type="submit"]');
    if (!submitBtn) {
        console.error('❌ Submit button not found');
        return;
    }
    
    const originalText = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = 'Processing...';
    
    // Show loading spinner
    const loadingElement = document.getElementById('loading');
    if (loadingElement) {
        loadingElement.classList.remove('d-none');
    }
    
    try {
        const formData = new FormData(e.target);
        
        console.log("📤 Sending request to /verify");
        const response = await fetch('verify', {
            method: 'POST',
            body: formData
        });
        
        console.log("📥 Response status:", response.status);
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error("❌ HTTP Error:", response.status, errorText);
            throw new Error(`HTTP error! status: ${response.status} - ${errorText}`);
        }
        
        const result = await response.json();
        console.log("📊 Full response:", result);
        
        if (result.status === 'success') {
            console.log("✅ Success! Processing results...");
            
            // Debug: Log the files object
            console.log("📁 Files object:", result.files);
            console.log("📁 Files keys:", Object.keys(result.files || {}));
            
            // Show results section
            showResultsSection();
            
            // Display all results
            displayAllResults(result);
            
            // Display verification results if available
            if (result.verification) {
                console.log("👤 Displaying verification results");
                displayVerificationResults(result.verification);
            }
        } else {
            console.error("❌ Verification failed:", result.message);
            alert('Verification failed: ' + (result.message || 'Unknown error'));
        }
        
    } catch (error) {
        console.error('💥 Error during submission:', error);
        alert('An error occurred during verification. Please check the console and try again.');
    } finally {
        // Restore button state
        submitBtn.disabled = false;
        submitBtn.textContent = originalText;
        
        // Hide loading spinner
        if (loadingElement) {
            loadingElement.classList.add('d-none');
        }
    }
}

function showResultsSection() {
    const resultsSection = document.getElementById('results-section');
    if (resultsSection) {
        console.log("📱 Showing results section");
        resultsSection.classList.remove('d-none');
        resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } else {
        console.error("❌ Results section not found in DOM");
    }
}

// Move this function UP in the file, before displayAllResults
function displayUploadedFiles(files) {
    console.log("📁 displayUploadedFiles called with:", files);
    
    const resultsContainer = document.getElementById('file-results');
    if (!resultsContainer) return;

    let html = '';
    
    for (const [fileType, file] of Object.entries(files)) {
        if (file && file.path) {
            let imageUrl;
            let statusHtml = '';
            let detectionText = 'No Detection';
            
            // ✅ FIXED: Handle person detection properly
            if (file.has_detection) {
                // Customize detection text based on file type
                if (fileType === 'photo') {
                    detectionText = 'Person Detected';
                    // Use person crop if available, otherwise detection image
                    imageUrl = file.person_crop_url || file.detection_image_data || file.detection_url || file.url;
                } else if (fileType === 'id_front' || fileType === 'id_back') {
                    detectionText = 'ID Detected';
                    imageUrl = file.detection_image_data || file.detection_url || file.url;
                } else {
                    detectionText = 'Object Detected';
                    imageUrl = file.detection_image_data || file.detection_url || file.url;
                }
                
                statusHtml = `<span class="badge bg-success">${detectionText}</span>`;
                
                // Add blur classification if available
                if (file.blur_classification) {
                    const blurResult = file.blur_classification;
                    const isBlurry = blurResult.class_name === 'blur';
                    const blurConfidence = parseFloat(blurResult.confidence) * 100;
                    
                    statusHtml += `
                        <div class="mt-2">
                            <span class="badge ${isBlurry ? 'bg-warning' : 'bg-info'}">
                                ${isBlurry ? '⚠️ BLURRY' : '✓ CLEAR'} 
                                (${blurConfidence.toFixed(1)}%)
                            </span>
                        </div>
                    `;
                }
            } else {
                // No detection found
                if (fileType === 'photo') {
                    detectionText = 'No Person Detected';
                } else if (fileType === 'id_front' || fileType === 'id_back') {
                    detectionText = 'No ID Detected';
                }
                
                statusHtml = `<span class="badge bg-warning">${detectionText}</span>`;
                imageUrl = file.url; // Use original upload
            }
            
            // Format display name
            const displayName = fileType
                .replace(/_/g, ' ')
                .replace(/\b\w/g, c => c.toUpperCase());
            
            html += `
                <div class="col-md-6 mb-3">
                    <div class="card">
                        <div class="card-header bg-light">
                            <h6 class="mb-0">${displayName}</h6>
                        </div>
                        <div class="card-body">
                            <div class="text-center mb-3">
                                <img src="${imageUrl}" 
                                     class="img-fluid detection-image border" 
                                     alt="${displayName}"
                                     style="max-height: 250px; object-fit: contain;"
                                     onerror="console.error('Failed to load image:', this.src); this.src='${file.url}';">
                            </div>
                            <div class="text-center">
                                ${statusHtml}
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }
    }
    
    resultsContainer.innerHTML = html || '<div class="col-12"><p>No files uploaded</p></div>';
}

// ✅ NOW this function can find displayUploadedFiles above
function displayAllResults(result) {
    console.log("📊 Displaying all verification results");
    debugImagePaths(result);
    
    console.log("🔍 Cropped elements:", result.cropped_elements);
    
    // 1. Display uploaded files and detection images
    if (result.files) {
        displayUploadedFiles(result.files);
    }
    
    // 2. Display crop images (ID elements)
    if (result.id_detections) {
        displayCropImages(result.id_detections);
    }
    
    // 3. Display face comparison results
    if (result.face_comparison) {
        // Use the specific URLs from the backend response
        const idPhotoUrl = result.detected_person_url || result.cropped_elements?.person_from_id;
        const selfieUrl = result.uploaded_person_url || result.cropped_elements?.person_from_photo || result.files?.photo?.url;
        
        console.log("👤 Face comparison URLs:", { idPhotoUrl, selfieUrl });
        
        displayFaceComparisonResults(result.face_comparison, idPhotoUrl, selfieUrl);
    }
    
    // 4. COMMENTED OUT: Signature comparison results
    /*
    if (result.signature_comparison) {
        console.log("🐛 DEBUG: Signature comparison detected");
        console.log("🐛 Available cropped elements:", result.cropped_elements);
        console.log("🐛 Direct URLs in response:");
        console.log("  - id_signature_url:", result.id_signature_url);
        console.log("  - uploaded_signature_url:", result.uploaded_signature_url);
        
        // Use the specific URLs from the backend response with fallbacks
        const idSignatureUrl = result.id_signature_url || 
                              result.cropped_elements?.signature || 
                              result.cropped_elements?.["signature_from_id"];
                              
        const userSignatureUrl = result.uploaded_signature_url || 
                                result.cropped_elements?.uploaded_signature || 
                                result.files?.signature?.url;
        
        console.log("✍️ Final signature URLs:", { idSignatureUrl, userSignatureUrl });
        
        if (!idSignatureUrl) {
            console.error("❌ ID signature URL is missing!");
            // Try to find it in the crop results section
            const cropSignatureImg = document.querySelector('#crop-results img[alt="Signature"]');
            if (cropSignatureImg) {
                console.log("🔍 Found signature in crop results, using:", cropSignatureImg.src);
                idSignatureUrl = cropSignatureImg.src;
            }
        }
        
        displaySignatureComparisonResults(
            result.signature_comparison,
            idSignatureUrl,
            userSignatureUrl
        );
    }
    */
}

// Add this debug function
function debugImagePaths(result) {
    console.log("🐛 DEBUG: All available image paths");
    console.log("📁 Files:", result.files);
    console.log("✂️ Cropped elements:", result.cropped_elements);
    console.log("🔗 Direct URLs:");
    console.log("  - detected_person_url:", result.detected_person_url);
    console.log("  - uploaded_person_url:", result.uploaded_person_url);
    console.log("  - id_signature_url:", result.id_signature_url);
    console.log("  - uploaded_signature_url:", result.uploaded_signature_url);
}

// Function to display crop images
function displayCropImages(idDetections) {
    console.log("🔍 Displaying cropped elements");
    
    const cropContainer = document.getElementById('crop-results');
    if (!cropContainer) return;
    
    let html = '';
    let cropsFound = false;
    
    // Process all detections from all documents
    for (const [docType, detections] of Object.entries(idDetections)) {
        if (!Array.isArray(detections)) continue;
        
        for (const detection of detections) {
            // REMOVED: Skip signature crops - now signatures will be shown
            // if (detection.class_name && detection.class_name.toLowerCase() === 'signature') {
            //     console.log("⏭️ Skipping signature crop");
            //     continue;
            // }
            
            // Only process if it's a crop with a path
            if (detection.class_name && detection.crop_path) {
                cropsFound = true;
                
                // Use web_path if available, otherwise construct it
                let imageSrc;
                if (detection.web_path) {
                    imageSrc = detection.web_path;
                } else {
                    // Fallback: construct path from crop_path
                    imageSrc = '/' + detection.crop_path.replace(/\\/g, '/').replace('static/', '');
                }
                
                // For debugging
                console.log(`Crop image path: ${imageSrc}`);
                
                html += `
                <div class="col-md-4 mb-3">
                    <div class="card h-100">
                        <div class="card-header bg-light">
                            <h6 class="mb-0">${detection.class_name} from ${docType}</h6>
                        </div>
                        <div class="card-body text-center">
                            <img src="${imageSrc}" 
                                 class="img-fluid" 
                                 alt="${detection.class_name}"
                                 style="max-height: 200px; object-fit: contain;"
                                 onerror="console.error('Failed to load crop image:', this.src); this.style.display='none';">
                            <div class="mt-2">
                                <span class="badge bg-primary">
                                    ${(detection.confidence * 100).toFixed(1)}% confidence
                                </span>
                            </div>
                        </div>
                    </div>
                </div>`;
            }
        }
    }
    
    if (!cropsFound) {
        html = '<div class="col-12"><p class="text-center">No cropped elements found</p></div>';
    }
    
    cropContainer.innerHTML = html;
}

// Function to display face comparison results
function displayFaceComparisonResults(comparison, idPhotoUrl, selfieUrl) {
    console.log("👤 Displaying face comparison results");
    console.log("📸 ID Photo URL:", idPhotoUrl);
    console.log("📸 Selfie URL:", selfieUrl);
    
    const container = document.getElementById('face-comparison-result');
    if (!container) return;
    
    // Fallback if URLs are missing
    if (!idPhotoUrl) {
        console.warn("⚠️ ID photo URL missing, trying to find in crops");
        const cropImage = document.querySelector('#crop-results img[alt*="Person"]');
        if (cropImage) {
            idPhotoUrl = cropImage.src;
        }
    }
    
    if (!selfieUrl) {
        console.warn("⚠️ Selfie URL missing");
        selfieUrl = '/static/img/placeholder.png'; // Add a placeholder
    }
    
    // Get values from DeepFace result format - USE DISTANCE AND THRESHOLD
    const isMatch = comparison.is_same_person || comparison.verified || false;
    const distance = comparison.distance || 0;
    const threshold = comparison.threshold || 0.6;
    const resultImagePath = comparison.result_image_path || comparison.comparison_image_url;
    
    let html = `
    <div class="row">
        <div class="col-md-6">
            <div class="d-flex justify-content-between mb-3">
                <div class="text-center mx-2">
                    <h6>ID Photo</h6>
                    <img src="${idPhotoUrl}" class="img-thumbnail" style="height: 150px; object-fit: contain;" 
                         onerror="console.error('Failed to load ID photo:', this.src); this.style.display='none';">
                </div>
                <div class="text-center mx-2">
                    <h6>Selfie</h6>
                    <img src="${selfieUrl}" class="img-thumbnail" style="height: 150px; object-fit: contain;"
                         onerror="console.error('Failed to load selfie:', this.src); this.style.display='none';">
                </div>
            </div>
            
            <div class="card ${isMatch ? 'border-success' : 'border-danger'}">
                <div class="card-body">
                    <h5 class="card-title">
                        <span class="badge ${isMatch ? 'bg-success' : 'bg-danger'} fs-6">
                            ${isMatch ? '✅ MATCH' : '❌ NO MATCH'}
                        </span>
                    </h5>
                    <p><strong>Description:</strong> ${distance < threshold ? 
                        'If distance < threshold, images are similar' : 
                        'If distance > threshold, images are different'}</p>
                </div>
            </div>
        </div>
        
        <div class="col-md-6">
            <h6>Comparison Result</h6>
            <img src="${resultImagePath ? '/' + resultImagePath.replace('static/', '') : comparison.comparison_image_url}" 
                 class="img-fluid border" 
                 alt="Face Comparison"
                 style="max-height: 300px; object-fit: contain;"
                 onerror="console.error('Failed to load comparison image:', this.src); this.style.display='none';">
        </div>
    </div>`;
    
    container.innerHTML = html;
}

// Function to display signature comparison results
function displaySignatureComparisonResults(comparison, idSignatureUrl, userSignatureUrl) {
    console.log("✍️ Displaying signature comparison results:", comparison);
    console.log("ID Signature URL:", idSignatureUrl);
    console.log("User Signature URL:", userSignatureUrl);
    // Ensure we have valid URLs by looking in multiple places
    const container = document.getElementById('signature-comparison-result');
    if (!container) {
        console.error("❌ signature-comparison-result container not found");
        return;
    }
    if (!idSignatureUrl) {
        console.warn("⚠️ ID signature URL missing, searching in crop results...");
        const cropImage = document.querySelector('#crop-results img[alt="Signature"]');
        if (cropImage) {
            idSignatureUrl = cropImage.src;
            console.log("✅ Found signature in crops:", idSignatureUrl);
        } else {
            console.error("❌ Could not find signature image anywhere");
        }
    }
    // Use placeholder if still missing
    if (!idSignatureUrl) {
        idSignatureUrl = '/static/img/signature-placeholder.png';
        console.warn("⚠️ Using placeholder for ID signature");
    }
    
    if (!userSignatureUrl) {
        userSignatureUrl = '/static/img/signature-placeholder.png';
        console.warn("⚠️ Using placeholder for user signature");
    }
    
    
    // Get the actual values from the comparison object
    const isMatch = comparison.is_match === true || comparison.is_match === "true";
    const score = parseFloat(comparison.match_score) || 0; // Ensure it's a number
    const status = comparison.status || (isMatch ? 'MATCH' : 'NOT MATCH');
    
    let html = `
    <div class="row">
        <div class="col-md-12">
            <div class="d-flex justify-content-between mb-3">
                <div class="text-center mx-2">
                    <h6>ID Signature</h6>
                    <img src="${idSignatureUrl}" class="img-thumbnail" style="height: 100px; object-fit: contain;">
                </div>
                <div class="text-center mx-2">
                    <h6>User Signature</h6>
                    <img src="${userSignatureUrl}" class="img-thumbnail" style="height: 100px; object-fit: contain;">
                </div>
            </div>
            
            <div class="card ${isMatch ? 'border-success' : 'border-danger'}">
                <div class="card-body">
                    <h5 class="card-title">
                        <span class="badge ${isMatch ? 'bg-success' : 'bg-danger'} fs-6">
                            ${status}
                        </span>
                    </h5>
                    <p><strong>Match Score:</strong> ${score.toFixed(1)}%</p>
                    ${comparison.good_matches_count !== undefined ? 
                      `<p><strong>Good Matches:</strong> ${comparison.good_matches_count}</p>` : ''}
                    ${comparison.img1_keypoints !== undefined ? 
                      `<p><strong>Keypoints (ID):</strong> ${comparison.img1_keypoints}</p>` : ''}
                    ${comparison.img2_keypoints !== undefined ? 
                      `<p><strong>Keypoints (User):</strong> ${comparison.img2_keypoints}</p>` : ''}
                </div>
            </div>
        </div>
    </div>`;
    
    container.innerHTML = html;
}

function displayVerificationResults(rawResult) {
  const result = safeProcessVerificationResult(rawResult);
  
  // Now you can safely use result.logic_check.is_suspicious, etc.
  // Rest of your display code...
}

function displayVerificationResults(verification) {
    console.log("👤 displayVerificationResults called with:", verification);
    
    // Check for missing data and log it
    if (!verification) {
        console.error("❌ Verification result is undefined or null!");
        return;
    }
    
    if (!verification.logic_check) {
        console.error("❌ Missing logic_check in verification result!");
        verification.logic_check = {
            is_suspicious: false,
            reasons: ["Data unavailable"],
            metrics: {}
        };
    }
    
    if (!verification.ai_check) {
        console.error("❌ Missing ai_check in verification result!");
        verification.ai_check = {
            prediction: "ERROR",
            confidence: 0,
            fraud_score: 0,
            name_component: "unknown"
        };
    }
    
    if (!verification.final_decision) {
        console.error("❌ Missing final_decision in verification result!");
        verification.final_decision = {
            decision: "REVIEW",
            reason: "Incomplete data",
            confidence: 0.5
        };
    }
    
    // Display final decision
    if (verification.final_decision) {
        displayFinalDecision(verification.final_decision);
    }
    
    // Display logic check
    if (verification.logic_check) {
        displayLogicCheck(verification.logic_check);
    }
    
    // Display AI check
    if (verification.ai_check) {
        displayAICheck(verification.ai_check);
    }
}

function displayFinalDecision(finalDecision) {
    console.log("🎯 displayFinalDecision called with:", finalDecision);
    
    const decisionSection = document.getElementById('final-decision');
    if (!decisionSection) {
        console.error("❌ final-decision section not found");
        return;
    }
    
    const decision = finalDecision.decision || 'UNKNOWN';
    const reason = finalDecision.reason || 'No reason provided';
    const confidence = finalDecision.confidence || 0;
    
    let cardClass = 'border-warning';
    let badgeClass = 'bg-warning';
    let icon = '⚠️';
    
    if (decision === 'ACCEPT') {
        cardClass = 'border-success';
        badgeClass = 'bg-success';
        icon = '✅';
    } else if (decision === 'REJECT') {
        cardClass = 'border-danger';
        badgeClass = 'bg-danger';
        icon = '❌';
    } else if (decision === 'REVIEW') {
        cardClass = 'border-warning';
        badgeClass = 'bg-warning';
        icon = '⚠️';
    }
    
    decisionSection.innerHTML = `
        <div class="card ${cardClass}">
            <div class="card-body text-center">
                <h3 class="card-title">
                    <span class="badge ${badgeClass} fs-5">${icon} ${decision}</span>
                </h3>
                <p class="card-text mt-3">
                    <strong>Reason:</strong> ${reason}
                </p>
                <p class="card-text">
                    <strong>Confidence:</strong> ${(confidence * 100).toFixed(1)}%
                </p>
            </div>
        </div>
    `;
}

function displayLogicCheck(logicCheck) {
    console.log("🧠 displayLogicCheck called with:", logicCheck);
    
    const logicSection = document.getElementById('logic-check-results');
    if (!logicSection) {
        console.error("❌ logic-check-results section not found");
        return;
    }
    
    const isSuspicious = logicCheck.is_suspicious;
    const reasons = logicCheck.reasons || [];
    const metrics = logicCheck.metrics || {};
    
    // Create status badge
    const statusBadge = isSuspicious ? 
        '<span class="badge bg-warning">⚠️ Suspicious</span>' : 
        '<span class="badge bg-success">✅ Clean</span>';
    
    let html = `
        <div class="card">
            <div class="card-body">
                <h5 class="card-title">🧠 Logic Check Results</h5>
                <div class="mb-3">
                    <strong>Status:</strong> ${statusBadge}
                </div>
    `;
    
    // Display reasons
    if (reasons.length > 0) {
        html += `
            <div class="mb-3">
                <strong>Reasons:</strong>
                <ul class="list-unstyled ms-3">
        `;
        reasons.forEach(reason => {
            html += `<li>• ${reason}</li>`;
        });
        html += `
                </ul>
            </div>
        `;
    }
    
    // Display metrics
    if (Object.keys(metrics).length > 0) {
        html += `
            <div class="mb-3">
                <strong>Metrics:</strong>
                <ul class="list-unstyled ms-3">
        `;
        for (const [key, value] of Object.entries(metrics)) {
            // Format the metric key to be more readable
            const formattedKey = key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
            html += `<li><strong>${formattedKey}:</strong> ${value}</li>`;
        }
        html += `
                </ul>
            </div>
        `;
    }
    
    html += `
            </div>
        </div>
    `;
    
    logicSection.innerHTML = html;
}

function displayAICheck(aiCheck) {
    console.log("🤖 displayAICheck called with:", aiCheck);
    
    const aiSection = document.getElementById('ai-check-results');
    if (!aiSection) {
        console.error("❌ ai-check-results section not found");
        return;
    }
    
    const prediction = aiCheck.prediction || 'Unknown';
    const confidence = aiCheck.confidence || 0;
    const fraudScore = aiCheck.fraud_score || 0;
    const nameComponent = aiCheck.name_component || 'name';
    const namePart = aiCheck.name_part || 'complete';
    
    let html = `
        <div class="card">
            <div class="card-body">
                <h5 class="card-title">🤖 AI Check Results</h5>
                <div class="mb-2">
                    <strong>Prediction:</strong> 
                    <span class="badge ${prediction === 'Genuine' ? 'bg-success' : 
                                       prediction === 'Fraudulent' ? 'bg-danger' : 'bg-warning'}">
                        ${prediction}
                    </span>
                </div>
                <div class="mb-2">
                    <strong>Confidence:</strong> ${(confidence * 100).toFixed(1)}%
                </div>
                <div class="mb-2">
                    <strong>Fraud Score:</strong> ${(fraudScore * 100).toFixed(1)}%
                </div>
                <div class="mb-2">
                    <strong>Analyzed Component:</strong> ${nameComponent}
                </div>
    `;
    
    // Show if it was a compound name
    if (aiCheck.compound_name) {
        html += `
                <div class="mb-2">
                    <strong>Name Type:</strong> Compound name (${aiCheck.part_count || 'multiple'} parts)
                </div>
        `;
    }
    
    // Show which part had the issue if not the complete name
    if (namePart && namePart !== 'complete_first' && namePart !== 'complete_last') {
        html += `
                <div class="mb-2">
                    <strong>Potential Problematic Part:</strong> "${namePart}"
                </div>
        `;
    }
    
    // Show if prediction was modified due to low confidence
    if (aiCheck.original_prediction) {
        html += `
                <div class="alert alert-info mt-2">
                    <small><strong>Note:</strong> Originally predicted as ${aiCheck.original_prediction}, 
                    but changed due to ${aiCheck.reason}</small>
                </div>
        `;
    }
    
    html += `
            </div>
        </div>
    `;
    
    aiSection.innerHTML = html;
}

function displayIdDetections(files, idDetections) {
    console.log("🔍 displayIdDetections called with:", idDetections);
    
    const detectionsSection = document.getElementById('id-detections');
    if (!detectionsSection) {
        console.error("❌ id-detections section not found");
        return;
    }
    
    let html = `
        <div class="card mb-4">
            <div class="card-body">
                <h5 class="card-title">🔍 Document Detection Results</h5>
                <div class="table-responsive">
                    <table class="table table-bordered">
                        <thead>
                            <tr>
                                <th>Document Type</th>
                                <th>Elements Detected</th>
                                <th>Confidence</th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
    `;
    
    // Track if we have any detections to display
    let hasDetections = false;
    
    // Process each document type (id_front, id_back, photo)
    Object.entries(idDetections).forEach(([docType, detections]) => {
        // Check if we have any valid detections
        const hasValidDetections = detections && 
            detections.length > 0 && 
            detections[0].class_name !== "no_object_detected" && 
            parseFloat(detections[0].confidence) > 0.3;
        
        let statusBadge = hasValidDetections 
            ? '<span class="badge bg-success">Detected</span>' 
            : '<span class="badge bg-warning">Not Detected</span>';
            
        // Add blur information if available
        if (detections && detections.length > 0 && detections[0].blur_classification) {
            const blur = detections[0].blur_classification;
            statusBadge += ` <span class="badge ${blur.class_name === 'blur' ? 'bg-warning' : 'bg-info'}">
                ${blur.class_name.toUpperCase()} (${(blur.confidence * 100).toFixed(1)}%)
            </span>`;
        }
        
        // Rest of your table row generation code...
        html += `
            <tr>
                <td>${docType.replace('_', ' ').toUpperCase()}</td>
                <td>${detections && detections.length > 0 ? detections[0].class_name : 'None'}</td>
                <td>${detections && detections.length > 0 ? (detections[0].confidence * 100).toFixed(1) + '%' : '0.0%'}</td>
                <td>${statusBadge}</td>
            </tr>
        `;
    });
    
    if (!Object.keys(idDetections).length) {
        html += `
            <tr>
                <td colspan="4" class="text-center">No detection results available</td>
            </tr>
        `;
    }
    
    html += `
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    `;
    
    detectionsSection.innerHTML = html;
}

// Process name verification
async function verifyName(firstName, lastName, dob) {
  try {
    // Show loading state
    document.getElementById('loading').classList.remove('d-none');
    
    // Create API request
    const response = await fetch('/verify-name', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        first_name: firstName,
        last_name: lastName,
        dob: dob
      })
    });
    
    const result = await response.json();
    
    // Display the results
    displayNameVerificationResults(result);
    
  } catch (error) {
    console.error('Error verifying name:', error);
    alert('Error verifying name: ' + error.message);
  } finally {
    document.getElementById('loading').classList.add('d-none');
  }
}

// Display name verification results
function displayNameVerificationResults(result) {
  // Display Logic Check Results
  const logicCheck = result.logic_check;
  const isSuspicious = logicCheck.is_suspicious;
  
  // Set suspicious flag
  const suspiciousFlag = document.getElementById('name-suspicious-flag');
  suspiciousFlag.textContent = isSuspicious ? 'YES' : 'NO';
  suspiciousFlag.classList.add(isSuspicious ? 'bg-danger' : 'bg-success');
  
  // Display reasons
  const reasonsList = document.getElementById('name-suspicious-reasons');
  reasonsList.innerHTML = '';
  logicCheck.reasons.forEach(reason => {
    const li = document.createElement('li');
    li.textContent = reason;
    reasonsList.appendChild(li);
  });
  
  // Display metrics
  const metricsDiv = document.getElementById('name-metrics');
  metricsDiv.innerHTML = '';
  Object.entries(logicCheck.metrics).forEach(([key, value]) => {
    const p = document.createElement('p');
    p.classList.add('mb-1');
    p.innerHTML = `<em>${key}:</em> ${value}`;
    metricsDiv.appendChild(p);
  });
  
  // Display AI Check Results
  const aiCheck = result.ai_check;
  
  // Name component
  document.getElementById('name-component').textContent = aiCheck.name_component === 'first_name' ? 
    'First Name' : 'Last Name';
  
  // Prediction
  const predictionBadge = document.getElementById('name-prediction');
  predictionBadge.textContent = aiCheck.prediction;
  
  // Set prediction badge color
  if (aiCheck.prediction === 'Genuine') {
    predictionBadge.classList.add('bg-success');
  } else if (aiCheck.prediction === 'Fraudulent') {
    predictionBadge.classList.add('bg-danger');
  } else {
    predictionBadge.classList.add('bg-warning');
  }
  
  // Fraud score
  const fraudScore = aiCheck.fraud_score;
  document.getElementById('name-fraud-score').textContent = `${(fraudScore * 100).toFixed(1)}%`;
  
  // Update progress bar
  const fraudScoreBar = document.getElementById('name-fraud-score-bar');
  fraudScoreBar.style.width = `${fraudScore * 100}%`;
  
  // Set color based on fraud score
  if (fraudScore < 0.3) {
    fraudScoreBar.classList.add('bg-success');
  } else if (fraudScore < 0.7) {
    fraudScoreBar.classList.add('bg-warning');
  } else {
    fraudScoreBar.classList.add('bg-danger');
  }
  
  // Display Final Decision
  const finalDecision = result.final_decision;
  
  // Decision badge
  const decisionBadge = document.getElementById('name-decision-badge');
  decisionBadge.textContent = finalDecision.decision;
  
  // Set decision badge color
  if (finalDecision.decision === 'ACCEPT') {
    decisionBadge.classList.add('bg-success');
  } else if (finalDecision.decision === 'REJECT') {
    decisionBadge.classList.add('bg-danger');
  } else {
    decisionBadge.classList.add('bg-warning');
  }
  
  // Decision reason
  document.getElementById('name-decision-reason').textContent = finalDecision.reason;
  
  // Confidence
  const confidence = finalDecision.confidence;
  document.getElementById('name-confidence').textContent = `${(confidence * 100).toFixed(1)}%`;
  
  // Update confidence bar
  const confidenceBar = document.getElementById('name-confidence-bar');
  confidenceBar.style.width = `${confidence * 100}%`;
}

// Main function to handle all results
function displayAllResults(result) {
    console.log("📊 Displaying all verification results");
    debugImagePaths(result);
    
    console.log("🔍 Cropped elements:", result.cropped_elements);
    
    // 1. Display uploaded files and detection images
    if (result.files) {
        displayUploadedFiles(result.files);
    }
    
    // 2. Display crop images (ID elements)
    if (result.id_detections) {
        displayCropImages(result.id_detections);
    }
    
    // 3. Display face comparison results
    if (result.face_comparison) {
        // Use the specific URLs from the backend response
        const idPhotoUrl = result.detected_person_url || result.cropped_elements?.person_from_id;
        const selfieUrl = result.uploaded_person_url || result.cropped_elements?.person_from_photo || result.files?.photo?.url;
        
        console.log("👤 Face comparison URLs:", { idPhotoUrl, selfieUrl });
        
        displayFaceComparisonResults(result.face_comparison, idPhotoUrl, selfieUrl);
    }
    
    // 4. COMMENTED OUT: Signature comparison results
    /*
    if (result.signature_comparison) {
        console.log("🐛 DEBUG: Signature comparison detected");
        console.log("🐛 Available cropped elements:", result.cropped_elements);
        console.log("🐛 Direct URLs in response:");
        console.log("  - id_signature_url:", result.id_signature_url);
        console.log("  - uploaded_signature_url:", result.uploaded_signature_url);
        
        // Use the specific URLs from the backend response with fallbacks
        const idSignatureUrl = result.id_signature_url || 
                              result.cropped_elements?.signature || 
                              result.cropped_elements?.["signature_from_id"];
                              
        const userSignatureUrl = result.uploaded_signature_url || 
                                result.cropped_elements?.uploaded_signature || 
                                result.files?.signature?.url;
        
        console.log("✍️ Final signature URLs:", { idSignatureUrl, userSignatureUrl });
        
        if (!idSignatureUrl) {
            console.error("❌ ID signature URL is missing!");
            // Try to find it in the crop results section
            const cropSignatureImg = document.querySelector('#crop-results img[alt="Signature"]');
            if (cropSignatureImg) {
                console.log("🔍 Found signature in crop results, using:", cropSignatureImg.src);
                idSignatureUrl = cropSignatureImg.src;
            }
        }
        
        displaySignatureComparisonResults(
            result.signature_comparison,
            idSignatureUrl,
            userSignatureUrl
        );
    }
    */
}

// Add this debug function
function debugImagePaths(result) {
    console.log("🐛 DEBUG: All available image paths");
    console.log("📁 Files:", result.files);
    console.log("✂️ Cropped elements:", result.cropped_elements);
    console.log("🔗 Direct URLs:");
    console.log("  - detected_person_url:", result.detected_person_url);
    console.log("  - uploaded_person_url:", result.uploaded_person_url);
    console.log("  - id_signature_url:", result.id_signature_url);
    console.log("  - uploaded_signature_url:", result.uploaded_signature_url);
}

// Function to display crop images
function displayCropImages(idDetections) {
    console.log("🔍 Displaying cropped elements");
    
    const cropContainer = document.getElementById('crop-results');
    if (!cropContainer) return;
    
    let html = '';
    let cropsFound = false;
    
    // Process all detections from all documents
    for (const [docType, detections] of Object.entries(idDetections)) {
        if (!Array.isArray(detections)) continue;
        
        for (const detection of detections) {
            // REMOVED: Skip signature crops - now signatures will be shown
            // if (detection.class_name && detection.class_name.toLowerCase() === 'signature') {
            //     console.log("⏭️ Skipping signature crop");
            //     continue;
            // }
            
            // Only process if it's a crop with a path
            if (detection.class_name && detection.crop_path) {
                cropsFound = true;
                
                // Use web_path if available, otherwise construct it
                let imageSrc;
                if (detection.web_path) {
                    imageSrc = detection.web_path;
                } else {
                    // Fallback: construct path from crop_path
                    imageSrc = '/' + detection.crop_path.replace(/\\/g, '/').replace('static/', '');
                }
                
                // For debugging
                console.log(`Crop image path: ${imageSrc}`);
                
                html += `
                <div class="col-md-4 mb-3">
                    <div class="card h-100">
                        <div class="card-header bg-light">
                            <h6 class="mb-0">${detection.class_name} from ${docType}</h6>
                        </div>
                        <div class="card-body text-center">
                            <img src="${imageSrc}" 
                                 class="img-fluid" 
                                 alt="${detection.class_name}"
                                 style="max-height: 200px; object-fit: contain;"
                                 onerror="console.error('Failed to load crop image:', this.src); this.style.display='none';">
                            <div class="mt-2">
                                <span class="badge bg-primary">
                                    ${(detection.confidence * 100).toFixed(1)}% confidence
                                </span>
                            </div>
                        </div>
                    </div>
                </div>`;
            }
        }
    }
    
    if (!cropsFound) {
        html = '<div class="col-12"><p class="text-center">No cropped elements found</p></div>';
    }
    
    cropContainer.innerHTML = html;
}

// Function to display face comparison results
function displayFaceComparisonResults(comparison, idPhotoUrl, selfieUrl) {
    console.log("👤 Displaying face comparison results");
    console.log("📸 ID Photo URL:", idPhotoUrl);
    console.log("📸 Selfie URL:", selfieUrl);
    
    const container = document.getElementById('face-comparison-result');
    if (!container) return;
    
    // Fallback if URLs are missing
    if (!idPhotoUrl) {
        console.warn("⚠️ ID photo URL missing, trying to find in crops");
        const cropImage = document.querySelector('#crop-results img[alt*="Person"]');
        if (cropImage) {
            idPhotoUrl = cropImage.src;
        }
    }
    
    if (!selfieUrl) {
        console.warn("⚠️ Selfie URL missing");
        selfieUrl = '/static/img/placeholder.png'; // Add a placeholder
    }
    
    // Get values from DeepFace result format - USE DISTANCE AND THRESHOLD
    const isMatch = comparison.is_same_person || comparison.verified || false;
    const distance = comparison.distance || 0;
    const threshold = comparison.threshold || 0.6;
    const resultImagePath = comparison.result_image_path || comparison.comparison_image_url;
    
    let html = `
    <div class="row">
        <div class="col-md-6">
            <div class="d-flex justify-content-between mb-3">
                <div class="text-center mx-2">
                    <h6>ID Photo</h6>
                    <img src="${idPhotoUrl}" class="img-thumbnail" style="height: 150px; object-fit: contain;" 
                         onerror="console.error('Failed to load ID photo:', this.src); this.style.display='none';">
                </div>
                <div class="text-center mx-2">
                    <h6>Selfie</h6>
                    <img src="${selfieUrl}" class="img-thumbnail" style="height: 150px; object-fit: contain;"
                         onerror="console.error('Failed to load selfie:', this.src); this.style.display='none';">
                </div>
            </div>
            
            <div class="card ${isMatch ? 'border-success' : 'border-danger'}">
                <div class="card-body">
                    <h5 class="card-title">
                        <span class="badge ${isMatch ? 'bg-success' : 'bg-danger'} fs-6">
                            ${isMatch ? '✅ MATCH' : '❌ NO MATCH'}
                        </span>
                    </h5>
                    <p><strong>Description:</strong> ${distance < threshold ? 
                        'If distance < threshold, images are similar' : 
                        'If distance > threshold, images are different'}</p>
                </div>
            </div>
        </div>
        
        <div class="col-md-6">
            <h6>Comparison Result</h6>
            <img src="${resultImagePath ? '/' + resultImagePath.replace('static/', '') : comparison.comparison_image_url}" 
                 class="img-fluid border" 
                 alt="Face Comparison"
                 style="max-height: 300px; object-fit: contain;"
                 onerror="console.error('Failed to load comparison image:', this.src); this.style.display='none';">
        </div>
    </div>`;
    
    container.innerHTML = html;
}

// Function to display signature comparison results
function displaySignatureComparisonResults(comparison, idSignatureUrl, userSignatureUrl) {
    console.log("✍️ Displaying signature comparison results:", comparison);
    console.log("ID Signature URL:", idSignatureUrl);
    console.log("User Signature URL:", userSignatureUrl);
    // Ensure we have valid URLs by looking in multiple places
    const container = document.getElementById('signature-comparison-result');
    if (!container) {
        console.error("❌ signature-comparison-result container not found");
        return;
    }
    if (!idSignatureUrl) {
        console.warn("⚠️ ID signature URL missing, searching in crop results...");
        const cropImage = document.querySelector('#crop-results img[alt="Signature"]');
        if (cropImage) {
            idSignatureUrl = cropImage.src;
            console.log("✅ Found signature in crops:", idSignatureUrl);
        } else {
            console.error("❌ Could not find signature image anywhere");
        }
    }
    // Use placeholder if still missing
    if (!idSignatureUrl) {
        idSignatureUrl = '/static/img/signature-placeholder.png';
        console.warn("⚠️ Using placeholder for ID signature");
    }
    
    if (!userSignatureUrl) {
        userSignatureUrl = '/static/img/signature-placeholder.png';
        console.warn("⚠️ Using placeholder for user signature");
    }
    
    
    // Get the actual values from the comparison object
    const isMatch = comparison.is_match === true || comparison.is_match === "true";
    const score = parseFloat(comparison.match_score) || 0; // Ensure it's a number
    const status = comparison.status || (isMatch ? 'MATCH' : 'NOT MATCH');
    
    let html = `
    <div class="row">
        <div class="col-md-12">
            <div class="d-flex justify-content-between mb-3">
                <div class="text-center mx-2">
                    <h6>ID Signature</h6>
                    <img src="${idSignatureUrl}" class="img-thumbnail" style="height: 100px; object-fit: contain;">
                </div>
                <div class="text-center mx-2">
                    <h6>User Signature</h6>
                    <img src="${userSignatureUrl}" class="img-thumbnail" style="height: 100px; object-fit: contain;">
                </div>
            </div>
            
            <div class="card ${isMatch ? 'border-success' : 'border-danger'}">
                <div class="card-body">
                    <h5 class="card-title">
                        <span class="badge ${isMatch ? 'bg-success' : 'bg-danger'} fs-6">
                            ${status}
                        </span>
                    </h5>
                    <p><strong>Match Score:</strong> ${score.toFixed(1)}%</p>
                    ${comparison.good_matches_count !== undefined ? 
                      `<p><strong>Good Matches:</strong> ${comparison.good_matches_count}</p>` : ''}
                    ${comparison.img1_keypoints !== undefined ? 
                      `<p><strong>Keypoints (ID):</strong> ${comparison.img1_keypoints}</p>` : ''}
                    ${comparison.img2_keypoints !== undefined ? 
                      `<p><strong>Keypoints (User):</strong> ${comparison.img2_keypoints}</p>` : ''}
                </div>
            </div>
        </div>
    </div>`;
    
    container.innerHTML = html;
}

function displayVerificationResults(rawResult) {
  const result = safeProcessVerificationResult(rawResult);
  
  // Now you can safely use result.logic_check.is_suspicious, etc.
  // Rest of your display code...
}

function displayVerificationResults(verification) {
    console.log("👤 displayVerificationResults called with:", verification);
    
    // Check for missing data and log it
    if (!verification) {
        console.error("❌ Verification result is undefined or null!");
        return;
    }
    
    if (!verification.logic_check) {
        console.error("❌ Missing logic_check in verification result!");
        verification.logic_check = {
            is_suspicious: false,
            reasons: ["Data unavailable"],
            metrics: {}
        };
    }
    
    if (!verification.ai_check) {
        console.error("❌ Missing ai_check in verification result!");
        verification.ai_check = {
            prediction: "ERROR",
            confidence: 0,
            fraud_score: 0,
            name_component: "unknown"
        };
    }
    
    if (!verification.final_decision) {
        console.error("❌ Missing final_decision in verification result!");
        verification.final_decision = {
            decision: "REVIEW",
            reason: "Incomplete data",
            confidence: 0.5
        };
    }
    
    // Display final decision
    if (verification.final_decision) {
        displayFinalDecision(verification.final_decision);
    }
    
    // Display logic check
    if (verification.logic_check) {
        displayLogicCheck(verification.logic_check);
    }
    
    // Display AI check
    if (verification.ai_check) {
        displayAICheck(verification.ai_check);
    }
}

function displayFinalDecision(finalDecision) {
    console.log("🎯 displayFinalDecision called with:", finalDecision);
    
    const decisionSection = document.getElementById('final-decision');
    if (!decisionSection) {
        console.error("❌ final-decision section not found");
        return;
    }
    
    const decision = finalDecision.decision || 'UNKNOWN';
    const reason = finalDecision.reason || 'No reason provided';
    const confidence = finalDecision.confidence || 0;
    
    let cardClass = 'border-warning';
    let badgeClass = 'bg-warning';
    let icon = '⚠️';
    
    if (decision === 'ACCEPT') {
        cardClass = 'border-success';
        badgeClass = 'bg-success';
        icon = '✅';
    } else if (decision === 'REJECT') {
        cardClass = 'border-danger';
        badgeClass = 'bg-danger';
        icon = '❌';
    } else if (decision === 'REVIEW') {
        cardClass = 'border-warning';
        badgeClass = 'bg-warning';
        icon = '⚠️';
    }
    
    decisionSection.innerHTML = `
        <div class="card ${cardClass}">
            <div class="card-body text-center">
                <h3 class="card-title">
                    <span class="badge ${badgeClass} fs-5">${icon} ${decision}</span>
                </h3>
                <p class="card-text mt-3">
                    <strong>Reason:</strong> ${reason}
                </p>
                <p class="card-text">
                    <strong>Confidence:</strong> ${(confidence * 100).toFixed(1)}%
                </p>
            </div>
        </div>
    `;
}

function displayLogicCheck(logicCheck) {
    console.log("🧠 displayLogicCheck called with:", logicCheck);
    
    const logicSection = document.getElementById('logic-check-results');
    if (!logicSection) {
        console.error("❌ logic-check-results section not found");
        return;
    }
    
    const isSuspicious = logicCheck.is_suspicious;
    const reasons = logicCheck.reasons || [];
    const metrics = logicCheck.metrics || {};
    
    // Create status badge
    const statusBadge = isSuspicious ? 
        '<span class="badge bg-warning">⚠️ Suspicious</span>' : 
        '<span class="badge bg-success">✅ Clean</span>';
    
    let html = `
        <div class="card">
            <div class="card-body">
                <h5 class="card-title">🧠 Logic Check Results</h5>
                <div class="mb-3">
                    <strong>Status:</strong> ${statusBadge}
                </div>
    `;
    
    // Display reasons
    if (reasons.length > 0) {
        html += `
            <div class="mb-3">
                <strong>Reasons:</strong>
                <ul class="list-unstyled ms-3">
        `;
        reasons.forEach(reason => {
            html += `<li>• ${reason}</li>`;
        });
        html += `
                </ul>
            </div>
        `;
    }
    
    // Display metrics
    if (Object.keys(metrics).length > 0) {
        html += `
            <div class="mb-3">
                <strong>Metrics:</strong>
                <ul class="list-unstyled ms-3">
        `;
        for (const [key, value] of Object.entries(metrics)) {
            // Format the metric key to be more readable
            const formattedKey = key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
            html += `<li><strong>${formattedKey}:</strong> ${value}</li>`;
        }
        html += `
                </ul>
            </div>
        `;
    }
    
    html += `
            </div>
        </div>
    `;
    
    logicSection.innerHTML = html;
}

function displayAICheck(aiCheck) {
    console.log("🤖 displayAICheck called with:", aiCheck);
    
    const aiSection = document.getElementById('ai-check-results');
    if (!aiSection) {
        console.error("❌ ai-check-results section not found");
        return;
    }
    
    const prediction = aiCheck.prediction || 'Unknown';
    const confidence = aiCheck.confidence || 0;
    const fraudScore = aiCheck.fraud_score || 0;
    const nameComponent = aiCheck.name_component || 'name';
    const namePart = aiCheck.name_part || 'complete';
    
    let html = `
        <div class="card">
            <div class="card-body">
                <h5 class="card-title">🤖 AI Check Results</h5>
                <div class="mb-2">
                    <strong>Prediction:</strong> 
                    <span class="badge ${prediction === 'Genuine' ? 'bg-success' : 
                                       prediction === 'Fraudulent' ? 'bg-danger' : 'bg-warning'}">
                        ${prediction}
                    </span>
                </div>
                <div class="mb-2">
                    <strong>Confidence:</strong> ${(confidence * 100).toFixed(1)}%
                </div>
                <div class="mb-2">
                    <strong>Fraud Score:</strong> ${(fraudScore * 100).toFixed(1)}%
                </div>
                <div class="mb-2">
                    <strong>Analyzed Component:</strong> ${nameComponent}
                </div>
    `;
    
    // Show if it was a compound name
    if (aiCheck.compound_name) {
        html += `
                <div class="mb-2">
                    <strong>Name Type:</strong> Compound name (${aiCheck.part_count || 'multiple'} parts)
                </div>
        `;
    }
    
    // Show which part had the issue if not the complete name
    if (namePart && namePart !== 'complete_first' && namePart !== 'complete_last') {
        html += `
                <div class="mb-2">
                    <strong>Potential Problematic Part:</strong> "${namePart}"
                </div>
        `;
    }
    
    // Show if prediction was modified due to low confidence
    if (aiCheck.original_prediction) {
        html += `
                <div class="alert alert-info mt-2">
                    <small><strong>Note:</strong> Originally predicted as ${aiCheck.original_prediction}, 
                    but changed due to ${aiCheck.reason}</small>
                </div>
        `;
    }
    
    html += `
            </div>
        </div>
    `;
    
    aiSection.innerHTML = html;
}

function displayIdDetections(files, idDetections) {
    console.log("🔍 displayIdDetections called with:", idDetections);
    
    const detectionsSection = document.getElementById('id-detections');
    if (!detectionsSection) {
        console.error("❌ id-detections section not found");
        return;
    }
    
    let html = `
        <div class="card mb-4">
            <div class="card-body">
                <h5 class="card-title">🔍 Document Detection Results</h5>
                <div class="table-responsive">
                    <table class="table table-bordered">
                        <thead>
                            <tr>
                                <th>Document Type</th>
                                <th>Elements Detected</th>
                                <th>Confidence</th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
    `;
    
    // Track if we have any detections to display
    let hasDetections = false;
    
    // Process each document type (id_front, id_back, photo)
    Object.entries(idDetections).forEach(([docType, detections]) => {
        // Check if we have any valid detections
        const hasValidDetections = detections && 
            detections.length > 0 && 
            detections[0].class_name !== "no_object_detected" && 
            parseFloat(detections[0].confidence) > 0.3;
        
        let statusBadge = hasValidDetections 
            ? '<span class="badge bg-success">Detected</span>' 
            : '<span class="badge bg-warning">Not Detected</span>';
            
        // Add blur information if available
        if (detections && detections.length > 0 && detections[0].blur_classification) {
            const blur = detections[0].blur_classification;
            statusBadge += ` <span class="badge ${blur.class_name === 'blur' ? 'bg-warning' : 'bg-info'}">
                ${blur.class_name.toUpperCase()} (${(blur.confidence * 100).toFixed(1)}%)
            </span>`;
        }
        
        // Rest of your table row generation code...
        html += `
            <tr>
                <td>${docType.replace('_', ' ').toUpperCase()}</td>
                <td>${detections && detections.length > 0 ? detections[0].class_name : 'None'}</td>
                <td>${detections && detections.length > 0 ? (detections[0].confidence * 100).toFixed(1) + '%' : '0.0%'}</td>
                <td>${statusBadge}</td>
            </tr>
        `;
    });
    
    if (!Object.keys(idDetections).length) {
        html += `
            <tr>
                <td colspan="4" class="text-center">No detection results available</td>
            </tr>
        `;
    }
    
    html += `
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    `;
    
    detectionsSection.innerHTML = html;
}

// Process name verification
async function verifyName(firstName, lastName, dob) {
  try {
    // Show loading state
    document.getElementById('loading').classList.remove('d-none');
    
    // Create API request
    const response = await fetch('/verify-name', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        first_name: firstName,
        last_name: lastName,
        dob: dob
      })
    });
    
    const result = await response.json();
    
    // Display the results
    displayNameVerificationResults(result);
    
  } catch (error) {
    console.error('Error verifying name:', error);
    alert('Error verifying name: ' + error.message);
  } finally {
    document.getElementById('loading').classList.add('d-none');
  }
}

// Display name verification results
function displayNameVerificationResults(result) {
  // Display Logic Check Results
  const logicCheck = result.logic_check;
  const isSuspicious = logicCheck.is_suspicious;
  
  // Set suspicious flag
  const suspiciousFlag = document.getElementById('name-suspicious-flag');
  suspiciousFlag.textContent = isSuspicious ? 'YES' : 'NO';
  suspiciousFlag.classList.add(isSuspicious ? 'bg-danger' : 'bg-success');
  
  // Display reasons
  const reasonsList = document.getElementById('name-suspicious-reasons');
  reasonsList.innerHTML = '';
  logicCheck.reasons.forEach(reason => {
    const li = document.createElement('li');
    li.textContent = reason;
    reasonsList.appendChild(li);
  });
  
  // Display metrics
  const metricsDiv = document.getElementById('name-metrics');
  metricsDiv.innerHTML = '';
  Object.entries(logicCheck.metrics).forEach(([key, value]) => {
    const p = document.createElement('p');
    p.classList.add('mb-1');
    p.innerHTML = `<em>${key}:</em> ${value}`;
    metricsDiv.appendChild(p);
  });
  
  // Display AI Check Results
  const aiCheck = result.ai_check;
  
  // Name component
  document.getElementById('name-component').textContent = aiCheck.name_component === 'first_name' ? 
    'First Name' : 'Last Name';
  
  // Prediction
  const predictionBadge = document.getElementById('name-prediction');
  predictionBadge.textContent = aiCheck.prediction;
  
  // Set prediction badge color
  if (aiCheck.prediction === 'Genuine') {
    predictionBadge.classList.add('bg-success');
  } else if (aiCheck.prediction === 'Fraudulent') {
    predictionBadge.classList.add('bg-danger');
  } else {
    predictionBadge.classList.add('bg-warning');
  }
  
  // Fraud score
  const fraudScore = aiCheck.fraud_score;
  document.getElementById('name-fraud-score').textContent = `${(fraudScore * 100).toFixed(1)}%`;
  
  // Update progress bar
  const fraudScoreBar = document.getElementById('name-fraud-score-bar');
  fraudScoreBar.style.width = `${fraudScore * 100}%`;
  
  // Set color based on fraud score
  if (fraudScore < 0.3) {
    fraudScoreBar.classList.add('bg-success');
  } else if (fraudScore < 0.7) {
    fraudScoreBar.classList.add('bg-warning');
  } else {
    fraudScoreBar.classList.add('bg-danger');
  }
  
  // Display Final Decision
  const finalDecision = result.final_decision;
  
  // Decision badge
  const decisionBadge = document.getElementById('name-decision-badge');
  decisionBadge.textContent = finalDecision.decision;
  
  // Set decision badge color
  if (finalDecision.decision === 'ACCEPT') {
    decisionBadge.classList.add('bg-success');
  } else if (finalDecision.decision === 'REJECT') {
    decisionBadge.classList.add('bg-danger');
  } else {
    decisionBadge.classList.add('bg-warning');
  }
  
  // Decision reason
  document.getElementById('name-decision-reason').textContent = finalDecision.reason;
  
  // Confidence
  const confidence = finalDecision.confidence;
  document.getElementById('name-confidence').textContent = `${(confidence * 100).toFixed(1)}%`;
  
  // Update confidence bar
  const confidenceBar = document.getElementById('name-confidence-bar');
  confidenceBar.style.width = `${confidence * 100}%`;
}