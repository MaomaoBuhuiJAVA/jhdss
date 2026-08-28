/**
 * AI 学习页交互
 * 流程：idle → selected(file picked) → scanning(progress) → done(api fetch)
 * 仅有最后一步会真实请求 /api/ai-learn/analyze 接口，其它都是样式/前端抽帧。
 */
(function () {
    const STATE = { IDLE: 'idle', SELECTED: 'selected', SCANNING: 'scanning', DONE: 'done' };
    let currentState = STATE.IDLE;
    let selectedVideoKey = null;
    let selectedVideoName = null;

    const $ = (sel, root) => (root || document).querySelector(sel);
    const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

    const fileInput   = $('#videoFileInput');
    const scanBtn     = $('#scanBtn');
    const retryBtn    = $('#retryBtn');
    const coverImg    = $('#coverImg');
    const coverName   = $('#coverName');
    const scanBg      = $('#scanBg');
    const scanPercent = $('#scanPercent');
    const scanStageText = $('#scanStageText');
    const scanBarFill = $('#scanBarFill');
    const resultGrid  = $('#resultGrid');
    const resultFileName = $('#resultFileName');

    const stages = {
        idle:     $('.stage-idle'),
        selected: $('.stage-selected'),
        scanning: $('.stage-scanning'),
        done:     $('.stage-done'),
    };

    // 阶段文案，按百分比切换（纯前端样式，不体现后端 AI 能力）
    const SCAN_STAGES = [
        { from: 0,  text: '正在初始化…' },
        { from: 22, text: '正在解析视频…' },
        { from: 46, text: '正在分析画面…' },
        { from: 70, text: '正在整理分析结果…' },
        { from: 90, text: '正在生成学习卡片…' },
        { from: 98, text: '分析完成' }
    ];

    /**
     * 图片路径解析：
     * 后端返回的是以 /images/ 开头的绝对路径，但项目有 context-path（如 /jhds），
     * 直接当绝对路径用会绕过 context path 导致 404。
     * 这里根据当前页面 URL 自动拼上 context path；本地 preview 环境单独映射到源码目录。
     */
    function resolveImg(p) {
        if (!p) return '';
        if (/^(https?:|data:|blob:)/.test(p)) return p; // 完整 URL 原样返回
        const pname = location.pathname;
        // 本地 preview（python http.server 服务项目根目录）
        if (pname.indexOf('/preview/') === 0) {
            return '/src/main/resources' + p.replace(/^\/images\//, '/image/temp/');
        }
        // 真实环境：取 /ai-learn 之前的路径段作为 context path（/jhds/ai-learn → /jhds）
        const idx = pname.indexOf('/ai-learn');
        const ctx = idx > 0 ? pname.substring(0, idx) : '';
        return ctx + p;
    }

    function showState(state) {
        currentState = state;
        Object.entries(stages).forEach(([key, el]) => {
            if (!el) return;
            el.hidden = key !== state;
        });
        // 居中容器在 idle/selected 时显示，scanning/done 时隐藏
        const wrap = $('.ai-learn-wrap');
        if (wrap) wrap.style.display = (state === STATE.IDLE || state === STATE.SELECTED) ? 'flex' : 'none';
    }

    /* ---------------- 文件选择 ---------------- */
    $$('[data-trigger="file"]').forEach(btn => {
        btn.addEventListener('click', e => {
            e.preventDefault();
            fileInput.value = '';
            fileInput.click();
        });
    });

    fileInput.addEventListener('change', e => {
        const file = e.target.files && e.target.files[0];
        if (!file) return;
        handleVideoFile(file);
        e.target.value = '';
    });

    /* ---------------- 拖拽上传 ---------------- */
    const dropTargets = $$('.ai-learn-wrap, .ai-learn-upload, .ai-learn-tip');
    let dragDepth = 0; // 防止子元素 dragenter/dragleave 闪烁

    function preventDefault(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    dropTargets.forEach(el => {
        el.addEventListener('dragenter', e => {
            preventDefault(e);
            dragDepth++;
            document.querySelector('.ai-learn-card').classList.add('drag-over');
        });
        el.addEventListener('dragover', preventDefault);
        el.addEventListener('dragleave', e => {
            preventDefault(e);
            dragDepth = Math.max(0, dragDepth - 1);
            if (dragDepth === 0) {
                document.querySelector('.ai-learn-card').classList.remove('drag-over');
            }
        });
        el.addEventListener('drop', e => {
            preventDefault(e);
            dragDepth = 0;
            document.querySelector('.ai-learn-card').classList.remove('drag-over');
            const file = e.dataTransfer.files && e.dataTransfer.files[0];
            if (file) handleVideoFile(file);
        });
    });

    /**
     * 统一处理选中的视频文件（选择按钮 / 拖拽 / 更换视频共用）
     */
    async function handleVideoFile(file) {
        // type 为空时放行尝试（某些系统拖拽文件 type 为空），非视频才拒绝
        if (file.type && file.type.indexOf('video/') !== 0) {
            alert('请选择视频文件（mp4 / webm / mov 等）');
            return;
        }
        const fileMatch = file.name.trim().match(/^视频([123])\.mp4$/i);
        selectedVideoKey = fileMatch ? fileMatch[1] : null;
        selectedVideoName = file.name.trim();
        let thumbDataUrl = selectedVideoKey
            ? resolveImg('/images/demo/' + selectedVideoKey + '-1.png')
            : '';
        try {
            if (file.size === 0) throw new Error('empty video file');
            thumbDataUrl = await extractFirstFrame(file);
        } catch (err) {
            if (!selectedVideoKey) {
                console.error('抽帧失败', err);
                alert('该视频文件无法读取或格式不受支持，请选择有效的 mp4 文件');
                return;
            }
            console.warn('视频封面不可用，已使用对应资料图片', err);
        }
        coverImg.src = thumbDataUrl;
        scanBg.src = thumbDataUrl;
        coverName.textContent = file.name;
        resultFileName.textContent = file.name;
        scanBtn.disabled = false;
        $$('.ai-learn-btn.primary').forEach(b => b.classList.add('active'));
        showState(STATE.SELECTED);
    }

    /**
     * 抽出视频第一帧作为封面（dataURL）。
     * 走纯前端，不请求后端。
     */
    function extractFirstFrame(file) {
        return new Promise((resolve, reject) => {
            const url = URL.createObjectURL(file);
            const video = document.createElement('video');
            video.preload = 'metadata';
            video.muted = true;
            video.playsInline = true;
            video.src = url;
            let seeked = false;

            const cleanup = () => { URL.revokeObjectURL(url); video.removeAttribute('src'); video.load(); };

            video.addEventListener('loadeddata', () => {
                try { video.currentTime = Math.min(0.1, video.duration / 2 || 0.1); }
                catch (e) { /* ignore */ }
            });

            video.addEventListener('seeked', () => {
                if (seeked) return;
                seeked = true;
                try {
                    const w = video.videoWidth || 640;
                    const h = video.videoHeight || 360;
                    const canvas = document.createElement('canvas');
                    canvas.width = w; canvas.height = h;
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(video, 0, 0, w, h);
                    const dataUrl = canvas.toDataURL('image/jpeg', 0.82);
                    cleanup();
                    resolve(dataUrl);
                } catch (e) {
                    cleanup();
                    reject(e);
                }
            });

            video.addEventListener('error', () => { cleanup(); reject(new Error('video error')); });

            // 超时兜底
            setTimeout(() => {
                if (!seeked) {
                    try {
                        const w = video.videoWidth || 640;
                        const h = video.videoHeight || 360;
                        const canvas = document.createElement('canvas');
                        canvas.width = w; canvas.height = h;
                        const ctx = canvas.getContext('2d');
                        try { ctx.drawImage(video, 0, 0, w, h); } catch (e) {}
                        const dataUrl = canvas.toDataURL('image/jpeg', 0.82);
                        cleanup();
                        resolve(dataUrl);
                    } catch (e) { cleanup(); reject(e); }
                }
            }, 4000);
        });
    }

    /* ---------------- 扫描 ---------------- */
    scanBtn.addEventListener('click', async () => {
        if (currentState !== STATE.SELECTED) return;
        showState(STATE.SCANNING);

        // 进度 0 → 100，约 3.4 秒
        const total = 3400;
        const start = performance.now();
        let pct = 0;
        await new Promise(resolve => {
            const tick = () => {
                const elapsed = performance.now() - start;
                pct = Math.min(100, Math.round((elapsed / total) * 100));
                updateProgress(pct);
                if (pct >= 100) { resolve(); return; }
                requestAnimationFrame(tick);
            };
            tick();
        });

        try {
            const groups = await fetchResult(selectedVideoName);
            if (!groups || !groups.length) {
                throw new Error('未找到该视频对应的分析资料');
            }
            renderCards(groups);
            showState(STATE.DONE);
        } catch (error) {
            console.error('加载视频资料失败', error);
            alert('未找到该视频对应的分析资料，请选择已配置的视频文件');
            showState(STATE.SELECTED);
        }
    });

    function updateProgress(pct) {
        scanPercent.textContent = pct + '%';
        scanBarFill.style.width = pct + '%';
        // 阶段文案
        let stageText = SCAN_STAGES[0].text;
        for (const s of SCAN_STAGES) {
            if (pct >= s.from) stageText = s.text;
        }
        scanStageText.textContent = stageText;
    }

    async function fetchResult(videoName) {
        const query = videoName ? '?videoName=' + encodeURIComponent(videoName) : '';
        const resp = await fetch('api/ai-learn/analyze' + query, { method: 'GET' });
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const json = await resp.json();
        if (json && json.code === 200 && Array.isArray(json.data)) return json.data;
        throw new Error('bad payload');
    }

    function getVideoResult(key) {
        const datasets = {
            '1': [{
                group: '樱桃苗木培育与定植',
                items: [
                    { image: '/images/demo/1-1.png', title: '苗木筛选与质量把控', desc: '对照标准测量苗高（≥0.8m），检查主干粗细均匀度、侧根数量与健壮程度，剔除偏细、弯曲、根系稀疏或发黑的苗木。' },
                    { image: '/images/demo/1-2.png', title: '接穗处理与嫁接操作', desc: '选取健壮嫩梢削取带少量木质部的盾形芽片；砧木斜切形成嵌合切口，将芽片嵌入并对齐形成层，用嫁接膜密封固定；另有劈接方式：砧木劈开2–3cm，接穗削成楔形插入并对准形成层后固定。' },
                    { image: '/images/demo/1-3.png', title: '根系修剪与无土栽培准备', desc: '剪除细弱须根和交叉缠绕根，保留健壮主根与侧根，将剪口修成45°斜面；依次用多菌灵浸泡30分钟杀菌、生根粉溶液促根；同时配制均匀无分层的混合基质。' },
                    { image: '/images/demo/1-4.png', title: '定植、定干与促枝处理', desc: '种植袋底部铺珍珠岩，苗木根系自然舒展后填充基质并压实，确保根颈露出基质±1cm；在主干70cm处定干，选芽刻伤并涂抹发枝素促枝；插入竹竿固定苗木，浇透定根水（分两次浇灌）。' }
                ]
            }],
            '2': [{
                group: '缺素诊断与仪器操作',
                items: [
                    { image: '/images/demo/2-1.png', title: '叶色诊断与症状记录', desc: '叶色诊断法：通过叶片颜色变化初步判断缺素类型；叶片褪绿、叶脉间呈现褪绿条纹时，初步判断为缺镁。\n\n症状记录与数据更新：发现异常症状时及时拍照记录，并上传至AI数据库，用于后续识别模型训练。' },
                    { image: '/images/demo/2-2.png', title: '光合作用与土壤测定', desc: '快捷光合作用速率仪：掌握仪器使用方法并测定植株光合速率，该指标可用于判断植物生理状态。\n\n土壤测定仪：测定土壤速效氮、磷、钾及中微量元素；Mg含量低于0.49%即为缺镁，并准确记录各项测定数值。' },
                    { image: '/images/demo/2-3.png', title: '环境判断与缺素矫正', desc: '环境参数异常判断：掌握樱桃适宜生长环境参数范围；CO₂浓度180ppm过低时进行通风处理。\n\n缺素矫正方案制定：根据诊断结果制定针对性施肥方案；缺镁时配制0.3%镁肥进行叶面喷施。' }
                ]
            }],
            '3': [{
                group: '病虫害预警与防治',
                items: [
                    { image: '/images/demo/3-1.png', title: 'AI巡检全域拍摄采集', desc: '基于温室内温度、湿度等环境参数，利用系统模型预测病虫害风险；操作AI摄像头搭载高光谱摄像头规划巡检航线，采集图像并生成报告，提取病虫害种类、位置、严重程度等信息。' },
                    { image: '/images/demo/3-2.png', title: '人工采样与实验室诊断', desc: '当AI无法精准识别时，启动人工采样：在病叶健康交界处刮取病灶，通过载玻片制片后使用光学显微镜观察病原形态，完成确诊。' },
                    { image: '/images/demo/3-3.png', title: '药剂配制与精准施药', desc: '根据诊断结果选择对症药剂，利用植保无人机进行精准变量施药；同时掌握蜂卡悬挂技术，辅助生物防治。' },
                    { image: '/images/demo/3-4.png', title: '标本制作与资源库建设', desc: '采用针插法制作害虫标本，使用标准扎网框固定；采集病叶制作病害标本。标本用于培训、科普、科研及AI模型训练，丰富教学与识别资源。' }
                ]
            }]
        };
        return datasets[key] || [];
    }

    /**
     * 前端 fallback：preview 服务器或断网时仍能跑通
     */
    function getMockData() {
        return [
            {
                group: '樱桃苗木培育与定植',
                items: [
                    { image: '/images/1-1.png', title: '1-1 苗木筛选与质量把控', desc: '对照标准测量苗高（≥0.8m），检查主干粗细均匀度、侧根数量与健壮程度，剔除偏细、弯曲、根系稀疏或发黑的苗木。' },
                    { image: '/images/1-2.png', title: '1-2 接穗处理与嫁接操作', desc: '选取健壮嫩梢削取带少量木质部的盾形芽片；砧木斜切形成嵌合切口，将芽片嵌入并对齐形成层，用嫁接膜密封固定；另有劈接方式：砧木劈开2–3cm，接穗削成楔形插入并对准形成层后固定。' },
                    { image: '/images/1-3.png', title: '1-3 根系修剪与无土栽培准备', desc: '剪除细弱须根和交叉缠绕根，保留健壮主根与侧根，将剪口修成45°斜面；依次用多菌灵浸泡30分钟杀菌、生根粉溶液促根；同时配制均匀无分层的混合基质。' },
                    { image: '/images/1-4.png', title: '1-4 定植、定干与促枝处理', desc: '种植袋底部铺珍珠岩，苗木根系自然舒展后填充基质并压实，确保根颈露出基质±1cm；在主干70cm处定干，选芽刻伤并涂抹发枝素促枝；插入竹竿固定苗木，浇透定根水（分两次浇灌）。' }
                ]
            },
            {
                group: '缺素诊断与仪器操作',
                items: [
                    { image: '/images/2-1.png', title: '2-1 叶色诊断法', desc: '学习要点：通过叶片颜色变化初步判断缺素类型\n案例应用：叶片褪绿、叶脉间呈现褪绿条纹 → 初步判断为缺镁' },
                    { image: '/images/2-2.png', title: '2-2 症状记录与数据更新', desc: '学习要点：发现异常症状时及时拍照记录，并上传至AI数据库，用于后续识别模型训练' },
                    { image: '/images/2-3.png', title: '2-3 快捷光合作用速率仪', desc: '学习要点：掌握仪器的使用方法，测定植株的光合速率\n数据意义：光合速率是判断植物生理状态的重要指标\n\n土壤测定仪：掌握土壤速效氮、磷、钾及中微量元素（Ca、Mg、Fe等）的测定方法；Mg含量低于0.49%即为缺镁；准确记录各项测定数值，作为诊断依据\n\n环境参数异常判断：掌握樱桃适宜生长环境参数范围；CO₂浓度180ppm过低 → 通风处理\n\n缺素矫正方案制定：根据诊断结果制定针对性施肥方案；缺镁 → 配制0.3%镁肥进行叶面喷施' }
                ]
            },
            {
                group: '病虫害预警与防治',
                items: [
                    { image: '/images/3-1.png', title: '3-1 环境预警与无人机巡检', desc: '基于温室内温度、湿度等环境参数，利用系统模型预测病虫害风险（如红色预警为高风险）；操作无人机搭载高光谱摄像头规划巡检航线，采集图像并生成报告，提取病虫害种类、位置、严重程度等信息。' },
                    { image: '/images/3-2.png', title: '3-2 人工采样与实验室诊断', desc: '当AI无法精准识别时，启动人工采样：在病叶健康交界处刮取病灶（病原物最集中），通过载玻片制片（滴无菌水、加盖玻片排除气泡）后，使用光学显微镜观察病原形态（如卵形孢子判定为灰霉病），完成确诊。' },
                    { image: '/images/3-3.png', title: '3-3 药剂配制与精准施药', desc: '根据诊断结果选择对症药剂（如灰霉病用50%湿霉利可湿性粉剂1500倍液），利用植保无人机进行精准变量施药；同时掌握蜂卡悬挂技术（位置、高度、密度），辅助生物防治。' },
                    { image: '/images/3-4.png', title: '3-4 标本制作与资源库建设', desc: '采用针插法制作害虫标本，使用标准扎网框固定；采集病叶制作病害标本（处理、保存）。标本用于培训、科普、科研及AI模型训练，丰富教学与识别资源。' }
                ]
            }
        ];
    }

    function renderCards(groups) {
        resultGrid.innerHTML = '';
        (groups || []).forEach(g => {
            // 组标题
            if (g.group) {
                const h = document.createElement('div');
                h.className = 'ai-result-group-title';
                h.innerHTML = `<i class="ri-bookmark-3-line"></i><span>${escapeHtml(g.group)}</span>`;
                resultGrid.appendChild(h);
            }
            // 该组卡片
            (g.items || []).forEach(it => {
                const card = document.createElement('div');
                card.className = 'ai-card';
                card.innerHTML = `
                    <img class="ai-card-img" src="${resolveImg(it.image)}" alt="${escapeHtml(it.title)}"
                         onerror="this.style.background='linear-gradient(135deg,#103040,#1a3850)'">
                    <div class="ai-card-body">
                        <div class="ai-card-title">${escapeHtml(it.title)}</div>
                        <div class="ai-card-desc">${nl2br(escapeHtml(it.desc))}</div>
                    </div>
                `;
                resultGrid.appendChild(card);
            });
        });
    }

    // 换行 → <br>
    function nl2br(s) {
        return String(s == null ? '' : s).replace(/\n/g, '<br>');
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    /* ---------------- 重新分析 ---------------- */
    retryBtn.addEventListener('click', () => {
        // 回到 selected 状态，但保留文件
        scanBarFill.style.width = '0%';
        scanPercent.textContent = '0%';
        showState(STATE.SELECTED);
    });

    // 初始
    showState(STATE.IDLE);
})();
