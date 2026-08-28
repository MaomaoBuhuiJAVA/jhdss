let soilChartInstance = null;

const SOIL_FIELDS = {
    soilTemp: 'soil-temp',
    soilHumidity: 'soil-humidity',
    soilEc: 'soil-ec',
    soilPh: 'soil-ph',
    soilSalt: 'soil-salt',
    soilNitrogen: 'soil-nitrogen',
    soilPhosphorus: 'soil-phosphorus',
    soilPotassium: 'soil-potassium'
};

function apiSucceeded(res) {
    return !!res && (res.code === 200 || res.code === 0);
}

function valueText(value) {
    return value === null || value === undefined || value === '' ? '--' : String(value);
}

function escapeHtml(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function showScheduleMessage(message, isError) {
    const el = document.getElementById('schedule-message');
    if (!el) return;
    el.textContent = message || '';
    el.style.color = isError ? 'var(--accent-warn)' : 'var(--accent-secondary)';
    clearTimeout(el._timer);
    el._timer = setTimeout(function () { el.textContent = ''; }, 3200);
}

function applyNutrientMode(mode) {
    const activeMode = ['manual', 'auto', 'ai'].includes(mode) ? mode : 'manual';
    document.querySelectorAll('.mode-tab').forEach(function (tab) {
        tab.classList.toggle('active', tab.dataset.mode === activeMode);
    });
    document.querySelectorAll('.mode-panel').forEach(function (panel) {
        panel.classList.toggle('active', panel.id === 'panel-' + activeMode);
    });
}

async function switchNutrientMode(mode) {
    const previous = document.querySelector('.mode-tab.active');
    applyNutrientMode(mode);
    const res = await apiPost('/nutrient/mode', { mode: mode });
    if (!apiSucceeded(res)) {
        applyNutrientMode(previous ? previous.dataset.mode : 'manual');
        showScheduleMessage((res && res.msg) || '模式保存失败', true);
    }
}

async function loadNutrientMode() {
    const res = await apiGet('/nutrient/mode');
    applyNutrientMode(apiSucceeded(res) ? res.data : 'manual');
}

async function loadSoilData() {
    const res = await apiGet('/nutrient/soil');
    const data = apiSucceeded(res) && res.data ? res.data : null;

    Object.keys(SOIL_FIELDS).forEach(function (key) {
        const element = document.getElementById(SOIL_FIELDS[key]);
        if (element) element.textContent = valueText(data && data[key]);
    });

    const updateTime = document.getElementById('soil-update-time');
    if (updateTime) {
        if (data && data.recordTime) {
            const time = new Date(data.recordTime);
            const pad = function (number) { return String(number).padStart(2, '0'); };
            updateTime.textContent = pad(time.getMonth() + 1) + '-' + pad(time.getDate()) + ' '
                + pad(time.getHours()) + ':' + pad(time.getMinutes());
        } else {
            updateTime.textContent = '';
        }
    }

    await loadSoilCompare(data);
}

async function loadSoilCompare(current) {
    const res = await apiGet('/nutrient/soil/history?days=1');
    const history = apiSucceeded(res) && Array.isArray(res.data) ? res.data : [];

    function avg(key) {
        const values = history.map(function (item) { return parseFloat(item[key]); }).filter(function (value) { return !isNaN(value); });
        return values.length ? values.reduce(function (sum, value) { return sum + value; }, 0) / values.length : null;
    }

    function updateTrend(id, currentValue, previousAverage) {
        const element = document.getElementById(id);
        if (!element || !current || currentValue === null || currentValue === undefined || isNaN(parseFloat(currentValue)) || previousAverage === null) {
            if (element) element.textContent = '—';
            return;
        }
        const difference = parseFloat(currentValue) - previousAverage;
        if (Math.abs(difference) < 0.01) {
            element.textContent = '— 持平';
            element.className = 'soil-sensor-trend';
        } else if (difference > 0) {
            element.textContent = '▲ 较昨日 +' + difference.toFixed(1);
            element.className = 'soil-sensor-trend trend-up';
        } else {
            element.textContent = '▼ 较昨日 ' + difference.toFixed(1);
            element.className = 'soil-sensor-trend trend-down';
        }
    }

    updateTrend('soil-temp-trend', current && current.soilTemp, avg('soilTemp'));
    updateTrend('soil-humidity-trend', current && current.soilHumidity, avg('soilHumidity'));
    updateTrend('soil-ec-trend', current && current.soilEc, avg('soilEc'));
    updateTrend('soil-ph-trend', current && current.soilPh, avg('soilPh'));
    updateTrend('soil-salt-trend', current && current.soilSalt, avg('soilSalt'));
    updateTrend('soil-nitrogen-trend', current && current.soilNitrogen, avg('soilNitrogen'));
    updateTrend('soil-phosphorus-trend', current && current.soilPhosphorus, avg('soilPhosphorus'));
    updateTrend('soil-potassium-trend', current && current.soilPotassium, avg('soilPotassium'));
}

async function initSoilChart() {
    const canvas = document.getElementById('soilChart');
    if (!canvas) return;
    if (soilChartInstance) soilChartInstance.destroy();

    const res = await apiGet('/nutrient/soil/history?days=2');
    const history = apiSucceeded(res) && Array.isArray(res.data) ? res.data : [];
    const labels = [];
    const temperature = [];
    const humidity = [];
    const ec = [];
    const ph = [];

    history.forEach(function (item) {
        const time = new Date(item.recordTime);
        labels.push(String(time.getHours()).padStart(2, '0') + ':' + String(time.getMinutes()).padStart(2, '0'));
        temperature.push(item.soilTemp == null ? null : item.soilTemp);
        humidity.push(item.soilHumidity == null ? null : item.soilHumidity);
        ec.push(item.soilEc == null ? null : item.soilEc);
        ph.push(item.soilPh == null ? null : item.soilPh);
    });

    soilChartInstance = new Chart(canvas, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: '温度 (°C)', data: temperature, borderColor: '#ff7a65', backgroundColor: 'rgba(255,122,101,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: '湿度 (%)', data: humidity, borderColor: '#30d8f0', backgroundColor: 'rgba(48,216,240,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: 'EC (mS/cm)', data: ec, borderColor: '#f5c842', backgroundColor: 'rgba(245,200,66,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false },
                { label: 'PH', data: ph, borderColor: '#c084fc', backgroundColor: 'rgba(192,132,252,0.1)', tension: 0.4, pointRadius: 0, borderWidth: 2, hidden: false }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
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
    if (!soilChartInstance) return;
    const meta = soilChartInstance.getDatasetMeta(index);
    meta.hidden = meta.hidden === null ? !soilChartInstance.data.datasets[index].hidden : null;
    soilChartInstance.update();
}

function getPumpIcon(pump) {
    const name = (pump.name || '').toLowerCase();
    if (name.includes('二氧化碳') || name.includes('co2')) return 'ri-cloud-line';
    if (name.includes('循环')) return 'ri-loop-right-line';
    if (name.includes('氯化钙')) return 'ri-contrast-drop-2-line';
    if (name.includes('灌溉')) return 'ri-water-flash-line';
    if (name.includes('混')) return 'ri-recycle-line';
    return 'ri-drop-fill';
}

function pumpStatusId(alias) {
    return 'status-' + String(alias || '').replace(/[^a-zA-Z0-9]/g, '_');
}

async function loadPumpStatus() {
    const grid = document.getElementById('pump-grid');
    if (!grid) return;
    const res = await apiGet('/nutrient/pumps');
    const pumps = apiSucceeded(res) && Array.isArray(res.data) ? res.data : [];
    if (!pumps.length) {
        grid.innerHTML = '<div class="pump-empty">暂无已配置的配液设备</div>';
        return;
    }
    grid.innerHTML = pumps.map(function (pump) {
        const checked = Number(pump.status) === 1;
        const alias = String(pump.alias || '');
        const statusId = pumpStatusId(alias);
        return '<div class="pump-card" data-api-pump>'
            + '<div class="pump-icon"><i class="' + getPumpIcon(pump) + '"></i></div>'
            + '<div class="pump-name">' + escapeHtml(pump.name || alias) + '</div>'
            + '<label class="toggle-switch"><input type="checkbox" data-alias="' + escapeHtml(alias) + '" aria-label="' + escapeHtml(pump.name || alias) + '开关"' + (checked ? ' checked' : '') + '><span class="toggle-slider"></span></label>'
            + '<div class="pump-status" id="' + statusId + '" style="color:' + (checked ? 'var(--accent-secondary)' : 'var(--text-secondary)') + '">' + (checked ? '运行中' : '已关闭') + '</div>'
            + '</div>';
    }).join('');
}

async function controlPump(alias, checked, input) {
    const status = checked ? 1 : 0;
    const statusElement = document.getElementById(pumpStatusId(alias));
    if (input) input.disabled = true;
    if (statusElement) {
        statusElement.textContent = '保存中…';
        statusElement.style.color = 'var(--text-secondary)';
    }
    const res = await apiPut('/nutrient/pump/' + encodeURIComponent(alias), { status: status });
    if (!apiSucceeded(res)) {
        if (input) input.checked = !checked;
        if (statusElement) {
            statusElement.textContent = checked ? '已关闭' : '运行中';
            statusElement.style.color = checked ? 'var(--text-secondary)' : 'var(--accent-secondary)';
        }
        showScheduleMessage((res && res.msg) || '设备状态保存失败', true);
    } else if (statusElement) {
        statusElement.textContent = checked ? '运行中' : '已关闭';
        statusElement.style.color = checked ? 'var(--accent-secondary)' : 'var(--text-secondary)';
    }
    if (input) input.disabled = false;
}

function normalizeScheduleTime(value) {
    return String(value || '').slice(0, 5);
}

function selectFrequency(frequency) {
    const selected = frequency || 'daily';
    document.querySelectorAll('.freq-chip').forEach(function (chip) {
        chip.classList.toggle('active', chip.dataset.frequency === selected);
    });
}

async function loadSchedules() {
    const res = await apiGet('/nutrient/schedules');
    const schedules = apiSucceeded(res) && Array.isArray(res.data) ? res.data : [];
    const rows = Array.from(document.querySelectorAll('#panel-auto .time-row'));
    rows.forEach(function (row) {
        delete row.dataset.scheduleId;
        const time = row.querySelector('input[type="time"]');
        const duration = row.querySelector('select');
        if (time) time.value = '';
        if (duration) duration.value = '10 分钟';
    });
    schedules.slice(0, rows.length).forEach(function (schedule, index) {
        const row = rows[index];
        const time = row.querySelector('input[type="time"]');
        const duration = row.querySelector('select');
        row.dataset.scheduleId = schedule.id;
        if (time) time.value = normalizeScheduleTime(schedule.scheduleTime);
        if (duration) duration.value = (schedule.duration || 10) + ' 分钟';
    });
    if (schedules.length) selectFrequency(schedules[0].frequency);
}

async function saveSchedule() {
    const button = document.querySelector('#panel-auto .btn-primary');
    if (button) button.disabled = true;
    const activeChip = document.querySelector('.freq-chip.active');
    const frequency = activeChip ? activeChip.dataset.frequency : 'daily';
    const rows = Array.from(document.querySelectorAll('#panel-auto .time-row'));
    try {
        for (const row of rows) {
            const id = row.dataset.scheduleId ? Number(row.dataset.scheduleId) : null;
            const time = row.querySelector('input[type="time"]').value;
            if (!time) {
                if (id) {
                    const deleteResult = await apiDelete('/nutrient/schedule/' + id);
                    if (!apiSucceeded(deleteResult)) throw new Error((deleteResult && deleteResult.msg) || '删除计划失败');
                }
                continue;
            }
            const duration = parseInt(row.querySelector('select').value, 10) || 10;
            const result = await apiPost('/nutrient/schedule', {
                id: id,
                scheduleTime: time + ':00',
                duration: duration,
                frequency: frequency,
                enabled: 1
            });
            if (!apiSucceeded(result)) throw new Error((result && result.msg) || '保存计划失败');
        }
        await loadSchedules();
        showScheduleMessage('灌溉计划已保存');
    } catch (error) {
        showScheduleMessage(error.message || '灌溉计划保存失败', true);
    } finally {
        if (button) button.disabled = false;
    }
}

async function loadIrrigationRecords() {
    const res = await apiGet('/nutrient/stats');
    const data = apiSucceeded(res) && res.data ? res.data : {};
    const setStat = function (name, value, unit) {
        const element = document.querySelector('#irrigation-stats [data-stat="' + name + '"]');
        if (element) element.textContent = valueText(value) + (value == null ? '' : ' ' + unit);
    };
    setStat('today-count', data.todayCount, '次');
    setStat('total-water', data.totalWater, 'L');
    setStat('pump-a', data.pumpA, 'mL');
    setStat('pump-b', data.pumpB, 'mL');
    setStat('pump-acid', data.pumpAcid, 'mL');
    setStat('pump-base', data.pumpBase, 'mL');
    const next = document.getElementById('next-irrigation-time');
    if (next) next.textContent = data.nextIrrigationTime || '--:--';

    const log = document.getElementById('ai-log');
    if (log && Array.isArray(data.recentRecords)) {
        if (!data.recentRecords.length) {
            log.innerHTML = '<div class="log-empty">暂无AI决策记录</div>';
        } else {
            log.innerHTML = data.recentRecords.map(function (record) {
                const time = record.createdAt ? String(record.createdAt).replace('T', ' ') : '--';
                const mode = record.mode === 'auto' ? '自动' : (record.mode === 'ai' ? 'AI' : '手动');
                const minutes = Math.max(0, Math.round(Number(record.duration || 0) / 60));
                return '<div class="log-item"><div class="log-time">' + escapeHtml(time) + '</div>'
                    + '<div class="log-msg">' + escapeHtml(mode + '模式执行 ' + (record.pumpAlias || '灌溉泵') + '，运行 ' + minutes + ' 分钟') + '</div></div>';
            }).join('');
        }
    }
}

document.addEventListener('DOMContentLoaded', function () {
    const grid = document.getElementById('pump-grid');
    if (grid) {
        grid.addEventListener('change', function (event) {
            const input = event.target.closest('.toggle-switch input[data-alias]');
            if (input) controlPump(input.dataset.alias, input.checked, input);
        });
    }
    document.querySelectorAll('.freq-chip').forEach(function (chip) {
        chip.addEventListener('click', function () { selectFrequency(chip.dataset.frequency); });
    });
    loadNutrientMode();
    loadSoilData();
    initSoilChart();
    loadPumpStatus();
    loadSchedules();
    loadIrrigationRecords();
    setInterval(loadSoilData, 30000);
    setInterval(loadPumpStatus, 30000);
    setInterval(loadSchedules, 30000);
    setInterval(loadIrrigationRecords, 30000);
});
