const DIRS_16 = ["北","北北东","东北","东北东","东","东南东","东南","南南东","南","南南西","西南","西南西","西","西北西","西北","北北西"];

function angleToText(angle) {
    const idx = Math.round(angle / 22.5) % 16;
    return DIRS_16[idx];
}

let weatherChartInstance = null;
let thresholdData = null;
let thresholdExpanded = true;

async function initWeatherChart() {
    const ctx = document.getElementById('weatherChart');
    if (!ctx) return;
    if (weatherChartInstance) weatherChartInstance.destroy();
    const res = await apiGet('/weather/history?days=2');
    const historyData = (res && res.data) ? res.data : [];
    const labels = [];
    const tempData = [];
    const humData = [];
    const windData = [];
    const rainData = [];
    const lightData = [];
    const uvData = [];
    if (historyData.length > 0) {
        historyData.forEach(d => {
            const t = new Date(d.recordTime);
            labels.push(String(t.getHours()).padStart(2,'0') + ':' + String(t.getMinutes()).padStart(2,'0'));
            tempData.push(d.temperature || 0);
            humData.push(d.humidity || 0);
            windData.push(d.windSpeed || 0);
            rainData.push(d.rainfall || 0);
            lightData.push(d.lightIntensity || 0);
            uvData.push(d.uvIndex || 0);
        });
    }
    weatherChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: '温度 (°C)', data: tempData, borderColor: '#ff7a65', backgroundColor: 'rgba(255,122,101,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: '湿度 (%)', data: humData, borderColor: '#30d8f0', backgroundColor: 'rgba(48,216,240,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: '风速 (m/s)', data: windData, borderColor: '#30e8a0', backgroundColor: 'rgba(48,232,160,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: '降雨量 (mm)', data: rainData, borderColor: '#6b9fff', backgroundColor: 'rgba(107,159,255,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: '光照 (Lux)', data: lightData, borderColor: '#f5c842', backgroundColor: 'rgba(245,200,66,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: true },
                { label: '紫外线 (UVI)', data: uvData, borderColor: '#c084fc', backgroundColor: 'rgba(192,132,252,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: true }
            ]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: { legend: { display: false } },
            scales: {
                x: { grid: { color: 'rgba(30,60,100,0.2)' }, ticks: { color: '#7b8fa8', maxTicksLimit: 12 } },
                y: { grid: { color: 'rgba(30,60,100,0.2)' }, ticks: { color: '#7b8fa8' } }
            }
        }
    });
    loadWeatherCurrent();
}

async function loadWeatherCurrent() {
    const res = await apiGet('/weather/current');
    if (res && res.data) {
        const d = res.data;
        document.getElementById('w-temp').textContent = d.temperature || '--';
        document.getElementById('w-humidity').textContent = d.humidity || '--';
        document.getElementById('w-wind').textContent = d.windSpeed || '--';
        document.getElementById('w-rain').textContent = d.rainfall || '--';

        const dir = parseFloat(d.windDirection);
        if (!isNaN(dir)) {
            document.getElementById('w-dir').textContent = dir.toFixed(0);
            document.getElementById('w-dir-text').textContent = angleToText(dir);
        } else {
            document.getElementById('w-dir').textContent = '--';
            document.getElementById('w-dir-text').textContent = '—';
        }

        document.getElementById('w-light').textContent = d.lightIntensity || '--';
        document.getElementById('w-uv').textContent = d.uvIntensity || '--';
        document.getElementById('w-uv-index').textContent = d.uvIndex || '--';

        const battery = d.batteryStatus;
        if (battery === 0) {
            document.getElementById('w-battery').textContent = '正常';
            document.getElementById('w-battery-status').textContent = '✓ 电量充足';
        } else if (battery === 1) {
            document.getElementById('w-battery').textContent = '⚡ 需更换';
            document.getElementById('w-battery-status').textContent = '⚠ 电量不足';
        } else {
            document.getElementById('w-battery').textContent = '--';
            document.getElementById('w-battery-status').textContent = '—';
        }

        document.getElementById('w-hourly-rain').textContent = d.hourlyRainfall || '--';
        document.getElementById('w-daily-rain').textContent = d.dailyRainfall || '--';

        loadWeatherCompare(d);
        applyThresholdWarnings(d);
    }
}

async function loadWeatherCompare(current) {
    const res = await apiGet('/weather/history?days=1');
    if (!res || !res.data || res.data.length === 0) return;

    function avg(key) {
        const vals = res.data.map(d => parseFloat(d[key])).filter(v => !isNaN(v));
        return vals.length === 0 ? null : vals.reduce((a, b) => a + b, 0) / vals.length;
    }

    function updateTrend(id, curVal, yesterdayAvg, unit) {
        const el = document.getElementById(id);
        if (!el || yesterdayAvg === null || curVal === null || curVal === undefined) {
            if (el) el.textContent = '—';
            return;
        }
        const diff = curVal - yesterdayAvg;
        if (Math.abs(diff) < 0.01) {
            el.textContent = '— 持平';
            el.className = 'sensor-trend';
        } else if (diff > 0) {
            el.textContent = '▲ 较昨日 +' + diff.toFixed(1) + unit;
            el.className = 'sensor-trend trend-up';
        } else {
            el.textContent = '▼ 较昨日 ' + diff.toFixed(1) + unit;
            el.className = 'sensor-trend trend-down';
        }
    }

    updateTrend('w-temp-trend', parseFloat(current.temperature), avg('temperature'), '°C');
    updateTrend('w-humidity-trend', parseFloat(current.humidity), avg('humidity'), '%');
    updateTrend('w-wind-trend', parseFloat(current.windSpeed), avg('windSpeed'), 'm/s');
    updateTrend('w-rain-trend', parseFloat(current.rainfall), avg('rainfall'), 'mm');
}

function toggleDataset(index) {
    if (weatherChartInstance) {
        const meta = weatherChartInstance.getDatasetMeta(index);
        meta.hidden = meta.hidden === null ? !weatherChartInstance.data.datasets[index].hidden : null;
        weatherChartInstance.update();
    }
}

async function loadHeartbeat() {
    const res = await apiGet('/weather/heartbeat');
    if (res && res.data) {
        const d = res.data;
        const statusEl = document.getElementById('w-heartbeat-status');
        const timeEl = document.getElementById('w-heartbeat-time');
        const cardEl = document.getElementById('w-heartbeat-card');

        if (d.online) {
            statusEl.textContent = '在线';
            statusEl.style.color = '#34d399';
            timeEl.textContent = d.relativeTime;
            timeEl.style.color = 'rgba(52, 211, 153, 0.7)';
            cardEl.style.borderColor = 'rgba(52, 211, 153, 0.4)';
        } else {
            statusEl.textContent = '离线';
            statusEl.style.color = '#ff4d6a';
            timeEl.textContent = d.lastHeartbeat ? '最后心跳: ' + d.relativeTime : '无心跳数据';
            timeEl.style.color = 'rgba(255, 77, 106, 0.7)';
            cardEl.style.borderColor = 'rgba(255, 77, 106, 0.4)';
        }
    }
}

function toggleThresholdPanel() {
    thresholdExpanded = !thresholdExpanded;
    document.getElementById('threshold-body').style.display = thresholdExpanded ? 'block' : 'none';
    document.getElementById('threshold-toggle').innerHTML = thresholdExpanded
        ? '<i class="ri-arrow-up-s-line"></i>'
        : '<i class="ri-arrow-down-s-line"></i>';
    if (thresholdExpanded) loadThreshold();
}

async function loadThreshold() {
    const res = await apiGet('/weather/threshold');
    if (res && res.data) {
        thresholdData = res.data;
        document.getElementById('th-temp-min').value = thresholdData.tempMin || '';
        document.getElementById('th-temp-max').value = thresholdData.tempMax || '';
        document.getElementById('th-humidity-min').value = thresholdData.humidityMin || '';
        document.getElementById('th-humidity-max').value = thresholdData.humidityMax || '';
        document.getElementById('th-wind-max').value = thresholdData.windSpeedMax || '';
        document.getElementById('th-rainfall-max').value = thresholdData.totalRainfallMax || '';
        document.getElementById('th-hourly-rain-max').value = thresholdData.hourlyRainfallMax || '';
        document.getElementById('th-daily-rain-max').value = thresholdData.dailyRainfallMax || '';
        document.getElementById('th-light-min').value = thresholdData.lightMin || '';
        document.getElementById('th-light-max').value = thresholdData.lightMax || '';
        document.getElementById('th-uv-max').value = thresholdData.uvIntensityMax || '';
        document.getElementById('th-uv-index-max').value = thresholdData.uvIndexMax || '';
        document.getElementById('th-battery-alarm').checked = thresholdData.batteryAlarm === 1;
        document.getElementById('th-enabled').checked = thresholdData.enabled === 1;
    }
}

async function saveThreshold() {
    const btn = document.querySelector('.threshold-save-btn');
    btn.disabled = true;
    btn.textContent = '保存中...';
    const body = {
        tempMin: parseFloat(document.getElementById('th-temp-min').value) || null,
        tempMax: parseFloat(document.getElementById('th-temp-max').value) || null,
        humidityMin: parseFloat(document.getElementById('th-humidity-min').value) || null,
        humidityMax: parseFloat(document.getElementById('th-humidity-max').value) || null,
        windSpeedMax: parseFloat(document.getElementById('th-wind-max').value) || null,
        totalRainfallMax: parseFloat(document.getElementById('th-rainfall-max').value) || null,
        hourlyRainfallMax: parseFloat(document.getElementById('th-hourly-rain-max').value) || null,
        dailyRainfallMax: parseFloat(document.getElementById('th-daily-rain-max').value) || null,
        lightMin: parseFloat(document.getElementById('th-light-min').value) || null,
        lightMax: parseFloat(document.getElementById('th-light-max').value) || null,
        uvIntensityMax: parseFloat(document.getElementById('th-uv-max').value) || null,
        uvIndexMax: parseFloat(document.getElementById('th-uv-index-max').value) || null,
        batteryAlarm: document.getElementById('th-battery-alarm').checked ? 1 : 0,
        enabled: document.getElementById('th-enabled').checked ? 1 : 0
    };
    const res = await apiPut('/weather/threshold', body);
    const msgEl = document.getElementById('threshold-msg');
    if (res && res.code === 200) {
        msgEl.textContent = '✓ 保存成功';
        msgEl.style.color = '#34d399';
        thresholdData = body;
    } else {
        msgEl.textContent = '✗ 保存失败';
        msgEl.style.color = '#ff4d6a';
    }
    setTimeout(function() { msgEl.textContent = ''; }, 3000);
    btn.disabled = false;
    btn.textContent = '保存设置';
}

function applyThresholdWarnings(d) {
    if (!thresholdData) return;
    function warn(elId, val, min, max) {
        const card = document.getElementById(elId);
        if (!card) return;
        if (val === null || val === undefined || val === '--') {
            card.classList.remove('warning');
            card.querySelector('.warn-badge')?.remove();
            return;
        }
        const num = parseFloat(val);
        if (isNaN(num)) {
            card.classList.remove('warning');
            card.querySelector('.warn-badge')?.remove();
            return;
        }
        let triggered = false;
        if (min !== null && min !== undefined && num < min) triggered = true;
        if (max !== null && max !== undefined && num > max) triggered = true;
        if (triggered) {
            card.classList.add('warning');
            if (!card.querySelector('.warn-badge')) {
                const badge = document.createElement('span');
                badge.className = 'warn-badge';
                badge.textContent = '⚠';
                badge.style.cssText = 'position:absolute; top:8px; right:10px; font-size:16px; color:#ff4d6a; animation:warn-blink 1.2s infinite;';
                card.appendChild(badge);
            }
        } else {
            card.classList.remove('warning');
            card.querySelector('.warn-badge')?.remove();
        }
    }

    warn('w-temp', d.temperature, thresholdData.tempMin, thresholdData.tempMax);
    warn('w-humidity', d.humidity, thresholdData.humidityMin, thresholdData.humidityMax);
    warn('w-wind', d.windSpeed, null, thresholdData.windSpeedMax);
    warn('w-rain', d.rainfall, null, thresholdData.totalRainfallMax);
    warn('w-hourly-rain', d.hourlyRainfall, null, thresholdData.hourlyRainfallMax);
    warn('w-daily-rain', d.dailyRainfall, null, thresholdData.dailyRainfallMax);
    warn('w-light', d.lightIntensity, thresholdData.lightMin, thresholdData.lightMax);
    warn('w-uv', d.uvIntensity, null, thresholdData.uvIntensityMax);
    warn('w-uv-index', d.uvIndex, null, thresholdData.uvIndexMax);
}

window.addEventListener('DOMContentLoaded', function() {
    initWeatherChart();
    loadHeartbeat();
    loadThreshold();
    setInterval(loadHeartbeat, 15000);
});
