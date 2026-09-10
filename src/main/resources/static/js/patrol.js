let currentDir = 'stop';
let isAiCapturing = false;
let patrolPageAlert = null;
let activePtzDirection = null;
let activePtzStartPromise = null;
let activePtzStartedAt = 0;
let cameraAudioEnabled = false;
let voiceBroadcastFile = null;
let voicePreviewUrl = null;
let voiceMediaRecorder = null;
let voiceMediaStream = null;
let voiceRecordChunks = [];
let voiceRecordStartedAt = 0;
let voiceRecordTimer = null;
let cameraRecoveryTimer = null;
let cameraPreferredProtocol = 4;
let cameraLatencyTimer = null;
let cameraWatchdogTimer = null;
let cameraLastProgressAt = 0;
let cameraInitStartedAt = 0;
let cameraRecoveryAttempts = 0;
let cameraZoom = 1;
let cameraPanX = 0;
let cameraPanY = 0;
let cameraDragging = false;
let cameraDragPointerId = null;
let cameraDragStartX = 0;
let cameraDragStartY = 0;
let cameraDragOriginX = 0;
let cameraDragOriginY = 0;
let ptzStopTimer = null;
let motorRequestId = 0;
let motorStopTimer = null;
let motorCommandChain = Promise.resolve();
let motorMotionGeneration = 0;
let motorMotionActive = false;
let panelMotionDir = null;
let panelMotionRequestId = 0;
let panelMotionActive = false;
let panelMotionTimer = null;
let panelMotionChain = Promise.resolve();
let panelMotionGeneration = 0;
let selectedAutomaticPatrolPlan = 'standard';
let automaticPatrolRunning = false;
let automaticPatrolSpeechEnabled = false;
let automaticPatrolLastSpokenPhase = '';
let automaticPatrolLastSpokenState = '';
let automaticPatrolSpeechQueue = [];
let automaticPatrolSpeechPlaying = false;
let automaticPatrolSpeechAudio = null;
let automaticPatrolSpeechRequest = null;
let automaticPatrolSpeechFinish = null;
let automaticPatrolSpeechGeneration = 0;
let realtimeDetectionEnabled = false;
let realtimeDetectionTimer = null;
let realtimeDetectionInFlight = false;
let realtimeDetectionErrors = 0;
let realtimeAlertDismissedAt = 0;
let realtimeWasDetected = false;
let realtimeLastSoundAt = 0;
let realtimeAudioContext = null;
let realtimeLastDetectionAt = 0;
let realtimeDetectionInterval = 1000;

function clampCameraPan() {
    var viewport = document.querySelector('.patrol-video-main');
    if (!viewport || cameraZoom <= 1) {
        cameraPanX = 0;
        cameraPanY = 0;
        return;
    }
    var maxX = viewport.clientWidth * (cameraZoom - 1) / 2;
    var maxY = viewport.clientHeight * (cameraZoom - 1) / 2;
    cameraPanX = Math.max(-maxX, Math.min(maxX, cameraPanX));
    cameraPanY = Math.max(-maxY, Math.min(maxY, cameraPanY));
}

function applyCameraZoom() {
    var video = document.getElementById('video-player');
    var label = document.getElementById('camera-zoom-label');
    var viewport = document.querySelector('.patrol-video-main');
    clampCameraPan();
    if (video) {
        video.style.transform = 'translate3d(' + cameraPanX.toFixed(1) + 'px,'
                + cameraPanY.toFixed(1) + 'px,0) scale(' + cameraZoom.toFixed(2) + ')';
    }
    var detectionCanvas = document.getElementById('realtime-detection-canvas');
    if (detectionCanvas) {
        detectionCanvas.style.transform = 'translate3d(' + cameraPanX.toFixed(1) + 'px,'
                + cameraPanY.toFixed(1) + 'px,0) scale(' + cameraZoom.toFixed(2) + ')';
    }
    if (viewport) viewport.classList.toggle('camera-pan-enabled', cameraZoom > 1);
    if (label) label.textContent = Math.round(cameraZoom * 100) + '%';
}

function adjustCameraZoom(delta, focusX, focusY) {
    var previousZoom = cameraZoom;
    var nextZoom = Math.max(1, Math.min(4, cameraZoom + delta));
    if (nextZoom === previousZoom) return;
    if (typeof focusX === 'number' && typeof focusY === 'number' && previousZoom > 0) {
        var ratio = nextZoom / previousZoom;
        cameraPanX = focusX - (focusX - cameraPanX) * ratio;
        cameraPanY = focusY - (focusY - cameraPanY) * ratio;
    }
    cameraZoom = nextZoom;
    applyCameraZoom();
}

function resetCameraZoom() {
    cameraZoom = 1;
    cameraPanX = 0;
    cameraPanY = 0;
    applyCameraZoom();
}

function bindCameraViewportControls() {
    var viewport = document.querySelector('.patrol-video-main');
    var video = document.getElementById('video-player');
    if (!viewport || !video || viewport.dataset.viewportControlsBound === 'true') return;
    viewport.dataset.viewportControlsBound = 'true';

    video.addEventListener('wheel', function(event) {
        event.preventDefault();
        var rect = viewport.getBoundingClientRect();
        var focusX = event.clientX - rect.left - rect.width / 2;
        var focusY = event.clientY - rect.top - rect.height / 2;
        adjustCameraZoom(event.deltaY < 0 ? 0.1 : -0.1, focusX, focusY);
    }, { passive: false });

    video.addEventListener('pointerdown', function(event) {
        if (event.button !== 0 || cameraZoom <= 1) return;
        event.preventDefault();
        cameraDragging = true;
        cameraDragPointerId = event.pointerId;
        cameraDragStartX = event.clientX;
        cameraDragStartY = event.clientY;
        cameraDragOriginX = cameraPanX;
        cameraDragOriginY = cameraPanY;
        video.setPointerCapture(event.pointerId);
        viewport.classList.add('camera-dragging');
    });

    video.addEventListener('pointermove', function(event) {
        if (!cameraDragging || event.pointerId !== cameraDragPointerId) return;
        cameraPanX = cameraDragOriginX + event.clientX - cameraDragStartX;
        cameraPanY = cameraDragOriginY + event.clientY - cameraDragStartY;
        applyCameraZoom();
    });

    function stopDragging(event) {
        if (!cameraDragging || event.pointerId !== cameraDragPointerId) return;
        cameraDragging = false;
        if (video.hasPointerCapture(event.pointerId)) video.releasePointerCapture(event.pointerId);
        cameraDragPointerId = null;
        viewport.classList.remove('camera-dragging');
    }
    video.addEventListener('pointerup', stopDragging);
    video.addEventListener('pointercancel', stopDragging);
    video.addEventListener('dragstart', function(event) { event.preventDefault(); });
    video.addEventListener('dblclick', resetCameraZoom);
    window.addEventListener('resize', applyCameraZoom);
}

const PATROL_ALERT_FALLBACK = {
    title: '⚠️花朵数量严重超标！',
    modalTitle: '花朵数量严重超标',
    imagesJson: '["/jhds/images/alerts/flower-overload-c2.png","/jhds/images/alerts/flower-overload-c1.png"]'
};

function patrolAlertImages(content) {
    const source = content && content.imagesJson ? content.imagesJson : PATROL_ALERT_FALLBACK.imagesJson;
    try {
        const images = JSON.parse(source);
        return Array.isArray(images) ? images.map(image => {
            if (typeof image === 'string') return { url: image, caption: '' };
            return image && typeof image === 'object' ? { url: image.url || image.imageUrl || '', caption: image.caption || '' } : null;
        }).filter(image => image && image.url) : [];
    } catch (e) {
        return [];
    }
}

async function loadPatrolPageAlert() {
    const res = await apiGet('/page-alerts/patrol-flower');
    if (!res || !res.data) return;
    patrolPageAlert = res.data;
    const content = res.data;
    const warningText = document.getElementById('patrol-warning-text');
    const modalTitle = document.getElementById('patrol-warning-modal-title');
    if (warningText) warningText.textContent = content.title || PATROL_ALERT_FALLBACK.title;
    if (modalTitle) modalTitle.textContent = content.modalTitle || content.title || PATROL_ALERT_FALLBACK.modalTitle;
    const gallery = document.getElementById('patrol-warning-images');
    const images = patrolAlertImages(content);
    if (gallery) {
        gallery.innerHTML = '';
        images.forEach((imageInfo, index) => {
            const image = document.createElement('img');
            image.src = imageInfo.url;
            image.alt = imageInfo.caption || (content.title || PATROL_ALERT_FALLBACK.title) + ' 图片' + (index + 1);
            gallery.appendChild(image);
        });
    }
    const warning = document.getElementById('patrol-warning');
    if (warning && Number(content.enabled) === 0) warning.hidden = true;
}

function openPatrolWarning() {
    const overlay = document.getElementById('patrolWarningOverlay');
    if (!overlay) return;
    overlay.hidden = false;
    document.body.style.overflow = 'hidden';
    overlay.querySelector('button').focus();
}

function closePatrolWarning() {
    const overlay = document.getElementById('patrolWarningOverlay');
    if (!overlay) return;
    overlay.hidden = true;
    document.body.style.overflow = '';
}

function openPatrolRecords() {
    const overlay = document.getElementById('patrolRecordsOverlay');
    if (!overlay) return;
    overlay.hidden = false;
    document.body.style.overflow = 'hidden';
    loadPatrolRecords();
    const closeButton = overlay.querySelector('button');
    if (closeButton) closeButton.focus();
}

function closePatrolRecords() {
    const overlay = document.getElementById('patrolRecordsOverlay');
    if (!overlay) return;
    overlay.hidden = true;
    document.body.style.overflow = '';
}

function patrolKeyTargetIsEditable(target) {
    return target && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName));
}

// The rail has no position sensor, so the backend dead-reckons travel from the
// confirmed rightmost origin and caps leftward motion. Mirror that soft limit
// here so "left" visibly disables instead of failing silently on click.
let railLeftLimitPercent = 80;
let railAtLeftLimit = false;

function applyRailLimitStatus(data) {
    if (!data) return;
    if (data.railLeftLimitPercent !== undefined && data.railLeftLimitPercent !== null) {
        railLeftLimitPercent = Number(data.railLeftLimitPercent) || railLeftLimitPercent;
    }
    const atLimit = data.railAtLeftLimit === true;
    if (atLimit !== railAtLeftLimit) {
        railAtLeftLimit = atLimit;
        if (railAtLeftLimit && currentDir === 'left') setPatrolDir('stop');
    }
    const leftButton = document.querySelector('[data-motor-direction="left"]');
    if (leftButton) {
        leftButton.disabled = railAtLeftLimit;
        leftButton.classList.toggle('soft-limited', railAtLeftLimit);
        leftButton.title = railAtLeftLimit
            ? '已到达左侧 ' + railLeftLimitPercent + '% 软限位，只能向右移动'
            : '向左移动';
    }
    const status = document.getElementById('motor-control-status');
    if (status && railAtLeftLimit && !status.classList.contains('pending')) {
        status.className = 'motor-control-status error';
        status.textContent = '已达到左侧 ' + railLeftLimitPercent + '% 软限位，只能向右移动';
    }
}

function updateMotorButtonState(dir, pending) {
    document.querySelectorAll('[data-motor-direction]').forEach(function(btn) {
        const active = dir !== 'stop' && btn.dataset.motorDirection === dir;
        btn.classList.toggle('active', active);
        btn.classList.toggle('pending', active && pending);
        btn.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
}

function togglePatrolDir(dir) {
    // Fallback for programmatic/click callers. Direction buttons are bound
    // as long-press controls in bindMotorHoldControls below.
    setPatrolDir(currentDir === dir ? 'stop' : dir);
}

async function setPatrolDir(dir) {
    const requestId = ++motorRequestId;
    if (motorStopTimer) {
        clearTimeout(motorStopTimer);
        motorStopTimer = null;
    }
    const status = document.getElementById('motor-control-status');
    currentDir = dir;
    updateMotorButtonState(dir, true);
    if (status) {
        status.className = 'motor-control-status pending';
        status.textContent = dir === 'stop' ? '正在发送停止指令...' : '正在发送 MQTT 电机指令...';
    }
    const res = await apiPost('/patrol/control', {
        dir: dir,
        // This value is created only by the user's current button/key action.
        // The backend rejects movement requests that do not include it.
        motionConfirmed: dir !== 'stop'
    });
    // A newer click owns the visible state. Do not let an older response
    // overwrite the direction selected by the user.
    if (requestId !== motorRequestId) return res;
    if (!res || res.code !== 200) {
        currentDir = 'stop';
        updateMotorButtonState('stop', false);
        if (status) {
            status.className = 'motor-control-status error';
            status.textContent = (res && res.msg) || 'MQTT 电机指令失败';
        }
        window.alert((res && res.msg) || '电机控制失败，请检查 MQTT 和串口指令配置');
    } else {
        updateMotorButtonState(dir, false);
        if (status) {
            status.className = 'motor-control-status success';
            status.textContent = dir === 'stop' ? '电机已停止' : (dir === 'left' ? 'MQTT 左移中' : 'MQTT 右移中');
        }
        if (dir === 'stop') {
            const stopButton = document.getElementById('btn-stop');
            if (stopButton) {
                stopButton.classList.remove('stop-pulse');
                void stopButton.offsetWidth;
                stopButton.classList.add('stop-pulse');
            }
        }
    }
}

function startPatrolHold(dir) {
    if (automaticPatrolRunning) return;
    if (motorStopTimer) {
        clearTimeout(motorStopTimer);
        motorStopTimer = null;
    }
    const generation = ++motorMotionGeneration;
    motorMotionActive = true;
    // Queue the start behind any in-flight command so a release cannot send
    // stop before the start request reaches the controller.
    motorCommandChain = motorCommandChain.then(function() {
        if (generation !== motorMotionGeneration) return;
        return setPatrolDir(dir);
    });
}

function stopPatrolHold() {
    if (!motorMotionActive) return;
    motorMotionActive = false;
    if (motorStopTimer) clearTimeout(motorStopTimer);
    // Delay the stop long enough for very short taps to be collapsed into a
    // single no-op by the generation guard instead of a start/stop pair.
    motorStopTimer = window.setTimeout(function() {
        motorStopTimer = null;
        const generation = ++motorMotionGeneration;
        motorCommandChain = motorCommandChain.then(function() {
            if (generation !== motorMotionGeneration) return;
            return setPatrolDir('stop');
        });
    }, 120);
}

function updatePanelMotionButtons(dir, pending) {
    document.querySelectorAll('[data-panel-direction]').forEach(function (btn) {
        const active = dir !== null && btn.dataset.panelDirection === dir;
        btn.classList.toggle('active', active);
        btn.classList.toggle('pending', active && pending);
        btn.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
}

function setPanelMotionStatus(message, error) {
    const status = document.getElementById('panel-motion-status');
    if (!status) return;
    status.textContent = message;
    status.className = 'panel-motion-status' + (error ? ' error' : '');
}

async function togglePanelMotion(direction) {
    return setPanelMotion(direction, panelMotionDir !== direction);
}

function setPanelMotion(direction, enabled) {
    if (panelMotionTimer) {
        clearTimeout(panelMotionTimer);
        panelMotionTimer = null;
    }
    const generation = ++panelMotionGeneration;
    panelMotionChain = panelMotionChain.then(function() {
        if (generation !== panelMotionGeneration) return;
        const requestId = ++panelMotionRequestId;
        updatePanelMotionButtons(enabled ? direction : null, true);
        setPanelMotionStatus(enabled ? '正在发送控制面板指令...' : '正在发送停止指令...');
        return apiPost('/control-panel/move', { direction: direction, enabled: enabled }).then(function(res) {
            if (requestId !== panelMotionRequestId) return res;
            if (!res || res.code !== 200) {
                updatePanelMotionButtons(panelMotionDir, false);
                setPanelMotionStatus((res && res.msg) || '控制面板指令失败', true);
                panelMotionActive = false;
                return res;
            }
            panelMotionDir = enabled ? direction : null;
            panelMotionActive = enabled;
            updatePanelMotionButtons(panelMotionDir, false);
            setPanelMotionStatus(enabled ? (direction === 'forward' ? '控制面板上移中' : '控制面板下移中') : '控制面板已停止');
            return res;
        });
    });
}

function startPanelMotionHold(direction) {
    if (automaticPatrolRunning) return;
    if (panelMotionTimer) {
        clearTimeout(panelMotionTimer);
        panelMotionTimer = null;
    }
    panelMotionActive = true;
    setPanelMotion(direction, true);
}

function stopPanelMotionHold(direction) {
    if (!panelMotionActive) return;
    panelMotionActive = false;
    if (panelMotionTimer) clearTimeout(panelMotionTimer);
    panelMotionTimer = window.setTimeout(function() {
        panelMotionTimer = null;
        setPanelMotion(direction || panelMotionDir || 'forward', false);
    }, 120);
}

function bindMotorHoldControls() {
    document.querySelectorAll('[data-motor-direction]').forEach(function(button) {
        const direction = button.dataset.motorDirection;
        button.addEventListener('pointerdown', function(event) {
            if (event.button !== undefined && event.button !== 0) return;
            event.preventDefault();
            if (button.setPointerCapture) button.setPointerCapture(event.pointerId);
            startPatrolHold(direction);
        });
        button.addEventListener('pointerup', function(event) {
            event.preventDefault();
            stopPatrolHold();
        });
        button.addEventListener('pointercancel', function(event) {
            event.preventDefault();
            stopPatrolHold();
        });
        button.addEventListener('lostpointercapture', function(event) {
            event.preventDefault();
            stopPatrolHold();
        });
        button.addEventListener('contextmenu', function(event) { event.preventDefault(); });
    });

    document.querySelectorAll('[data-panel-direction]').forEach(function(button) {
        const direction = button.dataset.panelDirection;
        button.addEventListener('pointerdown', function(event) {
            if (event.button !== undefined && event.button !== 0) return;
            event.preventDefault();
            if (button.setPointerCapture) button.setPointerCapture(event.pointerId);
            startPanelMotionHold(direction);
        });
        button.addEventListener('pointerup', function(event) {
            event.preventDefault();
            stopPanelMotionHold(direction);
        });
        button.addEventListener('pointercancel', function(event) {
            event.preventDefault();
            stopPanelMotionHold(direction);
        });
        button.addEventListener('lostpointercapture', function(event) {
            event.preventDefault();
            stopPanelMotionHold(direction);
        });
        button.addEventListener('contextmenu', function(event) { event.preventDefault(); });
    });
}

async function loadControlPanelStatus() {
    const connection = document.getElementById('panel-motion-connection');
    if (!connection) return;
    const res = await apiGet('/control-panel/status');
    const data = res && res.code === 200 ? res.data : null;
    const online = !!(data && data.reachable);
    connection.textContent = online ? '局域网已连接' : '局域网未连接';
    connection.className = 'panel-motion-connection ' + (online ? 'online' : 'offline');
    if (online && panelMotionDir === null && data) {
        panelMotionDir = data.forwardActive ? 'forward' : (data.backwardActive ? 'backward' : null);
        updatePanelMotionButtons(panelMotionDir, false);
    }
    connection.title = data && data.baseUrl ? data.baseUrl : 'http://169.254.240.33';
}

function setPtzStatus(message, error) {
    const status = document.getElementById('ptz-status');
    if (!status) return;
    status.textContent = message;
    status.className = 'ptz-status' + (error ? ' error' : '');
}

async function startPtz(direction, button) {
    if (activePtzDirection !== null) return;
    activePtzDirection = direction;
    activePtzStartedAt = 0;
    if (button) button.classList.add('active');
    const speed = Number((document.getElementById('ptz-speed') || {}).value || 1);
    setPtzStatus('云台移动中');
    activePtzStartPromise = apiPost('/camera/ptz/start', { direction: direction, speed: speed });
    const res = await activePtzStartPromise;
    if (!res || res.code !== 200) {
        activePtzDirection = null;
        activePtzStartedAt = 0;
        if (button) button.classList.remove('active');
        setPtzStatus((res && res.msg) || '云台控制失败', true);
    } else {
        // The cloud API acknowledges the start asynchronously. Record the
        // actual start time so a quick click cannot issue stop immediately
        // after start and produce an imperceptible movement.
        activePtzStartedAt = performance.now();
    }
}

async function stopPtz(direction, button, keepMinimumMotion) {
    const currentDirection = activePtzDirection;
    if (direction === undefined || direction === null) direction = currentDirection;
    if (direction === undefined || direction === null) return;
    // Ignore duplicate pointerup/lostpointercapture events and releases from
    // an older button after another direction has become active.
    if (currentDirection !== null && currentDirection !== direction) return;
    if (currentDirection === null && !activePtzStartPromise) return;
    // A quick pointer release can happen before the start request finishes.
    // Preserve command order so EZVIZ does not receive stop before start.
    const startPromise = activePtzStartPromise;
    activePtzDirection = null;
    activePtzStartPromise = null;
    document.querySelectorAll('.ptz-btn.active').forEach(function(btn) { btn.classList.remove('active'); });
    if (startPromise) await startPromise;
    if (keepMinimumMotion && activePtzStartedAt) {
        const elapsed = performance.now() - activePtzStartedAt;
        const minimumMotionMs = 500;
        if (elapsed < minimumMotionMs) {
            await new Promise(function(resolve) { setTimeout(resolve, minimumMotionMs - elapsed); });
        }
    }
    activePtzStartedAt = 0;
    const res = await apiPost('/camera/ptz/stop', { direction: direction });
    if (!res || res.code !== 200) setPtzStatus((res && res.msg) || '云台停止失败', true);
    else setPtzStatus('云台待命');
}

function schedulePtzStop(direction, button) {
    if (ptzStopTimer) {
        clearTimeout(ptzStopTimer);
        ptzStopTimer = null;
    }
    // A click/touch can emit pointerup before the cloud start response has
    // arrived. Keep the motor command active briefly so EZVIZ receives a
    // visible movement instead of an immediate start/stop pair.
    ptzStopTimer = window.setTimeout(function() {
        ptzStopTimer = null;
        stopPtz(direction, button, true);
    }, 900);
}

function bindPtzControls() {
    document.querySelectorAll('[data-ptz-direction]').forEach(function(button) {
        const direction = Number(button.dataset.ptzDirection);
        button.addEventListener('pointerdown', function(event) {
            event.preventDefault();
            if (button.setPointerCapture) button.setPointerCapture(event.pointerId);
            startPtz(direction, button);
        });
        button.addEventListener('pointerup', function(event) {
            event.preventDefault();
            schedulePtzStop(direction, button);
        });
        button.addEventListener('pointercancel', function(event) {
            event.preventDefault();
            stopPtz(direction, button, true);
        });
        button.addEventListener('lostpointercapture', function(event) {
            event.preventDefault();
            schedulePtzStop(direction, button);
        });
        button.addEventListener('contextmenu', function(event) { event.preventDefault(); });
    });
    const stop = document.querySelector('[data-ptz-stop]');
    if (stop) stop.addEventListener('click', function() { stopPtz(activePtzDirection, stop, false); });
}

async function loadAutomaticPatrolPlans() {
    const res = await apiGet('/patrol/auto/plans');
    const container = document.getElementById('auto-patrol-plans');
    if (!container) return;
    if (!res || res.code !== 200 || !Array.isArray(res.data)) {
        container.innerHTML = '<div class="auto-patrol-loading error">巡检方案读取失败</div>';
        return;
    }
    container.innerHTML = '';
    res.data.forEach(function(plan) {
        const selected = plan.id === selectedAutomaticPatrolPlan;
        const label = document.createElement('label');
        label.className = 'auto-patrol-plan' + (selected ? ' selected' : '');
        label.innerHTML = '<input type="radio" name="auto-patrol-plan" value="' + escapeHtml(plan.id) + '"'
            + (selected ? ' checked' : '') + '>'
            + '<span class="auto-patrol-plan-main"><strong>' + escapeHtml(plan.name) + '</strong>'
            + '<small>' + escapeHtml(plan.description) + '</small></span>'
            + '<span class="auto-patrol-plan-meta">' + Number(plan.scanRows || 0) + '线</span>';
        const input = label.querySelector('input');
        input.addEventListener('change', function() {
            selectedAutomaticPatrolPlan = plan.id;
            container.querySelectorAll('.auto-patrol-plan').forEach(function(item) {
                item.classList.toggle('selected', item.contains(input));
            });
            updateAutomaticPatrolActions();
        });
        container.appendChild(label);
    });
    updateAutomaticPatrolActions();
}

function updateAutomaticPatrolActions() {
    const origin = document.getElementById('auto-patrol-origin-confirmed');
    const start = document.getElementById('auto-patrol-start');
    const stop = document.getElementById('auto-patrol-stop');
    if (origin) origin.disabled = automaticPatrolRunning;
    if (start) start.disabled = automaticPatrolRunning || !origin || !origin.checked;
    if (stop) stop.disabled = !automaticPatrolRunning;
    document.querySelectorAll('[data-motor-direction], [data-panel-direction], [data-ptz-direction], [data-ptz-stop]').forEach(function(button) {
        button.disabled = automaticPatrolRunning;
    });
    document.querySelectorAll('input[name="auto-patrol-plan"]').forEach(function(input) {
        input.disabled = automaticPatrolRunning;
    });
    const quality = document.getElementById('camera-quality');
    const encode = document.getElementById('btn-encode');
    if (quality) quality.disabled = automaticPatrolRunning;
    if (encode) encode.disabled = automaticPatrolRunning;
}

function stopPatrolSpeechPlayback() {
    automaticPatrolSpeechGeneration++;
    if (automaticPatrolSpeechRequest) automaticPatrolSpeechRequest.abort();
    automaticPatrolSpeechRequest = null;
    if (automaticPatrolSpeechAudio) {
        automaticPatrolSpeechAudio.pause();
        automaticPatrolSpeechAudio.src = '';
    }
    if (automaticPatrolSpeechFinish) automaticPatrolSpeechFinish();
    automaticPatrolSpeechFinish = null;
    automaticPatrolSpeechAudio = null;
    automaticPatrolSpeechPlaying = false;
}

function unlockPatrolSpeechAudio() {
    const audio = new Audio('data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQAAAAA=');
    audio.volume = 0;
    const playback = audio.play();
    if (playback && typeof playback.catch === 'function') playback.catch(function() {});
}

async function playNextPatrolSpeech() {
    if (!automaticPatrolSpeechEnabled || automaticPatrolSpeechPlaying || automaticPatrolSpeechQueue.length === 0) return;
    automaticPatrolSpeechPlaying = true;
    const generation = automaticPatrolSpeechGeneration;
    const text = automaticPatrolSpeechQueue.shift();
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    automaticPatrolSpeechRequest = controller;
    let audioUrl = null;
    try {
        const response = await fetch(API_BASE + '/speech/synthesize', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text: text }),
            signal: controller ? controller.signal : undefined
        });
        const contentType = response.headers.get('Content-Type') || '';
        if (!response.ok || !/audio\/wav/i.test(contentType)) throw new Error('讯飞语音合成请求失败');
        const audioBlob = await response.blob();
        audioUrl = URL.createObjectURL(audioBlob);
        const audio = new Audio(audioUrl);
        audio.volume = 1;
        automaticPatrolSpeechAudio = audio;
        await new Promise(function(resolve, reject) {
            automaticPatrolSpeechFinish = resolve;
            audio.addEventListener('ended', resolve, { once: true });
            audio.addEventListener('error', function() { reject(new Error('讯飞语音音频播放失败')); }, { once: true });
            const playback = audio.play();
            if (playback && typeof playback.catch === 'function') playback.catch(reject);
        });
    } catch (error) {
        if (!error || error.name !== 'AbortError') console.warn('巡检语音播报失败:', error);
    } finally {
        if (audioUrl) URL.revokeObjectURL(audioUrl);
        if (generation !== automaticPatrolSpeechGeneration) return;
        automaticPatrolSpeechFinish = null;
        automaticPatrolSpeechRequest = null;
        automaticPatrolSpeechAudio = null;
        automaticPatrolSpeechPlaying = false;
        playNextPatrolSpeech();
    }
}

function speakPatrolInstruction(text, interrupt) {
    if (!automaticPatrolSpeechEnabled || !text) return;
    if (interrupt) {
        automaticPatrolSpeechQueue = [];
        stopPatrolSpeechPlayback();
    }
    if (automaticPatrolSpeechQueue[automaticPatrolSpeechQueue.length - 1] !== text) {
        automaticPatrolSpeechQueue.push(text);
    }
    playNextPatrolSpeech();
}

function patrolSpeechForStatus(data) {
    const state = String(data.state || '');
    const phase = String(data.phase || '');
    if (state === 'FAILED') return '巡检发生异常，已执行紧急停止，请检查设备连接';
    if (state === 'CANCELLED') return '自动巡检已停止，请重新确认设备位置后再启动';
    if (state === 'STOPPING') return '收到急停指令，正在停止全部设备';
    if (state === 'COMPLETED') return '自动巡检完成，设备已停止，当前位置已回到底部安全高度';
    if (state === 'QUEUED') return '巡检任务已提交，正在准备设备';
    if (/检查视频、MQTT和控制面板/.test(phase)) return '正在检查摄像头、轨道电机和升降控制面板，请稍候';
    if (/控制设备已就绪/.test(phase)) return '设备检查完成，自动巡检准备就绪';

    if (/巡检结束，下移返回底部安全高度/.test(phase)) return '蛇形扫描结束，升降电机启动，正在下移返回底部安全高度';
    let match = phase.match(/向左步进到第(\d+)条扫描线/);
    if (match) return '轨道电机启动，正在向左移动至第' + match[1] + '条扫描线';
    match = phase.match(/第(\d+)条扫描线(底部|中点|顶部)抓拍/);
    if (match) return '到达第' + match[1] + '条扫描线' + match[2] + '，正在抓拍巡检图像';
    match = phase.match(/第(\d+)条扫描线上移至(中点|顶部)/);
    if (match) return '升降电机启动，正在沿第' + match[1] + '条扫描线上移至' + match[2];
    match = phase.match(/第(\d+)条扫描线下移至(中点|底部)/);
    if (match) return '升降电机反向启动，正在沿第' + match[1] + '条扫描线下移至' + match[2];
    match = phase.match(/第(\d+)条扫描线返回底部/);
    if (match) return '升降电机反向启动，第' + match[1] + '条扫描线正在返回底部';
    return phase;
}

function announceAutomaticPatrolStatus(data) {
    if (!automaticPatrolSpeechEnabled || !data) return;
    const phase = String(data.phase || '');
    const state = String(data.state || '');
    if (phase && phase !== automaticPatrolLastSpokenPhase) {
        automaticPatrolLastSpokenPhase = phase;
        speakPatrolInstruction(patrolSpeechForStatus(data), state === 'FAILED' || state === 'CANCELLED' || state === 'STOPPING');
    }
    if (state === automaticPatrolLastSpokenState) return;
    automaticPatrolLastSpokenState = state;
    if (!phase && state !== 'IDLE') {
        speakPatrolInstruction(patrolSpeechForStatus(data), state === 'FAILED' || state === 'CANCELLED' || state === 'STOPPING');
    }
}

async function startAutomaticPatrol() {
    const origin = document.getElementById('auto-patrol-origin-confirmed');
    if (!origin || !origin.checked) {
        window.alert('请先确认轨道位于右下安全起点');
        return;
    }
    unlockPatrolSpeechAudio();
    automaticPatrolSpeechEnabled = true;
    automaticPatrolLastSpokenPhase = '';
    automaticPatrolLastSpokenState = '';
    const selectedPlan = document.querySelector('input[name="auto-patrol-plan"]:checked');
    const selectedPlanName = selectedPlan && selectedPlan.closest('.auto-patrol-plan')
        ? selectedPlan.closest('.auto-patrol-plan').querySelector('strong') : null;
    speakPatrolInstruction('开始执行' + (selectedPlanName ? selectedPlanName.textContent : '自动巡检')
        + '方案，请确保轨道和种植架周围无人', true);
    const res = await apiPost('/patrol/auto/start', {
        planId: selectedAutomaticPatrolPlan,
        originConfirmed: true
    });
    if (!res || res.code !== 200) {
        speakPatrolInstruction('自动巡检启动失败，请检查设备连接', true);
        automaticPatrolSpeechEnabled = false;
        window.alert((res && res.msg) || '自动巡检启动失败');
        return;
    }
    automaticPatrolRunning = true;
    updateAutomaticPatrolActions();
    loadAutomaticPatrolStatus();
}

async function stopAutomaticPatrol() {
    const stop = document.getElementById('auto-patrol-stop');
    if (stop) stop.disabled = true;
    speakPatrolInstruction('收到停止指令，正在停止轨道电机、升降电机和摄像云台', true);
    const res = await apiPost('/patrol/auto/stop', {});
    if (!res || res.code !== 200) {
        window.alert((res && res.msg) || '停止指令发送失败');
    }
    const origin = document.getElementById('auto-patrol-origin-confirmed');
    if (origin) origin.checked = false;
    loadAutomaticPatrolStatus();
}

async function loadAutomaticPatrolStatus() {
    const res = await apiGet('/patrol/auto/status');
    if (!res || res.code !== 200 || !res.data) return;
    const data = res.data;
    automaticPatrolRunning = !!data.running;
    announceAutomaticPatrolStatus(data);
    const stateClass = automaticPatrolRunning ? 'running'
        : (data.state === 'COMPLETED' ? 'completed' : ((data.state === 'FAILED' || data.state === 'CANCELLED') ? 'error' : ''));
    const live = document.getElementById('auto-patrol-live');
    if (live) {
        live.textContent = automaticPatrolRunning ? '运行中' : (data.state === 'COMPLETED' ? '已完成' : (data.state === 'FAILED' ? '异常' : (data.state === 'CANCELLED' ? '已停止' : '待命')));
        live.className = 'auto-patrol-live ' + stateClass;
    }
    const phase = document.getElementById('auto-patrol-phase');
    const progressText = document.getElementById('auto-patrol-progress-text');
    const progressBar = document.getElementById('auto-patrol-progress-bar');
    const row = document.getElementById('auto-patrol-row');
    const captures = document.getElementById('auto-patrol-captures');
    const output = document.getElementById('auto-patrol-output');
    const message = document.getElementById('auto-patrol-message');
    const patrolQuality = document.getElementById('auto-patrol-quality');
    const progress = Math.max(0, Math.min(100, Number(data.progress || 0)));
    if (phase) phase.textContent = data.phase || '等待开始';
    if (progressText) progressText.textContent = progress + '%';
    if (progressBar) progressBar.style.width = progress + '%';
    if (row) row.textContent = Number(data.currentRow || 0) + '/' + Number(data.totalRows || 0);
    if (captures) captures.textContent = Number(data.captureCount || 0);
    applyCameraQualityStatus(data);
    if (output) {
        output.textContent = data.outputPath || '保存目录未配置';
        output.title = data.outputPath || '';
    }
    // A preflight MQTT failure remains in the last-run record. Once the
    // gateway reconnects, hide that stale message without hiding new errors.
    const staleMqttError = data.lastError === 'MQTT网关未连接' && data.mqttConnected !== false;
    const notice = (staleMqttError ? null : data.lastError) || data.warning;
    if (message) {
        message.hidden = !notice;
        message.className = 'auto-patrol-message ' + (data.lastError ? 'error' : 'warning');
        message.textContent = notice || '';
    }
    if (patrolQuality && data.quality) {
        const width = Number(data.actualWidth || 0);
        const height = Number(data.actualHeight || 0);
        patrolQuality.textContent = data.quality === '4k' && data.qualityVerified === true
            && width === 3840 && height === 2160 ? '真实4K' : '4K待验证';
    }
    if (!automaticPatrolRunning && data.state === 'CANCELLED') {
        const origin = document.getElementById('auto-patrol-origin-confirmed');
        if (origin) origin.checked = false;
    }
    applyRailLimitStatus(data);
    updateAutomaticPatrolActions();
}

async function loadPatrolRecords() {
    const res = await apiGet('/patrol/records');
    if (!res || !res.data) return;
    const container = document.getElementById('patrol-records');
    if (!container) return;
    container.innerHTML = '';
    res.data.forEach(function(record) {
        const div = document.createElement('div');
        div.style.cssText = 'min-width:140px;height:90px;border-radius:8px;overflow:hidden;position:relative;border:1px solid rgba(30,60,100,0.3);cursor:pointer;flex-shrink:0;';
        if (record.imageUrl) {
            div.innerHTML = '<img src="' + record.imageUrl + '" style="width:100%;height:100%;object-fit:cover;">'
                + '<div style="position:absolute;bottom:0;left:0;right:0;padding:3px 6px;background:rgba(0,0,0,0.7);font-size:10px;color:#aab;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">'
                + (record.trackPosition || '') + ' ' + formatTime(record.shootTime) + '</div>';
            if (record.aiStatus === 2) {
                div.innerHTML += '<div style="position:absolute;top:4px;right:4px;width:16px;height:16px;border-radius:50%;background:rgba(0,232,135,0.9);display:flex;align-items:center;justify-content:center;font-size:10px;color:#000;"><i class="ri-check-line"></i></div>';
            }
            div.onclick = function() { viewPatrolRecord(record); };
        } else {
            div.innerHTML = '<div style="width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#0a1f2e,#0d2a3a);"><i class="ri-image-line" style="font-size:28px;color:#1e3550;"></i></div>';
        }
        container.appendChild(div);
    });
    if (res.data.length === 0) {
        for (var i = 0; i < 3; i++) {
            var ph = document.createElement('div');
            ph.className = 'record-placeholder';
            ph.style.cssText = 'min-width:140px;height:90px;background:linear-gradient(135deg,#0a1f2e,#0d2a3a);border-radius:8px;display:flex;align-items:center;justify-content:center;border:1px solid rgba(30,60,100,0.3);';
            ph.innerHTML = '<i class="ri-image-line" style="font-size:28px;color:#1e3550;"></i>';
            container.appendChild(ph);
        }
    }
}

function formatTime(dateVal) {
    if (!dateVal) return '';
    var d = new Date(dateVal);
    return String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0');
}

function viewPatrolRecord(record) {
    if (!record.imageUrl) return;
    showAiCaptureModal(null);
    document.getElementById('capturePreview').src = record.imageUrl;
    var resultEl = document.getElementById('captureResult');
    if (record.aiResult) {
        resultEl.innerHTML = '<div class="ai-capture-result-text">' + marked.parse(record.aiResult) + '</div>';
    } else {
        resultEl.innerHTML = '<div class="ai-capture-result-text" style="color:var(--text-dim);">暂无分析结果</div>';
    }
    document.getElementById('retryCaptureBtn').style.display = 'none';
}

function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function getTrackPosition() {
    var el = document.getElementById('track-pos');
    return el ? el.textContent : '';
}

function captureVideoFrame() {
    var video = document.getElementById('video-player');
    if (!video || !video.videoWidth) return null;
    var MAX = 640;
    var w = video.videoWidth || 1280;
    var h = video.videoHeight || 720;
    if (w > MAX || h > MAX) {
        var ratio = Math.min(MAX / w, MAX / h);
        w = Math.round(w * ratio);
        h = Math.round(h * ratio);
    }
    var canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    canvas.getContext('2d').drawImage(video, 0, 0, w, h);
    return canvas.toDataURL('image/jpeg', 0.7).split(',')[1];
}

function showAiCaptureModal(imageBase64) {
    var overlay = document.getElementById('aiCaptureOverlay');
    if (imageBase64) {
        document.getElementById('capturePreview').src = 'data:image/jpeg;base64,' + imageBase64;
    }
    var resultEl = document.getElementById('captureResult');
    resultEl.innerHTML = '<div class="ai-capture-thinking">'
        + '<div class="thinking-spinner"></div>'
        + '<span>AI 正在分析中，请稍候...</span>'
        + '</div>';
    document.getElementById('retryCaptureBtn').style.display = 'none';
    overlay.style.display = 'flex';
    document.body.style.overflow = 'hidden';
}

function closeAiCapture() {
    document.getElementById('aiCaptureOverlay').style.display = 'none';
    document.body.style.overflow = '';
}

function startAiCaptureStream(imageBase64, trackPosition) {
    var resultEl = document.getElementById('captureResult');
    var fullText = '';
    var firstChunk = true;
    var hasError = false;

    fetch(window.location.origin + '/jhds/api/patrol/ai-capture', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ image: imageBase64, trackPosition: trackPosition })
    })
    .then(function(response) {
        if (!response.ok) throw new Error('HTTP error: ' + response.status);

        var reader = response.body.getReader();
        var decoder = new TextDecoder();
        var buffer = '';
        var currentEvent = '';

        function readNext() {
            reader.read().then(function(result) {
                if (result.done) {
                    isAiCapturing = false;
                    if (!hasError) {
                        document.getElementById('retryCaptureBtn').style.display = 'inline-flex';
                    }
                    loadPatrolRecords();
                    return;
                }

                buffer += decoder.decode(result.value, { stream: true });
                var lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (var i = 0; i < lines.length; i++) {
                    var line = lines[i].trim();
                    if (line.startsWith('event:')) {
                        currentEvent = line.substring(6).trim();
                        continue;
                    }
                    if (line.startsWith('data:')) {
                        var data = line.substring(5).trim();
                        if (data === '[DONE]') {
                            continue;
                        }
                        if (currentEvent === 'error') {
                            hasError = true;
                            resultEl.innerHTML = '<div class="ai-capture-result-text" style="color:var(--accent-warn);">' + escapeHtml(data) + '</div>';
                            document.getElementById('retryCaptureBtn').style.display = 'inline-flex';
                            isAiCapturing = false;
                        } else if (currentEvent === 'meta') {
                            // metadata received, ignore for display
                        } else if (currentEvent === 'message') {
                            if (firstChunk) {
                                firstChunk = false;
                                resultEl.innerHTML = '<div class="ai-capture-result-text"></div>';
                            }
                            try {
                                var parsed = JSON.parse(data);
                                if (parsed && parsed.text) {
                                    fullText += parsed.text;
                                }
                            } catch(e) {
                                fullText += data;
                            }
                            resultEl.querySelector('.ai-capture-result-text').innerHTML = marked.parse(fullText);
                        }
                    }
                }
                readNext();
            }).catch(function(err) {
                isAiCapturing = false;
                hasError = true;
                resultEl.innerHTML = '<div class="ai-capture-result-text" style="color:var(--accent-warn);">接收数据失败: ' + escapeHtml(err.message) + '</div>';
                document.getElementById('retryCaptureBtn').style.display = 'inline-flex';
            });
        }

        readNext();
    })
    .catch(function(err) {
        isAiCapturing = false;
        hasError = true;
        resultEl.innerHTML = '<div class="ai-capture-result-text" style="color:var(--accent-warn);">请求失败: ' + escapeHtml(err.message) + '</div>';
        document.getElementById('retryCaptureBtn').style.display = 'inline-flex';
    });
}

async function triggerAiCapture() {
    if (isAiCapturing) return;
    var imageBase64 = captureVideoFrame();
    if (!imageBase64) {
        alert('摄像头尚未就绪，请等待视频加载完成');
        return;
    }
    isAiCapturing = true;
    var trackPos = getTrackPosition();
    showAiCaptureModal(imageBase64);
    startAiCaptureStream(imageBase64, trackPos);
}

function retryAiCapture() {
    var preview = document.getElementById('capturePreview');
    var base64Str = preview.src;
    if (base64Str && base64Str.startsWith('data:image/jpeg;base64,')) {
        base64Str = base64Str.split(',')[1];
    } else {
        base64Str = captureVideoFrame();
        if (!base64Str) { alert('摄像头尚未就绪'); return; }
        preview.src = 'data:image/jpeg;base64,' + base64Str;
    }
    isAiCapturing = true;
    startAiCaptureStream(base64Str, getTrackPosition());
}

let __flvPlayer = null;
let __hlsPlayer = null;
let patrolVideoReady = false;

function destroyCameraPlayer() {
    if (cameraRecoveryTimer) {
        clearTimeout(cameraRecoveryTimer);
        cameraRecoveryTimer = null;
    }
    if (cameraLatencyTimer) {
        clearInterval(cameraLatencyTimer);
        cameraLatencyTimer = null;
    }
    if (cameraWatchdogTimer) {
        clearInterval(cameraWatchdogTimer);
        cameraWatchdogTimer = null;
    }
    if (__hlsPlayer) {
        try { __hlsPlayer.destroy(); } catch (e) { /* ignore */ }
        __hlsPlayer = null;
    }
    if (__flvPlayer) {
        try {
            __flvPlayer.pause();
            __flvPlayer.unload();
            __flvPlayer.detachMediaElement();
            __flvPlayer.destroy();
        } catch (e) { /* ignore */ }
        __flvPlayer = null;
    }
    var video = document.getElementById('video-player');
    if (video) {
        video.onplaying = null;
        video.onerror = null;
        video.onstalled = null;
        video.onwaiting = null;
        video.ontimeupdate = null;
        video.pause();
        video.removeAttribute('src');
        video.load();
        applyCameraZoom();
    }
    patrolVideoReady = false;
}

function captureRealtimeFrame() {
    var video = document.getElementById('video-player');
    if (!video || !video.videoWidth || video.readyState < 2) return null;
    var maxSize = 1024;
    var width = video.videoWidth;
    var height = video.videoHeight;
    var ratio = Math.min(1, maxSize / Math.max(width, height));
    width = Math.max(1, Math.round(width * ratio));
    height = Math.max(1, Math.round(height * ratio));
    var canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    try {
        canvas.getContext('2d', { alpha: false }).drawImage(video, 0, 0, width, height);
        return { image: canvas.toDataURL('image/jpeg', 0.76).split(',')[1], width: width, height: height };
    } catch (error) {
        console.warn('实时视频帧截取失败:', error);
        return null;
    }
}

function setRealtimeDetectionStatus(state, text) {
    var element = document.getElementById('realtime-detect-status');
    if (!element) return;
    element.className = 'realtime-detect-status ' + state;
    element.textContent = text;
}

function clearRealtimeDetections() {
    var canvas = document.getElementById('realtime-detection-canvas');
    if (canvas) {
        var context = canvas.getContext('2d');
        context.clearRect(0, 0, canvas.width, canvas.height);
    }
}

function drawRealtimeDetections(data) {
    var canvas = document.getElementById('realtime-detection-canvas');
    var video = document.getElementById('video-player');
    if (!canvas || !video) return;
    var cssWidth = canvas.clientWidth;
    var cssHeight = canvas.clientHeight;
    if (!cssWidth || !cssHeight || !data.width || !data.height) return;
    var pixelRatio = window.devicePixelRatio || 1;
    var targetWidth = Math.round(cssWidth * pixelRatio);
    var targetHeight = Math.round(cssHeight * pixelRatio);
    if (canvas.width !== targetWidth || canvas.height !== targetHeight) {
        canvas.width = targetWidth;
        canvas.height = targetHeight;
    }
    var context = canvas.getContext('2d');
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    context.clearRect(0, 0, cssWidth, cssHeight);

    var sourceRatio = Number(data.width) / Number(data.height);
    var viewportRatio = cssWidth / cssHeight;
    var displayWidth;
    var displayHeight;
    var offsetX = 0;
    var offsetY = 0;
    if (viewportRatio > sourceRatio) {
        displayHeight = cssHeight;
        displayWidth = displayHeight * sourceRatio;
        offsetX = (cssWidth - displayWidth) / 2;
    } else {
        displayWidth = cssWidth;
        displayHeight = displayWidth / sourceRatio;
        offsetY = (cssHeight - displayHeight) / 2;
    }
    var scaleX = displayWidth / Number(data.width);
    var scaleY = displayHeight / Number(data.height);
    (data.detections || []).forEach(function(detection) {
        var box = detection.box || [];
        if (box.length !== 4) return;
        var x = offsetX + Number(box[0]) * scaleX;
        var y = offsetY + Number(box[1]) * scaleY;
        var width = (Number(box[2]) - Number(box[0])) * scaleX;
        var height = (Number(box[3]) - Number(box[1])) * scaleY;
        var score = (Number(detection.confidence || 0) * 100).toFixed(1) + '%';
        var label = '黑天牛 ' + score;
        context.lineWidth = Math.max(2, Math.min(4, cssWidth / 280));
        context.strokeStyle = '#ff365c';
        context.shadowColor = 'rgba(255,54,92,.85)';
        context.shadowBlur = 8;
        context.strokeRect(x, y, width, height);
        context.shadowBlur = 0;
        context.font = '600 13px "Microsoft YaHei", sans-serif';
        var labelWidth = context.measureText(label).width + 14;
        var labelHeight = 25;
        var labelY = Math.max(0, y - labelHeight);
        context.fillStyle = 'rgba(218,18,54,.94)';
        context.fillRect(x, labelY, labelWidth, labelHeight);
        context.fillStyle = '#fff';
        context.fillText(label, x + 7, labelY + 17);
    });
}

function showRealtimeAlert(data) {
    var banner = document.getElementById('realtime-alert-banner');
    var detail = document.getElementById('realtime-alert-detail');
    if (!banner || Date.now() - realtimeAlertDismissedAt < 10000) return;
    var detections = data.detections || [];
    var maxConfidence = detections.reduce(function(maximum, detection) {
        return Math.max(maximum, Number(detection.confidence || 0));
    }, 0);
    if (detail) detail.textContent = '检测到 ' + detections.length + ' 个目标，最高置信度 '
            + (maxConfidence * 100).toFixed(1) + '%';
    banner.hidden = false;
    realtimeLastDetectionAt = Date.now();
    if (!realtimeWasDetected || Date.now() - realtimeLastSoundAt > 15000) playRealtimeWarningSound();
}

function playRealtimeWarningSound() {
    realtimeLastSoundAt = Date.now();
    try {
        var AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextClass) return;
        realtimeAudioContext = realtimeAudioContext || new AudioContextClass();
        if (realtimeAudioContext.state === 'suspended') realtimeAudioContext.resume();
        var oscillator = realtimeAudioContext.createOscillator();
        var gain = realtimeAudioContext.createGain();
        oscillator.type = 'square';
        oscillator.frequency.setValueAtTime(880, realtimeAudioContext.currentTime);
        oscillator.frequency.setValueAtTime(660, realtimeAudioContext.currentTime + 0.18);
        gain.gain.setValueAtTime(0.0001, realtimeAudioContext.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.16, realtimeAudioContext.currentTime + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.0001, realtimeAudioContext.currentTime + 0.42);
        oscillator.connect(gain);
        gain.connect(realtimeAudioContext.destination);
        oscillator.start();
        oscillator.stop(realtimeAudioContext.currentTime + 0.44);
    } catch (error) {
        console.debug('浏览器暂未允许告警声音:', error);
    }
}

function unlockRealtimeWarningSound() {
    try {
        var AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextClass) return;
        realtimeAudioContext = realtimeAudioContext || new AudioContextClass();
        if (realtimeAudioContext.state === 'suspended') realtimeAudioContext.resume();
    } catch (error) { /* visual and persisted warnings remain available */ }
}

function dismissRealtimeAlert() {
    realtimeAlertDismissedAt = Date.now();
    var banner = document.getElementById('realtime-alert-banner');
    if (banner) banner.hidden = true;
}

function scheduleRealtimeDetection(delay) {
    if (realtimeDetectionTimer) clearTimeout(realtimeDetectionTimer);
    if (!realtimeDetectionEnabled) return;
    realtimeDetectionTimer = window.setTimeout(runRealtimeDetection, delay == null ? realtimeDetectionInterval : delay);
}

async function runRealtimeDetection() {
    var detectionStartedAt = Date.now();
    realtimeDetectionTimer = null;
    if (!realtimeDetectionEnabled || document.hidden || !patrolVideoReady || realtimeDetectionInFlight) {
        scheduleRealtimeDetection(600);
        return;
    }
    var frame = captureRealtimeFrame();
    if (!frame) {
        scheduleRealtimeDetection(800);
        return;
    }
    realtimeDetectionInFlight = true;
    setRealtimeDetectionStatus('warming', realtimeDetectionErrors ? '识别重试中' : '识别中…');
    try {
        var response = await apiPost('/patrol/realtime-detect', {
            image: frame.image,
            confidence: 0.25,
            location: getTrackPosition() || 'AI轨道巡检摄像头'
        });
        if (!response || response.code !== 200 || !response.data) {
            throw new Error(response && response.msg ? response.msg : '实时识别服务无响应');
        }
        realtimeDetectionErrors = 0;
        drawRealtimeDetections(response.data);
        var detected = !!response.data.detected;
        if (detected) {
            setRealtimeDetectionStatus('detected', '发现 ' + response.data.count + ' 个目标');
            showRealtimeAlert(response.data);
        } else {
            setRealtimeDetectionStatus('running', '监测中 · 未发现');
            var banner = document.getElementById('realtime-alert-banner');
            if (banner && Date.now() - realtimeLastDetectionAt > 8000) banner.hidden = true;
        }
        realtimeWasDetected = detected;
    } catch (error) {
        realtimeDetectionErrors++;
        clearRealtimeDetections();
        setRealtimeDetectionStatus('error', realtimeDetectionErrors >= 3 ? '模型连接失败' : '模型重连中');
        console.warn('实时虫害识别失败:', error);
    } finally {
        realtimeDetectionInFlight = false;
        var elapsed = Date.now() - detectionStartedAt;
        scheduleRealtimeDetection(realtimeDetectionErrors ? 2500 : Math.max(50, realtimeDetectionInterval - elapsed));
    }
}

function toggleRealtimeDetection() {
    if (!realtimeDetectionEnabled) {
        setRealtimeDetectionStatus('', '监测已关闭');
        return;
    }
    realtimeDetectionEnabled = !realtimeDetectionEnabled;
    var button = document.getElementById('realtime-detect-toggle');
    if (button) {
        button.classList.toggle('active', realtimeDetectionEnabled);
        button.setAttribute('aria-pressed', realtimeDetectionEnabled ? 'true' : 'false');
        button.innerHTML = '<i class="ri-radar-line"></i><span>虫害监测 ' + (realtimeDetectionEnabled ? '开' : '关') + '</span>';
    }
    if (realtimeDetectionEnabled) {
        try {
            var AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (AudioContextClass) realtimeAudioContext = realtimeAudioContext || new AudioContextClass();
        } catch (error) { /* visual warning remains available */ }
        setRealtimeDetectionStatus('warming', '模型准备中');
        scheduleRealtimeDetection(50);
    } else {
        if (realtimeDetectionTimer) clearTimeout(realtimeDetectionTimer);
        realtimeDetectionTimer = null;
        realtimeWasDetected = false;
        clearRealtimeDetections();
        dismissRealtimeAlert();
        setRealtimeDetectionStatus('', '监测已关闭');
    }
}

function startCameraWatchdog(video) {
    if (cameraWatchdogTimer) clearInterval(cameraWatchdogTimer);
    cameraInitStartedAt = Date.now();
    cameraLastProgressAt = cameraInitStartedAt;
    cameraWatchdogTimer = window.setInterval(function() {
        if (document.hidden || cameraRecoveryTimer) return;
        const now = Date.now();
        const loadingTooLong = !patrolVideoReady && now - cameraInitStartedAt > 30000;
        const playbackStalled = patrolVideoReady && now - cameraLastProgressAt > 15000;
        if (loadingTooLong || playbackStalled) {
            setCameraStatus('loading', '视频流无响应，正在自动恢复');
            showCameraPlaceholder('视频流无响应，正在自动重连');
            scheduleCameraRecovery(300, cameraPreferredProtocol);
        }
    }, 2000);
}

function startLiveLatencyMonitor(video) {
    if (cameraLatencyTimer) clearInterval(cameraLatencyTimer);
    // A live stream should not silently build a multi-second buffer after a
    // brief network stall. Catch up only when the buffered tail is clearly
    // stale, avoiding constant seeks during normal playback.
    cameraLatencyTimer = window.setInterval(function() {
        if (!video || video.paused || video.seeking || video.readyState < 3 || !video.buffered.length) return;
        const end = video.buffered.end(video.buffered.length - 1);
        const lag = end - video.currentTime;
        if (lag > 2.5) {
            video.currentTime = Math.max(0, end - 0.35);
        }
    }, 1000);
}

function scheduleCameraRecovery(delay, protocol) {
    if (cameraRecoveryTimer || document.hidden) return;
    cameraRecoveryAttempts = Math.min(cameraRecoveryAttempts + 1, 5);
    const backoff = Math.min(15000, Math.max(1000, (delay || 1800) * Math.pow(1.6, cameraRecoveryAttempts - 1)));
    cameraRecoveryTimer = window.setTimeout(function() {
        cameraRecoveryTimer = null;
        initCamera(protocol);
    }, backoff);
}

function cancelPendingCameraRecovery() {
    if (cameraRecoveryTimer) {
        clearTimeout(cameraRecoveryTimer);
        cameraRecoveryTimer = null;
    }
    cameraRecoveryAttempts = 0;
}

function updateAudioButton() {
    const video = document.getElementById('video-player');
    const button = document.getElementById('btn-audio');
    if (!video || !button) return;
    const enabled = !video.muted;
    button.className = 'audio-btn' + (enabled ? ' enabled' : '');
    button.innerHTML = enabled ? '<i class="ri-volume-up-line"></i>关闭声音' : '<i class="ri-volume-mute-line"></i>开启声音';
}

function toggleCameraAudio() {
    const video = document.getElementById('video-player');
    if (!video) return;
    video.muted = !video.muted;
    cameraAudioEnabled = !video.muted;
    updateAudioButton();
    video.play().catch(function() {
        if (!video.muted) {
            video.muted = true;
            updateAudioButton();
            setCameraStatus('ready', '画面已连接，请再次点击开启声音');
        }
    });
}

function setVoiceBroadcastStatus(message, state) {
    const status = document.getElementById('voice-broadcast-status');
    if (!status) return;
    status.className = 'voice-broadcast-status' + (state ? ' ' + state : '');
    status.textContent = message;
}

function openVoiceBroadcast() {
    const overlay = document.getElementById('voiceBroadcastOverlay');
    if (overlay) overlay.hidden = false;
}

function closeVoiceBroadcast() {
    const overlay = document.getElementById('voiceBroadcastOverlay');
    if (overlay) overlay.hidden = true;
    if (voiceMediaRecorder && voiceMediaRecorder.state === 'recording') stopVoiceRecording();
}

function updateVoiceRecordingTime() {
    const elapsed = Math.min(30, Math.floor((Date.now() - voiceRecordStartedAt) / 1000));
    const label = document.getElementById('voice-record-time');
    const progress = document.querySelector('#voice-record-progress span');
    if (label) label.textContent = '00:' + String(elapsed).padStart(2, '0');
    if (progress) progress.style.width = (elapsed / 30 * 100) + '%';
    if (elapsed >= 30) stopVoiceRecording();
}

async function toggleVoiceRecording() {
    if (voiceMediaRecorder && voiceMediaRecorder.state === 'recording') {
        stopVoiceRecording();
        return;
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || typeof MediaRecorder === 'undefined') {
        setVoiceBroadcastStatus('当前浏览器不支持录音，请选择WAV文件', 'error');
        return;
    }
    try {
        voiceMediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const mimeTypes = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus'];
        const mimeType = mimeTypes.find(function(type) { return MediaRecorder.isTypeSupported(type); });
        voiceRecordChunks = [];
        voiceMediaRecorder = mimeType
            ? new MediaRecorder(voiceMediaStream, { mimeType: mimeType })
            : new MediaRecorder(voiceMediaStream);
        voiceMediaRecorder.ondataavailable = function(event) {
            if (event.data && event.data.size) voiceRecordChunks.push(event.data);
        };
        voiceMediaRecorder.onstop = finishVoiceRecording;
        voiceMediaRecorder.start();
        voiceRecordStartedAt = Date.now();
        const button = document.getElementById('voice-record-btn');
        const progress = document.getElementById('voice-record-progress');
        if (button) {
            button.classList.add('recording');
            button.innerHTML = '<i class="ri-stop-circle-line"></i><span>结束录音</span>';
        }
        if (progress) progress.hidden = false;
        updateVoiceRecordingTime();
        voiceRecordTimer = window.setInterval(updateVoiceRecordingTime, 250);
        setVoiceBroadcastStatus('正在录音，结束后会生成可预览的WAV', 'recording');
    } catch (error) {
        releaseVoiceMicrophone();
        setVoiceBroadcastStatus(error && error.name === 'NotAllowedError'
            ? '未获得麦克风权限，请允许访问后重试'
            : '无法启动录音，请检查麦克风', 'error');
    }
}

function stopVoiceRecording() {
    if (voiceMediaRecorder && voiceMediaRecorder.state === 'recording') voiceMediaRecorder.stop();
}

function releaseVoiceMicrophone() {
    if (voiceRecordTimer) {
        clearInterval(voiceRecordTimer);
        voiceRecordTimer = null;
    }
    if (voiceMediaStream) {
        voiceMediaStream.getTracks().forEach(function(track) { track.stop(); });
        voiceMediaStream = null;
    }
}

async function finishVoiceRecording() {
    const button = document.getElementById('voice-record-btn');
    const progress = document.getElementById('voice-record-progress');
    if (button) {
        button.classList.remove('recording');
        button.innerHTML = '<i class="ri-mic-line"></i><span>重新录音</span>';
    }
    if (progress) progress.hidden = true;
    releaseVoiceMicrophone();
    try {
        setVoiceBroadcastStatus('正在处理录音', 'sending');
        const recorded = new Blob(voiceRecordChunks, {
            type: (voiceMediaRecorder && voiceMediaRecorder.mimeType) || 'audio/webm'
        });
        const wav = await convertRecordedAudioToWav(recorded);
        selectVoiceFile(new File([wav], 'voice-' + Date.now() + '.wav', { type: 'audio/wav' }));
    } catch (error) {
        console.warn('Voice recording conversion failed:', error);
        setVoiceBroadcastStatus('录音转换失败，请改用WAV文件', 'error');
    } finally {
        voiceMediaRecorder = null;
        voiceRecordChunks = [];
    }
}

async function convertRecordedAudioToWav(blob) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) throw new Error('AudioContext unavailable');
    const context = new AudioContextClass();
    try {
        const source = await context.decodeAudioData(await blob.arrayBuffer());
        const targetRate = 16000;
        const ratio = source.sampleRate / targetRate;
        const frameCount = Math.max(1, Math.floor(source.length / ratio));
        const samples = new Float32Array(frameCount);
        const channels = [];
        for (let channel = 0; channel < source.numberOfChannels; channel++) {
            channels.push(source.getChannelData(channel));
        }
        for (let i = 0; i < frameCount; i++) {
            const position = i * ratio;
            const left = Math.floor(position);
            const right = Math.min(source.length - 1, left + 1);
            const fraction = position - left;
            let mixed = 0;
            channels.forEach(function(data) {
                mixed += data[left] + (data[right] - data[left]) * fraction;
            });
            samples[i] = mixed / channels.length;
        }
        return encodePcmWav(samples, targetRate);
    } finally {
        context.close().catch(function() {});
    }
}

function encodePcmWav(samples, sampleRate) {
    const buffer = new ArrayBuffer(44 + samples.length * 2);
    const view = new DataView(buffer);
    function writeText(offset, text) {
        for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i));
    }
    writeText(0, 'RIFF');
    view.setUint32(4, 36 + samples.length * 2, true);
    writeText(8, 'WAVE');
    writeText(12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    writeText(36, 'data');
    view.setUint32(40, samples.length * 2, true);
    for (let i = 0; i < samples.length; i++) {
        const sample = Math.max(-1, Math.min(1, samples[i]));
        view.setInt16(44 + i * 2, sample < 0 ? sample * 32768 : sample * 32767, true);
    }
    return new Blob([buffer], { type: 'audio/wav' });
}

function selectVoiceFile(file) {
    if (!file) return;
    if (!/\.wav$/i.test(file.name || '')) {
        setVoiceBroadcastStatus('仅支持WAV语音文件', 'error');
        return;
    }
    if (file.size > 20 * 1024 * 1024) {
        setVoiceBroadcastStatus('语音文件不能超过20MB', 'error');
        return;
    }
    voiceBroadcastFile = file;
    if (voicePreviewUrl) URL.revokeObjectURL(voicePreviewUrl);
    voicePreviewUrl = URL.createObjectURL(file);
    const preview = document.getElementById('voice-preview');
    const name = document.getElementById('voice-file-name');
    const detail = document.getElementById('voice-file-detail');
    const send = document.getElementById('voice-send-btn');
    if (preview) {
        preview.src = voicePreviewUrl;
        preview.hidden = false;
    }
    if (name) name.textContent = file.name;
    if (detail) detail.textContent = (file.size / 1024).toFixed(1) + ' KB · WAV';
    if (send) send.disabled = false;
    setVoiceBroadcastStatus('语音已就绪，可试听后发送', 'ready');
}

async function sendVoiceBroadcast() {
    if (!voiceBroadcastFile) return;
    const button = document.getElementById('voice-send-btn');
    if (button) {
        button.disabled = true;
        button.innerHTML = '<i class="ri-loader-4-line"></i>正在发送';
    }
    setVoiceBroadcastStatus('正在通过萤石云下发到摄像头', 'sending');
    try {
        const body = new FormData();
        body.append('voiceFile', voiceBroadcastFile, voiceBroadcastFile.name || 'voice.wav');
        const response = await fetch(API_BASE + '/camera/voice/broadcast', { method: 'POST', body: body });
        const result = await response.json();
        if (!result || result.code !== 200) throw new Error((result && result.msg) || '语音下发失败');
        setVoiceBroadcastStatus('播报指令已发送，摄像头正在播放', 'success');
        if (button) button.innerHTML = '<i class="ri-check-line"></i>已发送';
    } catch (error) {
        setVoiceBroadcastStatus(error.message || '语音下发失败', 'error');
        if (button) button.innerHTML = '<i class="ri-send-plane-2-line"></i>重新发送';
    } finally {
        if (button) button.disabled = false;
    }
}

function playCameraVideo(video) {
    // Browsers block autoplay with sound until a user gesture. Start muted,
    // then let the user explicitly enable audio with the button.
    video.muted = !cameraAudioEnabled;
    updateAudioButton();
    video.play().catch(function() {
        video.muted = true;
        updateAudioButton();
        video.play().catch(function() {});
    });
}

function setCameraStatus(state, message) {
    var badge = document.getElementById('camera-status-badge');
    var label = document.getElementById('camera-connection-status');
    var labels = { checking: '检查中', loading: '加载中', ready: 'LIVE', error: '离线' };
    if (badge) {
        badge.className = 'status-badge camera-status-badge ' + state;
        badge.textContent = labels[state] || labels.checking;
    }
    if (label) {
        label.className = 'camera-connection-status ' + state;
        label.textContent = message || '';
    }
}

function showCameraPlaceholder(message) {
    var placeholder = document.getElementById('video-placeholder');
    var detail = document.getElementById('video-placeholder-message');
    if (detail && message) detail.textContent = message;
    if (placeholder) placeholder.style.display = 'flex';
}

async function initCamera(protocolOverride) {
    const video = document.getElementById('video-player');
    if (!video) return;

    const requestedProtocol = protocolOverride || cameraPreferredProtocol;
    if (requestedProtocol !== 2 && requestedProtocol !== 4) cameraPreferredProtocol = 4;
    const protocolQuery = '?protocol=' + encodeURIComponent(requestedProtocol);

    destroyCameraPlayer();
    startCameraWatchdog(video);
    video.muted = !cameraAudioEnabled;
    updateAudioButton();
    setCameraStatus('checking', '正在检查萤石连接');
    showCameraPlaceholder('正在检查萤石摄像头连接');

    try {
        setCameraStatus('loading', '正在加载实时画面');
        const res = await apiGet('/camera/play-url' + protocolQuery);
        if (!res || !res.data) {
            const message = (res && res.msg) || '未获取到萤石播放地址';
            const starting = res && res.code === 503;
            setCameraStatus(starting ? 'loading' : 'error', message);
            showCameraPlaceholder(message + (starting ? '，正在重试' : ''));
            scheduleCameraRecovery(starting ? 1200 : 2500, requestedProtocol);
            return;
        }
        destroyCameraPlayer();
        startCameraWatchdog(video);
        const isHls = /\.m3u8(?:$|\?)/i.test(res.data);
        cameraPreferredProtocol = isHls ? 2 : 4;
        if (isHls) {
            // Chromium can report a non-empty canPlayType result for HLS
            // without actually being able to play an m3u8 URL natively.
            // Prefer hls.js whenever MediaSource is available; reserve the
            // native path for Safari/iOS where it is the reliable option.
            if (typeof Hls !== 'undefined' && Hls.isSupported()) {
                __hlsPlayer = new Hls({
                    enableWorker: true,
                    lowLatencyMode: true,
                    backBufferLength: 10,
                    maxBufferLength: 4,
                    maxMaxBufferLength: 6,
                    liveSyncDurationCount: 1,
                    liveMaxLatencyDurationCount: 3,
                    maxLiveSyncPlaybackRate: 1.2,
                    // Never let a cached playlist keep the player on a stale
                    // sequence after the bridge has restarted.
                    xhrSetup: function(xhr) {
                        xhr.setRequestHeader('Cache-Control', 'no-cache');
                    },
                    manifestLoadingMaxRetry: 3,
                    fragLoadingMaxRetry: 3,
                    fragLoadingRetryDelay: 300,
                    manifestLoadingRetryDelay: 500
                });
                __hlsPlayer.on(Hls.Events.ERROR, function(event, data) {
                    if (!data.fatal) return;
                    if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
                        __hlsPlayer.startLoad();
                        scheduleCameraRecovery(2500);
                    } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
                        __hlsPlayer.recoverMediaError();
                        scheduleCameraRecovery(3500);
                    } else {
                        setCameraStatus('error', 'HLS 视频流播放失败');
                        showCameraPlaceholder('HLS 视频流播放失败，正在自动重连');
                        scheduleCameraRecovery(1500);
                    }
                });
                __hlsPlayer.loadSource(res.data);
                __hlsPlayer.attachMedia(video);
            } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                video.src = res.data;
                playCameraVideo(video);
            } else {
                setCameraStatus('error', '当前浏览器不支持 HLS 播放');
                showCameraPlaceholder('当前浏览器不支持 HLS 播放');
                return;
            }
        } else {
            if (typeof flvjs === 'undefined' || !flvjs.isSupported()) {
                setCameraStatus('error', '当前浏览器不支持 FLV 播放');
                showCameraPlaceholder('当前浏览器不支持 FLV 播放');
                return;
            }
            __flvPlayer = flvjs.createPlayer({
                type: 'flv',
                url: res.data,
                isLive: true,
                cors: true,
                lazyLoad: false,
                // Disable the stash for live control. The stash is useful for
                // VOD, but it adds visible latency to a camera stream.
                enableStashBuffer: false,
                autoCleanupSourceBuffer: true,
                autoCleanupMaxBackwardDuration: 10,
                autoCleanupMinBackwardDuration: 5
            });
            __flvPlayer.on(flvjs.Events.ERROR, function() {
                setCameraStatus('loading', 'FLV 流异常，正在切换备用 HLS');
                showCameraPlaceholder('FLV 流异常，正在切换备用 HLS');
                cameraPreferredProtocol = 2;
                scheduleCameraRecovery(1200, 2);
            });
            __flvPlayer.attachMediaElement(video);
            __flvPlayer.load();
            playCameraVideo(video);
        }
        function markCameraPlaying() {
            patrolVideoReady = true;
            cancelPendingCameraRecovery();
            cameraLastProgressAt = Date.now();
            startLiveLatencyMonitor(video);
            setCameraStatus('ready', '萤石实时画面已连接');
            updateAudioButton();
            var placeholder = document.getElementById('video-placeholder');
            if (placeholder) placeholder.style.display = 'none';
            scheduleRealtimeDetection(100);
        }
        video.onplaying = markCameraPlaying;
        video.onloadeddata = markCameraPlaying;
        video.oncanplay = markCameraPlaying;
        video.onerror = function() {
            patrolVideoReady = false;
            setCameraStatus('error', '视频流播放失败，请检查摄像头编码和网络');
            showCameraPlaceholder('视频流播放失败，正在自动重连');
            scheduleCameraRecovery(1500, cameraPreferredProtocol === 4 ? 2 : cameraPreferredProtocol);
        };
        video.ontimeupdate = function() {
            cameraLastProgressAt = Date.now();
            if (!video.paused && video.readyState >= 2) cancelPendingCameraRecovery();
        };
        video.onstalled = function() {
            setCameraStatus('loading', '视频流卡顿，正在恢复');
        };
        video.onwaiting = function() {
            setCameraStatus('loading', '视频流等待数据，正在恢复');
        };
    } catch (e) {
        console.error('摄像头初始化失败:', e);
        setCameraStatus('error', '摄像头连接请求失败');
        showCameraPlaceholder('摄像头连接请求失败');
        scheduleCameraRecovery(2000, requestedProtocol);
    }
}

function labelForCameraQuality(quality) {
    return quality === 'smooth' ? '480p' : '720p';
}

function applyCameraQualityStatus(data) {
    if (!data) return;
    var select = document.getElementById('camera-quality');
    var status = document.getElementById('camera-quality-status');
    var patrolQuality = document.getElementById('auto-patrol-quality');
    var quality = String(data.quality || (select && select.value) || 'hd').toLowerCase();
    var width = Number(data.actualWidth || 0);
    var height = Number(data.actualHeight || 0);
    var verified = data.qualityVerified === true;
    var dimensions = width > 0 && height > 0 ? width + 'x' + height : '';
    var text;
    var state = 'warning';
    if (quality === '4k') {
        var real4k = verified && width === 3840 && height === 2160;
        text = real4k ? '真实 4K · 3840x2160' : ('4K 未验证' + (dimensions ? ' · ' + dimensions : ''));
        state = real4k ? 'verified' : 'warning';
    } else {
        var label = labelForCameraQuality(quality);
        text = dimensions ? label + ' · ' + dimensions : label + '待校验';
        state = verified ? 'verified' : 'warning';
    }
    if (select && data.quality) {
        select.value = quality;
        select.title = text;
    }
    if (status) {
        status.className = 'camera-quality-status ' + state;
        status.textContent = text;
        status.title = text;
    }
    if (patrolQuality) {
        patrolQuality.textContent = quality === '4k'
            ? (verified && width === 3840 && height === 2160 ? '真实4K' : '4K待验证')
            : (dimensions || labelForCameraQuality(quality));
    }
}

async function loadCameraQuality() {
    const select = document.getElementById('camera-quality');
    if (!select) return;
    const res = await apiGet('/camera/local-status');
    if (res && res.code === 200 && res.data && res.data.quality) applyCameraQualityStatus(res.data);
}

async function changeCameraQuality(quality) {
    const select = document.getElementById('camera-quality');
    if (!select || select.disabled) return;
    const previous = select.dataset.current || 'hd';
    select.disabled = true;
    destroyCameraPlayer();
    setCameraStatus('loading', quality === '4k' ? '正在切换 4K 主码流' : '正在切换视频清晰度');
    showCameraPlaceholder('正在切换清晰度，请稍候');
    const res = await apiPut('/camera/local-quality', { quality: quality });
    if (!res || res.code !== 200) {
        select.value = previous;
        select.disabled = false;
        const message = (res && res.msg) || '清晰度切换失败';
        setCameraStatus('error', message);
        showCameraPlaceholder(message);
        scheduleCameraRecovery(1500, 4);
        return;
    }
    select.dataset.current = quality;
    if (res.data) applyCameraQualityStatus(res.data);
    select.disabled = false;
    initCamera(4);
}

async function changeEncodeType() {
    const btn = document.getElementById('btn-encode');
    btn.disabled = true;
    btn.innerHTML = '<i class="ri-loader-4-line"></i>修改中...';
    setCameraStatus('loading', '正在切换摄像头编码');
    const res = await apiPut('/camera/encode-type', null);
    if (res && res.code === 200) {
        btn.innerHTML = '<i class="ri-check-line"></i>已切换';
        setTimeout(function() {
            btn.innerHTML = '<i class="ri-settings-4-line"></i>H.264 默认';
            btn.disabled = false;
            initCamera();
        }, 3000);
    } else {
        btn.innerHTML = '<i class="ri-close-line"></i>切换失败';
        setCameraStatus('error', (res && res.msg) || '摄像头编码切换失败');
        setTimeout(function() {
            btn.innerHTML = '<i class="ri-settings-4-line"></i>H.264 默认';
            btn.disabled = false;
        }, 3000);
    }
}

window.addEventListener('DOMContentLoaded', function() {
    document.addEventListener('pointerdown', unlockRealtimeWarningSound, { once: true });
    loadPatrolPageAlert();
    window.setInterval(loadPatrolPageAlert, 60000);
    loadCameraQuality().finally(function() {
        var quality = document.getElementById('camera-quality');
        if (quality) quality.dataset.current = quality.value;
        initCamera();
    });
    bindCameraViewportControls();
    setRealtimeDetectionStatus('', '监测已关闭');
    apiGet('/patrol/realtime-detect/status').then(function(response) {
        if (response && response.code === 200 && response.data) {
            realtimeDetectionInterval = Math.max(500, Math.min(5000, Number(response.data.intervalMs || 1000)));
            realtimeDetectionEnabled = response.data.enabled === true;
            var detectButton = document.getElementById('realtime-detect-toggle');
            if (detectButton) {
                detectButton.classList.toggle('active', realtimeDetectionEnabled);
                detectButton.setAttribute('aria-pressed', realtimeDetectionEnabled ? 'true' : 'false');
                detectButton.innerHTML = '<i class="ri-radar-line"></i><span>虫害监测 ' + (realtimeDetectionEnabled ? '开' : '已关闭') + '</span>';
            }
            setRealtimeDetectionStatus(realtimeDetectionEnabled ? 'warming' : '', realtimeDetectionEnabled ? '模型待加载' : '监测已关闭');
        }
        if (realtimeDetectionEnabled) scheduleRealtimeDetection(500);
    });
    loadAutomaticPatrolPlans();
    loadAutomaticPatrolStatus();
    loadPatrolRecords();
    window.setInterval(loadAutomaticPatrolStatus, 1000);
    window.setInterval(loadPatrolRecords, 30000);
    var aiBtn = document.getElementById('btn-ai-capture');
    if (aiBtn) {
        aiBtn.addEventListener('click', triggerAiCapture);
    }
    bindMotorHoldControls();
    bindPtzControls();
    loadControlPanelStatus();
    window.setInterval(loadControlPanelStatus, 15000);
    var warningOverlay = document.getElementById('patrolWarningOverlay');
    if (warningOverlay) {
        warningOverlay.addEventListener('click', function(event) {
            if (event.target === warningOverlay) closePatrolWarning();
        });
    }
    var recordsOverlay = document.getElementById('patrolRecordsOverlay');
    if (recordsOverlay) {
        recordsOverlay.addEventListener('click', function(event) {
            if (event.target === recordsOverlay) closePatrolRecords();
        });
    }
});

document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape') {
        closePatrolWarning();
        closePatrolRecords();
        return;
    }
    if (event.key === '2' && !patrolKeyTargetIsEditable(event.target)) {
        var warning = document.getElementById('patrol-warning');
        if (warning && (!patrolPageAlert || Number(patrolPageAlert.enabled) !== 0)) warning.hidden = false;
    }
});

window.addEventListener('pageshow', function (event) {
    if (event.persisted) initCamera();
});
window.addEventListener('visibilitychange', function() {
    if (!document.hidden && Date.now() - cameraLastProgressAt > 15000) initCamera(cameraPreferredProtocol);
});
window.addEventListener('pagehide', function() { stopPtz(activePtzDirection); destroyCameraPlayer(); releaseVoiceMicrophone(); if (realtimeDetectionTimer) clearTimeout(realtimeDetectionTimer); });
window.addEventListener('beforeunload', function() { stopPtz(activePtzDirection); destroyCameraPlayer(); releaseVoiceMicrophone(); if (realtimeDetectionTimer) clearTimeout(realtimeDetectionTimer); });
