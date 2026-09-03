function getDeviceIcon(name) {
    const map = {
        '内遮阳': 'ri-sun-line',
        '外遮阳': 'ri-sun-foggy-line',
        '风机': 'ri-wind-line',
        '湿帘': 'ri-water-flash-line',
        '开窗': 'ri-window-line',
        '卷帘': 'ri-blur-off-line',
        '灌溉': 'ri-water-percent-line',
        '补光': 'ri-lightbulb-flash-line',
        '加热': 'ri-fire-line',
        'co2': 'ri-cloud-line'
    };
    for (const [key, icon] of Object.entries(map)) {
        if (name && name.includes(key)) return icon;
    }
    return 'ri-hammer-fill';
}

function getGHNumber(alias) {
    const m = alias && alias.match(/^GH(\d+)/i);
    return m ? parseInt(m[1]) : 0;
}

function formatGHName(num) {
    return '大棚 ' + num;
}

async function loadDevices() {
    const res = await apiGet('/iot/devices');
    if (!res || !res.data) return;

    const groups = {};
    res.data.forEach(d => {
        const num = getGHNumber(d.alias);
        if (!groups[num]) groups[num] = [];
        groups[num].push(d);
    });

    const sortedKeys = Object.keys(groups).sort((a, b) => a - b);
    const grid = document.getElementById('iot-grid');
    grid.innerHTML = sortedKeys.map(num => {
        const devices = groups[num];
        const online = devices.some(d => d.status === 1);
        return '<div class="greenhouse-card">'
            + '<div class="greenhouse-header">'
            + '<div class="greenhouse-icon"><i class="ri-plant-line"></i></div>'
            + '<span class="greenhouse-name">' + formatGHName(parseInt(num)) + '</span>'
            + '<span class="greenhouse-meta"><span class="greenhouse-dot ' + (online ? 'online' : 'offline') + '"></span>' + (online ? '在线' : '离线') + ' · ' + devices.length + ' 台设备</span>'
            + '</div>'
            + '<div class="device-grid">'
            + devices.map(d => {
                const icon = getDeviceIcon(d.name);
                const checked = d.status === 1;
                return '<div class="device-card">'
                    + '<div class="device-icon"><i class="' + icon + '"></i></div>'
                    + '<div class="device-name">' + d.name + '</div>'
                    + '<label class="iot-toggle"><input type="checkbox" data-alias="' + d.alias + '" data-status="' + (checked ? '1' : '0') + '"' + (checked ? ' checked' : '') + '><span class="toggle-slider"></span></label>'
                    + '<div class="device-status" id="iot-status-' + d.alias.replace(/[^a-zA-Z0-9]/g, '_') + '" style="color:' + (checked ? 'var(--accent-secondary)' : 'var(--text-secondary)') + '">' + (checked ? '运行中' : '已关闭') + '</div>'
                    + '</div>';
            }).join('')
            + '</div>'
            + '</div>';
    }).join('');
}

async function loadMqttStatus() {
    const status = document.getElementById('mqtt-status');
    if (!status) return;
    const res = await apiGet('/iot/mqtt-status');
    if (!res || !res.data) {
        status.textContent = 'MQTT 未知';
        status.className = 'mqtt-status offline';
        return;
    }
    const connected = res.data.connected === true;
    status.textContent = connected ? 'MQTT 已连接' : (res.data.enabled ? 'MQTT 未连接' : 'MQTT 已禁用');
    status.className = 'mqtt-status ' + (connected ? 'online' : 'offline');
    status.title = res.data.brokerUrl + ' · ' + res.data.commandTopic;
}

function renderDeviceStatus(alias, checked) {
    const safeAlias = alias.replace(/[^a-zA-Z0-9]/g, '_');
    const statusEl = document.getElementById('iot-status-' + safeAlias);
    if (!statusEl) return;
    statusEl.textContent = checked ? '运行中' : '已关闭';
    statusEl.style.color = checked ? 'var(--accent-secondary)' : 'var(--text-secondary)';
}

async function controlDevice(input) {
    const alias = input.dataset.alias;
    const status = input.checked ? 1 : 0;
    const previous = input.dataset.status === '1';
    input.disabled = true;
    renderDeviceStatus(alias, input.checked);
    const res = await apiPut('/iot/device/' + encodeURIComponent(alias), { status: status });
    input.disabled = false;
    if (!res || res.code !== 200) {
        input.checked = previous;
        renderDeviceStatus(alias, previous);
        window.alert((res && res.msg) || '设备状态未保存，请检查设备连接后重试');
        return;
    }
    input.dataset.status = String(status);
}

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('iot-grid').addEventListener('change', function(e) {
        const input = e.target.closest('.iot-toggle input[data-alias]');
        if (!input) return;
        controlDevice(input);
    });
    loadDevices();
    loadMqttStatus();
    setInterval(loadDevices, 30000);
    setInterval(loadMqttStatus, 10000);
    document.getElementById('iot-update-time').textContent = new Date().toLocaleTimeString('zh-CN', { hour12: false });
});
