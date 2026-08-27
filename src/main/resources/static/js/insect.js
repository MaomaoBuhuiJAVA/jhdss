let currentInsectDate = '';
let deviceList = [];
let selectedDevice = '';
let controlParamsMap = {};

function openInsectAlert() {
    const overlay = document.getElementById('insect-alert-overlay');
    if (!overlay) return;
    overlay.hidden = false;
    document.body.style.overflow = 'hidden';
    const closeButton = overlay.querySelector('.insect-alert-close');
    if (closeButton) closeButton.focus();
}

function closeInsectAlert() {
    const overlay = document.getElementById('insect-alert-overlay');
    if (!overlay) return;
    overlay.hidden = true;
    document.body.style.overflow = '';
    const alertButton = document.getElementById('insect-alert');
    if (alertButton) alertButton.focus();
}

document.addEventListener('DOMContentLoaded', () => {
    const overlay = document.getElementById('insect-alert-overlay');
    if (overlay) {
        overlay.addEventListener('click', event => {
            if (event.target === overlay) closeInsectAlert();
        });
    }
});

document.addEventListener('keydown', event => {
    if (event.key === 'Escape') closeInsectAlert();
});

async function initPage() {
    await loadDevices();
    if (deviceList.length > 0) {
        selectedDevice = deviceList[0].did;
        renderDeviceSelector();
    }
    loadInsectRecords();
    if (selectedDevice) loadControlPanel(selectedDevice);
}

async function loadDevices() {
    const res = await apiGet('/insect/api/devices');
    if (res && res.data) {
        deviceList = res.data;
    }
}

function renderDeviceSelector() {
    const container = document.getElementById('device-selector');
    if (!container) return;
    container.innerHTML = '<i class="ri-hardware-line"></i>';
    deviceList.forEach(d => {
        const btn = document.createElement('button');
        btn.className = 'device-chip' + (d.did === selectedDevice ? ' active' : '');
        const dot = d.status === '已连接' ? '<i class="ri-checkbox-circle-fill" style="color:var(--accent-secondary);font-size:10px;margin-right:4px;"></i>' : '<i class="ri-close-circle-fill" style="color:#e74c3c;font-size:10px;margin-right:4px;"></i>';
        btn.innerHTML = dot + (d.name || d.did);
        btn.title = '类型: ' + (d.type || '未知') + ' | 状态: ' + (d.status || '未知');
        btn.onclick = () => selectDevice(d.did, btn);
        container.appendChild(btn);
    });
}

function selectDevice(did, el) {
    document.querySelectorAll('.device-chip').forEach(c => c.classList.remove('active'));
    el.classList.add('active');
    selectedDevice = did;
    loadInsectRecords(currentInsectDate);
    loadControlPanel(did);
}

async function selectDate(el, date) {
    el.parentElement.querySelectorAll('.date-chip').forEach(c => c.classList.remove('active'));
    el.classList.add('active');
    currentInsectDate = date;
    loadInsectRecords(date);
}

async function loadInsectRecords(date) {
    const d = date || currentInsectDate;
    let url = '/insect/records';
    if (d) url += '?date=' + d;
    const res = await apiGet(url);
    const gallery = document.getElementById('insect-gallery');
    gallery.innerHTML = '';
    if (res && res.data && res.data.length > 0) {
        res.data.forEach(r => {
            const div = document.createElement('div');
            div.className = 'insect-card';
            const imgHtml = r.thumbUrl
                ? '<div class="insect-img"><img src="' + r.thumbUrl + '" alt="' + r.species + '" style="width:100%;height:100%;object-fit:cover;" onerror="this.style.display=\'none\'"><i class="ri-bug-2-line" style="display:none"></i></div>'
                : '<div class="insect-img"><i class="ri-bug-2-line"></i></div>';
            div.innerHTML = imgHtml +
                '<div class="insect-info"><div class="insect-date">' + (r.recordDate || d) + '</div>' +
                '<div class="insect-type">' + r.species + '</div>' +
                '<div class="insect-count">数量: <span>' + r.count + '</span> 只</div></div>';
            gallery.appendChild(div);
        });
    } else {
        renderDemoInsectGallery(gallery);
    }
    const totalRes = await apiGet('/insect/stats/today');
    if (totalRes && totalRes.data) {
        document.getElementById('insect-total').textContent = totalRes.data.total || 0;
    }
    const typesRes = await apiGet('/insect/stats/types' + (d ? '?date=' + d : ''));
    renderTypeStats(typesRes);
}

function renderDemoInsectGallery(gallery) {
    const demoItems = [
        { image: '/jhds/images/alerts/fruit-fly-detection.png', label: '果蝇检测', detail: 'AI巡检采集' },
        { image: '/jhds/images/alerts/red-spider-suspected.png', label: '红蜘蛛疑似病斑', detail: 'AI识别巡检' },
        { image: '/jhds/images/alerts/longhorn-beetle-detection.png', label: '桃红颈天牛检测', detail: 'AI识别巡检' }
    ];
    gallery.innerHTML = demoItems.map(item =>
        '<div class="insect-card demo-insect-card">' +
            '<div class="insect-img"><img src="' + item.image + '" alt="' + item.label + '"></div>' +
            '<div class="insect-info"><div class="insect-date">' + item.detail + '</div>' +
            '<div class="insect-type">' + item.label + '</div></div>' +
        '</div>'
    ).join('');
}

function renderTypeStats(res) {
    const container = document.querySelector('.type-list');
    if (!container) return;
    container.innerHTML = '';
    if (res && res.data && res.data.length > 0) {
        const maxTotal = Math.max(...res.data.map(t => parseInt(t.total) || 0));
        res.data.forEach(t => {
            const pct = maxTotal > 0 ? ((t.total / maxTotal) * 100).toFixed(0) : 0;
            const row = document.createElement('div');
            row.className = 'type-row';
            row.innerHTML = '<span class="type-name">' + t.species + '</span>' +
                '<div class="type-bar-bg"><div class="type-bar-fill" style="width:' + pct + '%"></div></div>' +
                '<span class="type-count">' + t.total + '</span>';
            container.appendChild(row);
        });
    } else {
        container.innerHTML = '<div style="text-align:center;padding:20px;color:var(--text-dim);">暂无数据</div>';
    }
}

async function loadControlPanel(did) {
    const body = document.getElementById('control-body');
    if (!body) return;
    body.innerHTML = '<div style="text-align:center;padding:20px;color:var(--text-dim);"><i class="ri-loader-4-line spinning" style="font-size:20px;display:block;margin-bottom:6px;"></i>加载设备信息...</div>';

    const [paramsRes, statusRes, realtimeRes] = await Promise.all([
        apiGet('/insect/api/control-params?did=' + did),
        apiGet('/insect/api/control-status?did=' + did),
        apiGet('/insect/api/realtime?did=' + did)
    ]);

    if (paramsRes && paramsRes.data && paramsRes.data.status === 1) {
        controlParamsMap[did] = paramsRes.data.data;
    }

    const statusMap = {};
    if (statusRes && statusRes.data && statusRes.data.status === 1) {
        const data = statusRes.data.data;
        if (data && data.op && Array.isArray(data.op)) {
            data.op.forEach(item => { statusMap[item.groupname] = item.status || '-'; });
        }
    }

    let toolbarHtml = '<div class="control-toolbar">';
    toolbarHtml += '<div class="toolbar-left">';
    toolbarHtml += '<select class="dev-select" onchange="switchDeviceCtrl(this.value)">';
    deviceList.forEach(d => {
        toolbarHtml += '<option value="' + d.did + '"' + (d.did === did ? ' selected' : '') + '>' + (d.name || d.did) + '</option>';
    });
    toolbarHtml += '</select>';
    toolbarHtml += '<span style="font-size:10px;color:var(--text-dim);font-family:var(--font-mono);">' + did + '</span>';
    toolbarHtml += '</div>';
    toolbarHtml += '<div class="toolbar-right">';
    toolbarHtml += '<span id="sync-status" class="sync-status"></span>';
    toolbarHtml += '<button class="ctrl-btn ctrl-btn-primary" onclick="manualCtrlSync()"><i class="ri-cloud-line"></i>手动同步</button>';
    toolbarHtml += '</div></div>';

    let infoHtml = renderRealtimePanel(realtimeRes, did);
    let groupsHtml = renderControlGroups(did, paramsRes, statusMap);

    body.innerHTML = toolbarHtml + '<div class="control-layout">' + infoHtml + groupsHtml + '</div>';
}

function switchDeviceCtrl(did) {
    selectedDevice = did;
    loadControlPanel(did);
}

function renderRealtimePanel(realtimeRes, did) {
    let html = '<div class="control-info-panel">';
    html += '<div class="ctrl-header"><i class="ri-information-line"></i>设备信息</div>';
    html += '<div class="ctrl-device-id">' + did + '</div>';

    if (realtimeRes && realtimeRes.data && realtimeRes.data.status === 1) {
        const arr = realtimeRes.data.data;
        if (Array.isArray(arr) && arr.length > 0) {
            const item = arr[0];
            if (item.jsonstr) {
                try {
                    const rtParams = JSON.parse(item.jsonstr);
                    if (Array.isArray(rtParams) && rtParams.length > 0) {
                        html += '<div class="control-realtime-grid">';
                        rtParams.forEach(p => {
                            const icon = getRealtimeIcon(p.ename || '');
                            let valClass = '';
                            if (p.name && p.name.includes('降雨')) valClass = p.value === '无雨' ? '' : ' style="color:#e8c840;"';
                            if (p.name && p.name.includes('温度')) {
                                const t = parseFloat(p.value);
                                if (t > 50) valClass = ' style="color:#e86060;"';
                                else if (t > 35) valClass = ' style="color:#e8a040;"';
                            }
                            if (p.name && p.name.includes('光照')) valClass = p.value === '弱' ? ' style="color:var(--text-dim);"' : ' style="color:#e8d040;"';
                            html += '<div class="ctrl-realtime-item"><span class="rt-icon">' + icon + '</span><div class="rt-info"><span class="rt-label">' + (p.name || '') + '</span><span class="rt-value"' + valClass + '>' + (p.value || '') + (p.unit || '') + '</span></div></div>';
                        });
                        html += '</div>';
                    }
                } catch (e) {}
            }
        }
    }
    html += '</div>';
    return html;
}

function getRealtimeIcon(ename) {
    const map = {
        'dainfallRegime': '<i class="ri-water-flash-line"></i>',
        'disinsectizingTemp': '<i class="ri-temp-hot-line"></i>',
        'illumination': '<i class="ri-sun-line"></i>',
        'mode': '<i class="ri-settings-3-line"></i>',
        'log': '<i class="ri-map-pin-line"></i>',
        'lad': '<i class="ri-map-pin-line"></i>'
    };
    return map[ename] || '<i class="ri-donut-chart-line"></i>';
}

function renderControlGroups(did, paramsRes, statusMap) {
    const params = controlParamsMap[did];
    if (!params || params.length === 0) {
        return '<div style="flex:1;text-align:center;padding:20px;color:var(--text-dim);">暂无控制参数</div>';
    }

    let html = '<div class="control-groups-panel">';
    params.forEach(group => {
        const status = statusMap[group.groupname] || '-';
        const list = group.list || [];
        const hasManyOps = list.length > 4;
        const activeOp = list.find(op => status === op.opname);
        const statusClass = status && status !== '-' && status !== '' ? (activeOp ? 'active' : 'inactive') : 'idle';

        let statusLabel = status;
        if (statusLabel === '-' || statusLabel === '') statusLabel = '待机';

        html += '<div class="ctrl-group-card">';
        html += '<div class="ctrl-group-header"><span class="ctrl-group-title">' + getGroupIcon(group.groupname) + group.groupname + '</span><span class="ctrl-group-status ' + statusClass + '">' + statusLabel + '</span></div>';
        html += '<div class="ctrl-op-grid' + (hasManyOps ? ' collapsed' : '') + '">';
        list.forEach(op => {
            const isActive = status === op.opname;
            html += '<button class="ctrl-op-btn' + (isActive ? ' active' : '') + '" onclick="triggerCtrlOp(\'' + did + '\',\'' + group.groupname.replace(/'/g, "\\'") + '\',\'' + op.opname.replace(/'/g, "\\'") + '\',this)"' + '>' + getOpIcon(op.opname) + op.opname + '</button>';
        });
        html += '</div>';
        if (hasManyOps) {
            html += '<button class="ctrl-expand-btn" onclick="toggleGroupExpand(this)" data-expanded="false">展开全部 (' + list.length + ')</button>';
        }
        html += '</div>';
    });
    html += '</div>';
    return html;
}

function getGroupIcon(groupname) {
    const map = {
        '诱虫灯': '<i class="ri-sun-line" style="color:#f0c040;font-size:13px;"></i> ',
        '杀虫仓上挡板': '<i class="ri-arrow-up-s-line" style="color:#60b0e0;font-size:13px;"></i> ',
        '杀虫仓加热': '<i class="ri-fire-line" style="color:#e06040;font-size:13px;"></i> ',
        '杀虫仓下挡板': '<i class="ri-arrow-down-s-line" style="color:#60b0e0;font-size:13px;"></i> ',
        '清扫': '<i class="ri-broom-line" style="color:#80c0a0;font-size:13px;"></i> ',
        '补光灯': '<i class="ri-flashlight-line" style="color:#f0d060;font-size:13px;"></i> ',
        '旋转盘': '<i class="ri-refresh-line" style="color:#80b0e0;font-size:13px;"></i> ',
        '运行模式': '<i class="ri-robot-2-line" style="color:#a0c0f0;font-size:13px;"></i> ',
        '拍照': '<i class="ri-camera-line" style="color:#80d0c0;font-size:13px;"></i> '
    };
    for (const key in map) {
        if (groupname.includes(key)) return map[key];
    }
    return '<i class="ri-play-line" style="color:var(--text-dim);font-size:13px;"></i> ';
}

function getOpIcon(opname) {
    if (opname.includes('拍照')) return '<i class="ri-camera-line"></i>';
    if (opname.includes('灯开')) return '<i class="ri-sun-line"></i>';
    if (opname.includes('灯关')) return '<i class="ri-moon-line"></i>';
    if (opname.includes('加热') && opname.includes('开')) return '<i class="ri-fire-line"></i>';
    if (opname.includes('加热') && opname.includes('关')) return '<i class="ri-fire-off-line"></i>';
    if (opname.includes('清扫')) return '<i class="ri-broom-line"></i>';
    if (opname.includes('复位')) return '<i class="ri-restart-line"></i>';
    if (opname.includes('旋转')) return '<i class="ri-refresh-line"></i>';
    if (opname.includes('手动')) return '<i class="ri-hand-line"></i>';
    if (opname.includes('自动')) return '<i class="ri-robot-2-line"></i>';
    if (opname.includes('补光') && opname.includes('开')) return '<i class="ri-sun-line"></i>';
    if (opname.includes('补光') && opname.includes('关')) return '<i class="ri-moon-clear-line"></i>';
    if (opname.includes('开')) return '<i class="ri-toggle-fill"></i>';
    if (opname.includes('关')) return '<i class="ri-toggle-line"></i>';
    return '<i class="ri-play-line"></i>';
}

function toggleGroupExpand(btn) {
    const expanded = btn.getAttribute('data-expanded') === 'true';
    const grid = btn.previousElementSibling;
    if (expanded) {
        grid.classList.add('collapsed');
        btn.setAttribute('data-expanded', 'false');
        const m = grid.querySelectorAll('.ctrl-op-btn').length;
        btn.textContent = '展开全部 (' + m + ')';
    } else {
        grid.classList.remove('collapsed');
        btn.setAttribute('data-expanded', 'true');
        btn.textContent = '收起';
    }
}

async function triggerCtrlOp(did, groupname, opname, btn) {
    const params = controlParamsMap[did];
    if (!params) { setCtrlSyncStatus('请先加载设备参数', '#e74c3c'); return; }
    const group = params.find(g => g.groupname === groupname);
    if (!group || !group.list) { setCtrlSyncStatus('未找到控制组: ' + groupname, '#e74c3c'); return; }
    const op = group.list.find(item => item.opname === opname);
    if (!op) { setCtrlSyncStatus('未找到操作: ' + opname, '#e74c3c'); return; }
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="ri-loader-4-line spinning"></i>'; }
    await sendCtrlCmd(did, op.cmd, groupname, opname);
    if (btn) { btn.disabled = false; }
}

async function sendCtrlCmd(did, cmd, groupname, opname) {
    const res = await apiPost('/insect/api/control', { did, cmd, groupname, opname });
    if (res && res.code === 200) {
        setCtrlSyncStatus('✓ ' + opname + ' 命令已发送', 'var(--accent-secondary)');
        setTimeout(() => loadControlPanel(did), 2000);
    } else {
        setCtrlSyncStatus('✗ ' + (res && res.msg || '发送失败'), '#e74c3c');
    }
}

function setCtrlSyncStatus(msg, color) {
    const el = document.getElementById('sync-status');
    if (!el) return;
    el.textContent = msg;
    el.style.color = color || 'var(--text-dim)';
    clearTimeout(el._timer);
    el._timer = setTimeout(() => { if (el) el.textContent = ''; }, 4000);
}

async function manualCtrlSync() {
    const el = document.getElementById('sync-status');
    if (el) { el.textContent = '⏳ 同步中...'; el.style.color = 'var(--text-dim)'; }
    const res = await apiPost('/insect/api/sync', {});
    if (res && res.code === 200) {
        if (el) { el.textContent = '✓ 同步完成'; el.style.color = 'var(--accent-secondary)'; }
        loadInsectRecords(currentInsectDate);
    } else {
        if (el) { el.textContent = '✗ 同步失败'; el.style.color = '#e74c3c'; }
    }
    if (el) {
        clearTimeout(el._timer);
        el._timer = setTimeout(() => { if (el) el.textContent = ''; }, 3000);
    }
}

async function loadLatestPhotos() {
    const container = document.getElementById('history-gallery');
    if (!container) return;
    switchTabInternal('latest');
    container.innerHTML = '<div style="text-align:center;padding:40px;color:var(--text-dim);"><i class="ri-loader-4-line spinning" style="font-size:24px;display:block;margin-bottom:8px;"></i>加载最新照片...</div>';
    const res = await apiGet('/insect/api/photos/latest');
    container.innerHTML = '';
    if (res && res.data && res.data.length > 0) {
        res.data.forEach(item => {
            const div = document.createElement('div');
            div.className = 'insect-card';
            const thumbSrc = item.originalImage || item.thumb || '';
            const imgHtml = thumbSrc
                ? '<div class="insect-img" onclick="window.open(\'' + thumbSrc + '\',\'_blank\')" style="cursor:pointer;"><img src="' + thumbSrc + '" alt="photo" style="width:100%;height:100%;object-fit:cover;" onerror="this.style.display=\'none\'"><i class="ri-bug-2-line" style="display:none"></i></div>'
                : '<div class="insect-img"><i class="ri-bug-2-line"></i></div>';
            div.innerHTML = imgHtml +
                '<div class="insect-info"><div class="insect-date">' + (item.datetime || '') + '</div>' +
                '<div class="insect-type">设备: ' + (item.did || '') + '</div>' +
                '<div class="insect-count">AI引擎: ' + (item.aiEngine || '-') + ' | 状态: ' + (item.aiStatus === 1 ? '已识别' : '未识别') + '</div></div>';
            container.appendChild(div);
        });
    } else {
        container.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:40px;color:var(--text-dim);"><i class="ri-inbox-line" style="font-size:32px;display:block;margin-bottom:8px;"></i>暂无最新照片</div>';
    }
}

async function loadPhotoHistory(did, page) {
    const container = document.getElementById('history-gallery2');
    if (!container) return;
    switchTabInternal('history');
    page = page || 1;
    const endTime = new Date().toISOString().slice(0, 19).replace('T', ' ');
    const startTime = new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 19).replace('T', ' ');
    container.innerHTML = '<div style="text-align:center;padding:40px;color:var(--text-dim);"><i class="ri-loader-4-line spinning" style="font-size:24px;display:block;margin-bottom:8px;"></i>加载历史记录...</div>';
    const res = await apiGet('/insect/api/photos/history?did=' + did + '&startTime=' + startTime + '&endTime=' + endTime + '&page=' + page + '&num=12');
    container.innerHTML = '';
    if (res && res.data && res.data.length > 1) {
        const items = res.data.slice(1);
        items.forEach(item => {
            const div = document.createElement('div');
            div.className = 'insect-card';
            const thumbSrc = item.sourceThumb || item.thumb || item.originalImage || '';
            const imgHtml = thumbSrc
                ? '<div class="insect-img" onclick="window.open(\'' + thumbSrc + '\',\'_blank\')" style="cursor:pointer;"><img src="' + thumbSrc + '" alt="photo" style="width:100%;height:100%;object-fit:cover;" onerror="this.style.display=\'none\';this.nextSibling.style.display=\'block\'"><i class="ri-bug-2-line" style="display:none"></i></div>'
                : '<div class="insect-img"><i class="ri-bug-2-line"></i></div>';
            let aiLabel = '';
            if (item.aiResult && item.aiResult !== '[]' && item.aiResult !== '') {
                try {
                    const aiArr = JSON.parse(item.aiResult);
                    aiLabel = aiArr.map(a => a.name + '×' + a.num).join(' ');
                } catch (e) {
                    aiLabel = item.aiResult;
                }
            } else {
                aiLabel = item.aiStatus === 1 ? '已识别(无结果)' : '未识别';
            }
            div.innerHTML = imgHtml +
                '<div class="insect-info"><div class="insect-date">' + (item.datetime || '') + '</div>' +
                '<div class="insect-type">' + aiLabel + '</div></div>';
            container.appendChild(div);
        });
        const meta = res.data[0];
        if (meta.total > page * 12) {
            const loadMore = document.createElement('div');
            loadMore.style.cssText = 'grid-column:1/-1;text-align:center;padding:12px;';
            loadMore.innerHTML = '<button class="date-chip" onclick="loadPhotoHistory(\'' + did + '\',' + (page + 1) + ')">加载更多 (' + meta.total + ')</button>';
            container.appendChild(loadMore);
        }
    } else {
        container.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:40px;color:var(--text-dim);"><i class="ri-inbox-line" style="font-size:32px;display:block;margin-bottom:8px;"></i>暂无历史记录</div>';
    }
}

async function loadDataHistory(did) {
    const container = document.getElementById('history-gallery3');
    if (!container) return;
    switchTabInternal('data-history');
    container.innerHTML = '<div style="text-align:center;padding:40px;color:var(--text-dim);"><i class="ri-loader-4-line spinning" style="font-size:24px;display:block;margin-bottom:8px;"></i>加载数据历史...</div>';
    const endTime = new Date().toISOString().slice(0, 19).replace('T', ' ');
    const startTime = new Date(Date.now() - 3 * 86400000).toISOString().slice(0, 19).replace('T', ' ');
    const res = await apiGet('/insect/api/data-history?did=' + did + '&startTime=' + startTime + '&endTime=' + endTime);
    container.innerHTML = '';
    if (res && res.data && res.data.status === 1 && res.data.data && res.data.data.length > 0) {
        const records = res.data.data;
        const allParams = {};
        records.forEach(r => {
            if (r.jsonstr) {
                try {
                    JSON.parse(r.jsonstr).forEach(p => {
                        if (!allParams[p.name]) allParams[p.name] = [];
                        allParams[p.name].push({ time: r.datetime, value: p.value, unit: p.unit });
                    });
                } catch (e) {}
            }
        });
        Object.keys(allParams).forEach(name => {
            const values = allParams[name];
            const section = document.createElement('div');
            section.style.cssText = 'padding:12px 0;border-bottom:1px solid rgba(30,60,100,0.15);';
            section.innerHTML = '<div style="font-size:12px;font-weight:600;color:var(--text-primary);margin-bottom:8px;">' + name + '</div>';
            values.slice(0, 20).forEach(v => {
                section.innerHTML += '<div class="ctrl-info"><span class="ctrl-label">' + v.time + '</span><span class="ctrl-value">' + v.value + (v.unit || '') + '</span></div>';
            });
            if (values.length > 20) {
                section.innerHTML += '<div style="font-size:10px;color:var(--text-dim);text-align:center;">...共' + values.length + '条记录</div>';
            }
            container.appendChild(section);
        });
    } else {
        container.innerHTML = '<div style="text-align:center;padding:40px;color:var(--text-dim);"><i class="ri-inbox-line" style="font-size:32px;display:block;margin-bottom:8px;"></i>暂无数据历史</div>';
    }
}

function switchTabInternal(tab) {
    document.querySelectorAll('.insect-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(t => t.style.display = 'none');
    const tabBtn = document.querySelector('.insect-tab[data-tab="' + tab + '"]');
    if (tabBtn) tabBtn.classList.add('active');
    const content = document.getElementById('tab-content-' + tab);
    if (content) content.style.display = 'block';
}

function switchTab(tab) {
    switchTabInternal(tab);
    if (tab === 'latest' && selectedDevice) loadLatestPhotos();
    else if (tab === 'history' && selectedDevice) loadPhotoHistory(selectedDevice, 1);
    else if (tab === 'data-history' && selectedDevice) loadDataHistory(selectedDevice);
}

window.addEventListener('DOMContentLoaded', initPage);
