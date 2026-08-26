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
                    + '<label class="iot-toggle"><input type="checkbox" data-alias="' + d.alias + '"' + (checked ? ' checked' : '') + '><span class="toggle-slider"></span></label>'
                    + '<div class="device-status" id="iot-status-' + d.alias.replace(/[^a-zA-Z0-9]/g, '_') + '" style="color:' + (checked ? 'var(--accent-secondary)' : 'var(--text-secondary)') + '">' + (checked ? '运行中' : '已关闭') + '</div>'
                    + '</div>';
            }).join('')
            + '</div>'
            + '</div>';
    }).join('');
}

async function controlDevice(alias, checked) {
    const status = checked ? 1 : 0;
    const safeAlias = alias.replace(/[^a-zA-Z0-9]/g, '_');
    const statusEl = document.getElementById('iot-status-' + safeAlias);
    statusEl.textContent = checked ? '运行中' : '已关闭';
    statusEl.style.color = checked ? 'var(--accent-secondary)' : 'var(--text-secondary)';
    await apiPut('/iot/device/' + alias, { status: status });
}

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('iot-grid').addEventListener('change', function(e) {
        const input = e.target.closest('.iot-toggle input[data-alias]');
        if (!input) return;
        controlDevice(input.dataset.alias, input.checked);
    });
    loadDevices();
    setInterval(loadDevices, 30000);
    document.getElementById('iot-update-time').textContent = new Date().toLocaleTimeString('zh-CN', { hour12: false });
});
