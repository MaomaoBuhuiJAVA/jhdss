let soilChartInstance = null;

function switchNutrientMode(mode) {
    document.querySelectorAll('.mode-tab').forEach(t => t.classList.remove('active'));
    event.target.classList.add('active');
    document.querySelectorAll('.mode-panel').forEach(p => p.classList.remove('active'));
    document.getElementById('panel-' + mode).classList.add('active');
    apiPost('/nutrient/mode', { mode: mode });
}

async function loadSoilData() {
    const res = await apiGet('/nutrient/soil');
    if (res && res.data) {
        const d = res.data;
        document.getElementById('soil-temp').textContent = d.soilTemp ?? '--';
        document.getElementById('soil-humidity').textContent = d.soilHumidity ?? '--';
        document.getElementById('soil-ec').textContent = d.soilEc ?? '--';
        document.getElementById('soil-ph').textContent = d.soilPh ?? '--';
        document.getElementById('soil-salt').textContent = d.soilSalt ?? '--';
        document.getElementById('soil-nitrogen').textContent = d.soilNitrogen ?? '--';
        document.getElementById('soil-phosphorus').textContent = d.soilPhosphorus ?? '--';
        document.getElementById('soil-potassium').textContent = d.soilPotassium ?? '--';

        if (d.recordTime) {
            const t = new Date(d.recordTime);
            const pad = n => String(n).padStart(2, '0');
            document.getElementById('soil-update-time').textContent =
                pad(t.getMonth() + 1) + '-' + pad(t.getDate()) + ' ' + pad(t.getHours()) + ':' + pad(t.getMinutes());
        }

        loadSoilCompare(d);
    }
}

async function loadSoilCompare(current) {
    const res = await apiGet('/nutrient/soil/history?days=1');
    if (!res || !res.data || res.data.length === 0) return;

    function avg(key) {
        const vals = res.data.map(d => parseFloat(d[key])).filter(v => !isNaN(v));
        return vals.length === 0 ? null : vals.reduce((a, b) => a + b, 0) / vals.length;
    }

    function updateTrend(id, curVal, yesterdayAvg) {
        const el = document.getElementById(id);
        if (!el || yesterdayAvg === null || curVal === null || curVal === undefined) {
            if (el) el.textContent = '—';
            return;
        }
        const diff = curVal - yesterdayAvg;
        if (Math.abs(diff) < 0.01) {
            el.textContent = '— 持平';
            el.className = 'soil-sensor-trend';
        } else if (diff > 0) {
            el.textContent = '▲ 较昨日 +' + diff.toFixed(1);
            el.className = 'soil-sensor-trend trend-up';
        } else {
            el.textContent = '▼ 较昨日 ' + diff.toFixed(1);
            el.className = 'soil-sensor-trend trend-down';
        }
    }

    updateTrend('soil-temp-trend', parseFloat(current.soilTemp), avg('soilTemp'));
    updateTrend('soil-humidity-trend', parseFloat(current.soilHumidity), avg('soilHumidity'));
    updateTrend('soil-ec-trend', parseFloat(current.soilEc), avg('soilEc'));
    updateTrend('soil-ph-trend', parseFloat(current.soilPh), avg('soilPh'));
    updateTrend('soil-salt-trend', parseFloat(current.soilSalt), avg('soilSalt'));
    updateTrend('soil-nitrogen-trend', parseFloat(current.soilNitrogen), avg('soilNitrogen'));
    updateTrend('soil-phosphorus-trend', parseFloat(current.soilPhosphorus), avg('soilPhosphorus'));
    updateTrend('soil-potassium-trend', parseFloat(current.soilPotassium), avg('soilPotassium'));
}

async function initSoilChart() {
    const ctx = document.getElementById('soilChart');
    if (!ctx) return;
    if (soilChartInstance) soilChartInstance.destroy();

    const res = await apiGet('/nutrient/soil/history?days=2');
    const historyData = (res && res.data) ? res.data : [];
    const labels = [];
    const tempData = [];
    const humData = [];
    const ecData = [];
    const phData = [];

    if (historyData.length > 0) {
        historyData.forEach(d => {
            const t = new Date(d.recordTime);
            labels.push(String(t.getHours()).padStart(2, '0') + ':' + String(t.getMinutes()).padStart(2, '0'));
            tempData.push(d.soilTemp || 0);
            humData.push(d.soilHumidity || 0);
            ecData.push(d.soilEc || 0);
            phData.push(d.soilPh || 0);
        });
    }

    soilChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: '温度 (°C)', data: tempData, borderColor: '#ff7a65', backgroundColor: 'rgba(255,122,101,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: '湿度 (%)', data: humData, borderColor: '#30d8f0', backgroundColor: 'rgba(48,216,240,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: 'EC (mS/cm)', data: ecData, borderColor: '#f5c842', backgroundColor: 'rgba(245,200,66,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: 'PH', data: phData, borderColor: '#c084fc', backgroundColor: 'rgba(192,132,252,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false }
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
}

function toggleSoilDataset(index) {
    if (soilChartInstance) {
        const meta = soilChartInstance.getDatasetMeta(index);
        meta.hidden = meta.hidden === null ? !soilChartInstance.data.datasets[index].hidden : null;
        soilChartInstance.update();
    }
}

function getPumpIcon(suffix) {
    const map = {
        'a': 'ri-drop-fill',
        'b': 'ri-drop-fill',
        'acid': 'ri-contrast-drop-2-line',
        'base': 'ri-contrast-drop-line',
        'irrigate': 'ri-water-flash-line',
        'mix': 'ri-recycle-line'
    };
    return map[suffix] || 'ri-hammer-fill';
}

async function loadPumpStatus() {
    const res = await apiGet('/nutrient/pumps');
    if (!res || !res.data) return;
    const grid = document.getElementById('pump-grid');
    const pumps = res.data.filter(e => e.alias && e.alias.startsWith('PUMP_'));
    grid.innerHTML = pumps.map(pump => {
        const suffix = pump.alias.replace('PUMP_', '').toLowerCase();
        const icon = getPumpIcon(suffix);
        const checked = pump.status === 1;
        return '<div class="pump-card">'
            + '<div class="pump-icon"><i class="' + icon + '"></i></div>'
            + '<div class="pump-name">' + pump.name + '</div>'
            + '<label class="toggle-switch"><input type="checkbox" data-alias="' + pump.alias + '"' + (checked ? ' checked' : '') + '><span class="toggle-slider"></span></label>'
            + '<div class="pump-status" id="status-' + suffix + '" style="color:' + (checked ? 'var(--accent-secondary)' : 'var(--text-secondary)') + '">' + (checked ? '运行中...' : '已关闭') + '</div>'
            + '</div>';
    }).join('');
}

async function controlPump(alias, checked) {
    const status = checked ? 1 : 0;
    const suffix = alias.replace('PUMP_', '').toLowerCase();
    const statusEl = document.getElementById('status-' + suffix);
    statusEl.textContent = checked ? '运行中...' : '已关闭';
    statusEl.style.color = checked ? 'var(--accent-secondary)' : 'var(--text-secondary)';
    await apiPut('/nutrient/pump/' + alias, { status: status });
}

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('pump-grid').addEventListener('change', function(e) {
        const input = e.target.closest('.toggle-switch input[data-alias]');
        if (!input) return;
        controlPump(input.dataset.alias, input.checked);
    });
    document.querySelectorAll('.freq-chip').forEach(chip => {
        chip.addEventListener('click', function() {
            this.parentElement.querySelectorAll('.freq-chip').forEach(c => c.classList.remove('active'));
            this.classList.add('active');
        });
    });
    loadSoilData();
    initSoilChart();
    loadPumpStatus();
    loadIrrigationRecords();
    setInterval(loadSoilData, 30000);
});

async function saveSchedule() {
    const timeInputs = document.querySelectorAll('#panel-auto input[type="time"]');
    const durationSelects = document.querySelectorAll('#panel-auto select');
    for (let i = 0; i < timeInputs.length; i++) {
        const time = timeInputs[i].value;
        const duration = parseInt(durationSelects[i]?.value) || 10;
        if (time) {
            await apiPost('/nutrient/schedule', {
                scheduleTime: time + ':00',
                duration: duration,
                frequency: 'daily'
            });
        }
    }
    alert('灌溉计划已保存');
}

async function loadIrrigationRecords() {
    const res = await apiGet('/nutrient/stats');
    if (res && res.data) {
        document.querySelector('#irrigation-stats .info-row:first-child .info-value').textContent = (res.data.todayCount || 0) + ' 次';
        document.querySelector('#irrigation-stats .info-row:nth-child(2) .info-value').textContent = (res.data.totalWater || 0) + ' L';
    }
}
