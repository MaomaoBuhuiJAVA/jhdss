(function () {
    const input = document.getElementById('yoloImageInput');
    const uploadButton = document.getElementById('yoloUploadButton');
    const evaluationButton = document.getElementById('yoloEvaluationButton');
    const threshold = document.getElementById('yoloThreshold');
    const thresholdValue = document.getElementById('yoloThresholdValue');
    const status = document.getElementById('yoloStatus');
    const stats = document.getElementById('yoloStats');
    const singleResult = document.getElementById('yoloSingleResult');
    const evaluation = document.getElementById('yoloEvaluation');
    const grid = document.getElementById('yoloEvaluationGrid');
    if (!input || !uploadButton) return;

    let evaluationData = null;
    const contextPath = (location.pathname.match(/^\/[^/]+/) || [''])[0];
    const url = path => /^(https?:|data:|blob:)/.test(path) ? path : contextPath + (path.charAt(0) === '/' ? path : '/' + path);

    threshold.addEventListener('input', () => thresholdValue.textContent = threshold.value);
    uploadButton.addEventListener('click', () => input.click());
    input.addEventListener('change', () => {
        const file = input.files && input.files[0];
        if (file) detect(file);
        input.value = '';
    });
    evaluationButton.addEventListener('click', loadEvaluation);
    document.querySelectorAll('[data-yolo-filter]').forEach(button => button.addEventListener('click', () => {
        document.querySelectorAll('[data-yolo-filter]').forEach(item => item.classList.toggle('active', item === button));
        renderEvaluation(button.dataset.yoloFilter || 'all');
    }));

    async function detect(file) {
        if (!file.type || file.type.indexOf('image/') !== 0) {
            setStatus('请选择图片文件', true);
            return;
        }
        setBusy(true, '正在识别…');
        const body = new FormData();
        body.append('file', file);
        body.append('confidence', threshold.value);
        try {
            const response = await fetch(url('/api/ai-learn/detect'), { method: 'POST', body });
            const json = await response.json();
            if (!response.ok || json.code !== 200) throw new Error(json.msg || '识别失败');
            renderSingle(json.data);
            evaluation.hidden = true;
            setStatus('识别完成');
        } catch (error) {
            setStatus(error.message || '识别失败', true);
        } finally {
            setBusy(false);
        }
    }

    async function loadEvaluation() {
        setBusy(true, '正在加载测试集…');
        try {
            const response = await fetch(url('/api/ai-learn/evaluation'));
            const json = await response.json();
            if (!response.ok || json.code !== 200) throw new Error(json.msg || '测试集结果不可用');
            evaluationData = json.data;
            renderStats(evaluationData);
            renderEvaluation('all');
            evaluation.hidden = false;
            singleResult.hidden = true;
            setStatus('测试集已加载');
        } catch (error) {
            setStatus(error.message || '测试集结果不可用', true);
        } finally {
            setBusy(false);
        }
    }

    function renderSingle(data) {
        const detections = data.detections || [];
        singleResult.innerHTML = `
            <div class="yolo-single-image"><img src="${url(data.image)}" alt="${escapeHtml(data.fileName || '识别结果')}" /></div>
            <div class="yolo-single-meta"><h2>${escapeHtml(data.fileName || '图片识别结果')}</h2>
                <strong class="${detections.length ? 'found' : 'empty'}">${detections.length ? '识别到 ' + detections.length + ' 个目标' : '未识别到目标'}</strong>
                <div class="yolo-detection-list">${detections.map(d => `<span>${displayClass(d.class)} · ${(Number(d.confidence) * 100).toFixed(1)}%</span>`).join('') || '<span>可以尝试降低置信度阈值后重新识别</span>'}</div>
            </div>`;
        singleResult.hidden = false;
    }

    function renderStats(data) {
        stats.innerHTML = [
            stat('测试照片', data.imageCount), stat('识别到目标', data.detectedCount),
            stat('未识别', data.undetectedCount), stat('检测覆盖率', data.detectionRate + '%'),
            stat('目标框总数', data.totalBoxes), stat('平均置信度', data.averageConfidence + '%'),
            stat('验证集 Precision', metric(data.validation, 'precision')), stat('验证集 Recall', metric(data.validation, 'recall')),
            stat('验证集 mAP50', metric(data.validation, 'map50')), stat('验证集 mAP50-95', metric(data.validation, 'map50_95'))
        ].join('');
        stats.hidden = false;
    }

    function renderEvaluation(filter) {
        if (!evaluationData) return;
        const images = (evaluationData.images || []).filter(item => filter === 'all' || (filter === 'detected' ? item.detected : !item.detected));
        grid.innerHTML = images.map(item => `
            <article class="yolo-eval-card ${item.detected ? 'is-detected' : 'is-empty'}">
                <a href="${url(item.image)}" target="_blank" rel="noreferrer"><img loading="lazy" src="${url(item.image)}" alt="${escapeHtml(item.name)}"></a>
                <div class="yolo-eval-caption"><span>${escapeHtml(item.name)}</span><b>${item.detected ? '识别到 ' + item.count + ' 个' : '未识别'}</b></div>
            </article>`).join('');
    }

    function stat(label, value) { return `<div><b>${escapeHtml(value)}</b><span>${label}</span></div>`; }
    function metric(metrics, key) { return metrics && metrics[key] != null ? metrics[key] + '%' : '-'; }
    function displayClass(value) { return value === 'longhorn_beetle' ? '黑天牛' : value; }
    function escapeHtml(value) { return String(value == null ? '' : value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;'); }
    function setStatus(text, error) { status.textContent = text; status.classList.toggle('error', !!error); }
    function setBusy(busy, text) { uploadButton.disabled = busy; evaluationButton.disabled = busy; if (busy) setStatus(text); }
})();
