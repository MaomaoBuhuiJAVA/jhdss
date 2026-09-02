let currentDir = 'stop';
let isAiCapturing = false;
let patrolPageAlert = null;

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

function patrolKeyTargetIsEditable(target) {
    return target && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName));
}

async function setPatrolDir(dir) {
    currentDir = dir;
    document.querySelectorAll('.ctrl-btn').forEach(btn => btn.classList.remove('active'));
    if (dir !== 'stop') document.getElementById('btn-' + dir).classList.add('active');
    await apiPost('/patrol/control', { dir: dir });
}

async function addPatrolTask() {
    const name = document.getElementById('task-name').value;
    const time = document.getElementById('task-time').value;
    const range = document.getElementById('task-range').value;
    if (!name || !time) { alert('请填写完整任务信息'); return; }
    await apiPost('/patrol/task', {
        taskName: name,
        executeTime: time + ':00',
        patrolRange: range
    });
    loadPatrolTasks();
    document.getElementById('task-name').value = '';
}

async function loadPatrolTasks() {
    const res = await apiGet('/patrol/tasks');
    if (!res || !res.data) return;
    const container = document.getElementById('patrol-tasks');
    if (!container) return;
    container.innerHTML = '';
    if (!res.data.length) {
        container.innerHTML = '<div class="empty-state">暂无巡检任务</div>';
        return;
    }
    res.data.forEach(task => {
        const statusMap = {0:'○ 待执行',1:'● 执行中',2:'● 已完成',3:'○ 已停用'};
        const statusClass = {0:'pending',1:'running',2:'completed',3:'pending'};
        const div = document.createElement('div');
        div.className = 'task-item';
        div.innerHTML = '<div class="task-time">' + escapeHtml(String(task.executeTime || '').slice(0, 5)) + '</div>' +
            '<div class="task-info"><div class="task-name">' + escapeHtml(task.taskName || '') + '</div><div class="task-status ' + statusClass[task.status] + '">' + statusMap[task.status] + '</div></div>' +
            '<div class="task-action" onclick="deletePatrolTask(' + task.id + ')"><i class="ri-delete-bin-line"></i></div>';
        container.appendChild(div);
    });
}

async function deletePatrolTask(id) {
    await apiDelete('/patrol/task/' + id);
    loadPatrolTasks();
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
        video.pause();
        video.removeAttribute('src');
        video.load();
    }
    patrolVideoReady = false;
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

async function initCamera() {
    const video = document.getElementById('video-player');
    if (!video) return;

    destroyCameraPlayer();
    setCameraStatus('checking', '正在检查萤石连接');
    showCameraPlaceholder('正在检查萤石摄像头连接');

    try {
        const diagnostic = await apiGet('/camera/stream-check');
        const status = diagnostic && diagnostic.data;
        if (!diagnostic || diagnostic.code !== 200 || !status || !status.ready) {
            const message = (status && status.message) || (diagnostic && diagnostic.msg) || '无法连接萤石摄像头';
            setCameraStatus('error', message);
            showCameraPlaceholder(message);
            return;
        }

        setCameraStatus('loading', '正在加载实时画面');
        const res = await apiGet('/camera/play-url');
        if (!res || !res.data) {
            const message = (res && res.msg) || '未获取到萤石播放地址';
            setCameraStatus('error', message);
            showCameraPlaceholder(message);
            return;
        }
        destroyCameraPlayer();
        const isHls = /\.m3u8(?:$|\?)/i.test(res.data);
        if (isHls) {
            if (video.canPlayType('application/vnd.apple.mpegurl')) {
                video.src = res.data;
                video.play();
            } else if (typeof Hls !== 'undefined' && Hls.isSupported()) {
                __hlsPlayer = new Hls({ enableWorker: true, lowLatencyMode: true });
                __hlsPlayer.on(Hls.Events.ERROR, function(event, data) {
                    if (!data.fatal) return;
                    if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
                        __hlsPlayer.startLoad();
                    } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
                        __hlsPlayer.recoverMediaError();
                    } else {
                        setCameraStatus('error', 'HLS 视频流播放失败');
                        showCameraPlaceholder('HLS 视频流播放失败，请刷新页面重试');
                    }
                });
                __hlsPlayer.loadSource(res.data);
                __hlsPlayer.attachMedia(video);
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
            __flvPlayer = flvjs.createPlayer({ type: 'flv', url: res.data });
            __flvPlayer.attachMediaElement(video);
            __flvPlayer.load();
            __flvPlayer.play();
        }
        video.onplaying = function() {
            patrolVideoReady = true;
            setCameraStatus('ready', '萤石实时画面已连接');
            var placeholder = document.getElementById('video-placeholder');
            if (placeholder) placeholder.style.display = 'none';
        };
        video.onerror = function() {
            patrolVideoReady = false;
            setCameraStatus('error', '视频流播放失败，请检查摄像头编码和网络');
            showCameraPlaceholder('视频流播放失败，请尝试设为 H.264 后刷新');
        };
    } catch (e) {
        console.error('摄像头初始化失败:', e);
        setCameraStatus('error', '摄像头连接请求失败');
        showCameraPlaceholder('摄像头连接请求失败');
    }
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
            btn.innerHTML = '<i class="ri-settings-4-line"></i>设为H.264';
            btn.disabled = false;
            initCamera();
        }, 3000);
    } else {
        btn.innerHTML = '<i class="ri-close-line"></i>切换失败';
        setCameraStatus('error', (res && res.msg) || '摄像头编码切换失败');
        setTimeout(function() {
            btn.innerHTML = '<i class="ri-settings-4-line"></i>设为H.264';
            btn.disabled = false;
        }, 3000);
    }
}

window.addEventListener('DOMContentLoaded', function() {
    loadPatrolPageAlert();
    window.setInterval(loadPatrolPageAlert, 60000);
    initCamera();
    loadPatrolTasks();
    loadPatrolRecords();
    window.setInterval(loadPatrolTasks, 30000);
    window.setInterval(loadPatrolRecords, 30000);
    var aiBtn = document.getElementById('btn-ai-capture');
    if (aiBtn) {
        aiBtn.addEventListener('click', triggerAiCapture);
    }
    var warningOverlay = document.getElementById('patrolWarningOverlay');
    if (warningOverlay) {
        warningOverlay.addEventListener('click', function(event) {
            if (event.target === warningOverlay) closePatrolWarning();
        });
    }
});

document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape') {
        closePatrolWarning();
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
window.addEventListener('pagehide', destroyCameraPlayer);
window.addEventListener('beforeunload', destroyCameraPlayer);
