(function() {
    'use strict';

    var modal = document.getElementById('aiVideoModal');
    var trigger = document.getElementById('aiVideoUpload');
    if (!modal || !trigger) return;

    var fileInput = document.getElementById('aiVideoFileInput');
    var dropzone = document.getElementById('aiVideoDropzone');
    var cover = document.getElementById('aiVideoCover');
    var scanImage = document.getElementById('aiVideoScanImage');
    var fileName = document.getElementById('aiVideoFileName');
    var scanButton = document.getElementById('aiVideoScanBtn');
    var retryButton = document.getElementById('aiVideoRetryBtn');
    var percent = document.getElementById('aiVideoScanPercent');
    var scanText = document.getElementById('aiVideoScanText');
    var progressBar = document.getElementById('aiVideoProgressBar');
    var resultGrid = document.getElementById('aiVideoResultGrid');
    var stages = Array.prototype.slice.call(modal.querySelectorAll('[data-video-stage]'));
    var currentState = 'idle';
    var selectedFile = null;
    var lastFocused = null;
    var scanToken = 0;
    var closeTimer = null;
    var openFrame = null;

    function contextPath() {
        var marker = '/ai';
        var index = window.location.pathname.indexOf(marker);
        return index > 0 ? window.location.pathname.substring(0, index) : '';
    }

    function appUrl(path) {
        if (/^(?:https?:|data:|blob:)/.test(path || '')) return path;
        var base = contextPath();
        if (base && path.indexOf(base + '/') === 0) return path;
        return base + (path.charAt(0) === '/' ? path : '/' + path);
    }

    function showState(state) {
        currentState = state;
        stages.forEach(function(stage) {
            stage.hidden = stage.getAttribute('data-video-stage') !== state;
        });
        var body = document.getElementById('aiVideoDialogBody');
        if (body) body.scrollTop = 0;
    }

    function openModal() {
        if (closeTimer) {
            window.clearTimeout(closeTimer);
            closeTimer = null;
        }
        if (openFrame) window.cancelAnimationFrame(openFrame);
        lastFocused = document.activeElement;
        modal.hidden = false;
        modal.classList.remove('is-open');
        document.body.classList.add('ai-video-open');
        openFrame = window.requestAnimationFrame(function() {
            openFrame = window.requestAnimationFrame(function() {
                modal.classList.add('is-open');
                openFrame = null;
                var close = modal.querySelector('.ai-video-close');
                if (close) close.focus({ preventScroll: true });
            });
        });
    }

    function closeModal() {
        if (openFrame) {
            window.cancelAnimationFrame(openFrame);
            openFrame = null;
        }
        scanToken += 1;
        if (currentState === 'scanning') {
            updateScan(0);
            showState(selectedFile ? 'selected' : 'idle');
        }
        modal.classList.remove('is-open');
        document.body.classList.remove('ai-video-open');
        closeTimer = window.setTimeout(function() {
            modal.hidden = true;
            closeTimer = null;
            if (lastFocused && lastFocused.focus) lastFocused.focus();
        }, 380);
    }

    function pickVideo() {
        fileInput.value = '';
        fileInput.click();
    }

    function extractFrame(file) {
        return new Promise(function(resolve, reject) {
            var url = URL.createObjectURL(file);
            var video = document.createElement('video');
            var settled = false;

            function cleanup() {
                URL.revokeObjectURL(url);
                video.removeAttribute('src');
                video.load();
            }

            function finish() {
                if (settled) return;
                settled = true;
                try {
                    var canvas = document.createElement('canvas');
                    canvas.width = video.videoWidth || 960;
                    canvas.height = video.videoHeight || 540;
                    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
                    var image = canvas.toDataURL('image/jpeg', .84);
                    cleanup();
                    resolve(image);
                } catch (error) {
                    cleanup();
                    reject(error);
                }
            }

            video.preload = 'metadata';
            video.muted = true;
            video.playsInline = true;
            video.addEventListener('loadeddata', function() {
                try {
                    video.currentTime = Math.min(.25, Math.max(0, video.duration / 3));
                } catch (error) {
                    finish();
                }
            });
            video.addEventListener('seeked', finish);
            video.addEventListener('error', function() {
                if (settled) return;
                settled = true;
                cleanup();
                reject(new Error('video format is not supported'));
            });
            video.src = url;
            window.setTimeout(finish, 4500);
        });
    }

    function showInlineError(message) {
        var existing = modal.querySelector('.ai-video-inline-error');
        if (!existing) {
            existing = document.createElement('span');
            existing.className = 'ai-video-inline-error';
            dropzone.appendChild(existing);
        }
        existing.textContent = message;
    }

    function handleFile(file) {
        if (!file) return;
        if (file.type && file.type.indexOf('video/') !== 0) {
            showInlineError('请选择 MP4、MOV 或 WebM 视频文件');
            return;
        }
        selectedFile = file;
        var demoMatch = file.name.trim().match(/^视频([123])\.mp4$/i);
        var demoCover = demoMatch ? appUrl('/images/demo/' + demoMatch[1] + '-1.png') : '';

        function applySelected(image) {
            cover.src = image;
            scanImage.src = image;
            fileName.textContent = file.name;
            showState('selected');
        }

        if (file.size === 0 && demoCover) {
            applySelected(demoCover);
            return;
        }
        extractFrame(file).then(function(image) {
            applySelected(image);
        }).catch(function() {
            if (demoCover) {
                applySelected(demoCover);
                return;
            }
            showInlineError('无法读取该视频，请更换常用格式的视频文件');
            showState('idle');
        });
    }

    function updateScan(value) {
        var phases = [
            { at: 0, text: '正在初始化识别任务' },
            { at: 18, text: '正在抽取视频关键帧' },
            { at: 40, text: '正在提取叶片与病斑特征' },
            { at: 64, text: '正在检索历史识别资料' },
            { at: 84, text: '正在生成分析知识卡片' },
            { at: 98, text: '识别完成' }
        ];
        var label = phases[0].text;
        phases.forEach(function(phase) {
            if (value >= phase.at) label = phase.text;
        });
        percent.textContent = value + '%';
        scanText.textContent = label;
        progressBar.style.width = value + '%';
    }

    function fetchResults(name) {
        return fetch(appUrl('/api/ai-learn/analyze') + '?videoName=' + encodeURIComponent(name), { method: 'GET' })
            .then(function(response) {
                if (!response.ok) throw new Error('HTTP ' + response.status);
                return response.json();
            })
            .then(function(payload) {
                return payload && payload.code === 200 && Array.isArray(payload.data) ? payload.data : [];
            });
    }

    function renderResults(groups) {
        resultGrid.innerHTML = '';
        if (!groups.length) {
            var empty = document.createElement('div');
            empty.className = 'ai-video-empty';
            empty.textContent = '数据库中暂未配置该视频的识别资料，请使用已录入的视频文件。';
            resultGrid.appendChild(empty);
            return;
        }
        groups.forEach(function(group) {
            if (group.group) {
                var title = document.createElement('div');
                title.className = 'ai-video-result-group';
                title.textContent = group.group;
                resultGrid.appendChild(title);
            }
            (group.items || []).forEach(function(item) {
                var card = document.createElement('article');
                card.className = 'ai-video-result-card';
                var image = document.createElement('img');
                image.src = appUrl(item.image || '');
                image.alt = item.title || '识别画面';
                var copy = document.createElement('div');
                copy.className = 'ai-video-result-copy';
                var heading = document.createElement('strong');
                heading.textContent = item.title || '识别结果';
                var description = document.createElement('p');
                description.textContent = item.desc || '';
                copy.appendChild(heading);
                copy.appendChild(description);
                card.appendChild(image);
                card.appendChild(copy);
                resultGrid.appendChild(card);
            });
        });
    }

    function startScan() {
        if (!selectedFile || currentState !== 'selected') return;
        var token = ++scanToken;
        var start = performance.now();
        var duration = 4600;
        showState('scanning');
        updateScan(0);

        function frame(now) {
            if (token !== scanToken) return;
            var value = Math.min(100, Math.round((now - start) / duration * 100));
            updateScan(value);
            if (value < 100) {
                window.requestAnimationFrame(frame);
                return;
            }
            fetchResults(selectedFile.name).then(function(groups) {
                if (token !== scanToken) return;
                renderResults(groups);
                showState('done');
            }).catch(function() {
                if (token !== scanToken) return;
                renderResults([]);
                showState('done');
            });
        }
        window.requestAnimationFrame(frame);
    }

    trigger.addEventListener('click', openModal);
    modal.querySelectorAll('[data-video-close]').forEach(function(button) {
        button.addEventListener('click', closeModal);
    });
    modal.querySelectorAll('[data-video-pick]').forEach(function(button) {
        button.addEventListener('click', pickVideo);
    });
    dropzone.addEventListener('click', pickVideo);
    fileInput.addEventListener('change', function(event) {
        handleFile(event.target.files && event.target.files[0]);
    });
    ['dragenter', 'dragover'].forEach(function(type) {
        dropzone.addEventListener(type, function(event) {
            event.preventDefault();
            dropzone.classList.add('is-dragging');
        });
    });
    ['dragleave', 'drop'].forEach(function(type) {
        dropzone.addEventListener(type, function(event) {
            event.preventDefault();
            dropzone.classList.remove('is-dragging');
        });
    });
    dropzone.addEventListener('drop', function(event) {
        handleFile(event.dataTransfer.files && event.dataTransfer.files[0]);
    });
    scanButton.addEventListener('click', startScan);
    retryButton.addEventListener('click', function() {
        updateScan(0);
        showState(selectedFile ? 'selected' : 'idle');
    });
    document.addEventListener('keydown', function(event) {
        if (event.key === 'Escape' && !modal.hidden) closeModal();
    });

    showState('idle');
})();
