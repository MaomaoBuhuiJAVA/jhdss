let alarmPieInstance = null;

const ALARM_PAGE_SIZE = 500;
const alarmState = {
  records: [],
  stats: null,
  filter: 'all',
  memoTimers: new Map(),
  memoSaveChains: new Map()
};

const levelText = { urgent: '紧急', important: '重要', normal: '一般' };
const sourceText = {
  insect: '虫情灯模块',
  patrol: 'AI轨道巡检',
  nutrient: '营养液配液',
  weather: '气象站',
  iot: '物联设备'
};

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function alarmStatus(status) {
  switch (Number(status)) {
    case 2: return 'processing';
    case 1: return 'resolved';
    default: return 'pending';
  }
}

function formatAlarmTime(value) {
  if (!value) return '--';
  if (typeof value === 'string') return value.replace('T', ' ').slice(0, 16);
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '--';
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' +
    String(date.getDate()).padStart(2, '0') + ' ' + String(date.getHours()).padStart(2, '0') + ':' +
    String(date.getMinutes()).padStart(2, '0');
}

function resultData(response, fallback) {
  return response && (response.code === 200 || response.code === 0) && response.data != null
    ? response.data
    : fallback;
}

function showAlarmMessage(message, isError) {
  const messageBox = document.getElementById('alarm-message');
  if (!messageBox) return;
  messageBox.textContent = message || '';
  messageBox.className = isError ? 'alarm-message error' : 'alarm-message';
  if (!message) return;
  window.clearTimeout(messageBox._timer);
  messageBox._timer = window.setTimeout(function () {
    messageBox.textContent = '';
    messageBox.className = 'alarm-message';
  }, 3500);
}

function renderAlarmList() {
  const container = document.getElementById('alarm-list');
  if (!container) return;
  const records = alarmState.filter === 'all'
    ? alarmState.records
    : alarmState.records.filter(function (record) { return record.level === alarmState.filter; });

  if (!records.length) {
    container.innerHTML = '<div class="alarm-empty"><i class="ri-inbox-line"></i><span>' +
      (alarmState.records.length ? '当前筛选条件下暂无告警' : '暂无告警记录') + '</span></div>';
    return;
  }

  container.innerHTML = records.map(function (record) {
    const level = levelText[record.level] ? record.level : 'normal';
    const status = alarmStatus(record.status);
    const source = sourceText[record.sourceModule] || record.sourceModule || '未分类';
    const location = record.location ? '<span class="alarm-location">' + escapeHtml(record.location) + '</span>' : '';
    return '<div class="alarm-row ' + level + '" data-id="' + record.id + '">' +
      '<div class="alarm-severity ' + level + '"></div>' +
      '<div class="alarm-content">' +
      '<div class="alarm-title-row">' +
      '<span class="alarm-title-text">' + escapeHtml(record.title || '未命名告警') + '</span>' +
      '<span class="alarm-badge ' + level + '">' + levelText[level] + '</span>' +
      '</div>' +
      '<div class="alarm-desc">' + escapeHtml(record.description || '暂无描述') + '</div>' +
      '<div class="alarm-detail">' +
      '<div class="alarm-detail-row">' +
      '<label class="alarm-status-label" for="alarm-status-' + record.id + '">状态</label>' +
      '<select id="alarm-status-' + record.id + '" class="alarm-status-select ' + status + '" data-id="' + record.id + '">' +
      '<option value="pending"' + (status === 'pending' ? ' selected' : '') + '>待处理</option>' +
      '<option value="processing"' + (status === 'processing' ? ' selected' : '') + '>处理中</option>' +
      '<option value="resolved"' + (status === 'resolved' ? ' selected' : '') + '>已解决</option>' +
      '</select>' +
      '</div>' +
      '<label class="alarm-memo-label" for="alarm-memo-' + record.id + '">处置说明</label>' +
      '<textarea id="alarm-memo-' + record.id + '" class="alarm-memo" data-id="' + record.id + '" placeholder="记录处置说明...">' +
      escapeHtml(record.handlingMemo || '') + '</textarea>' +
      '</div>' +
      '</div>' +
      '<div class="alarm-meta"><div class="alarm-time">' + formatAlarmTime(record.createdAt) + '</div>' +
      '<div class="alarm-module">' + escapeHtml(source) + location + '</div></div>' +
      '</div>';
  }).join('');

  bindAlarmEvents();
}

function syncRecord(updated) {
  if (!updated || updated.id == null) return;
  alarmState.records = alarmState.records.map(function (record) {
    return record.id === updated.id ? updated : record;
  });
}

async function saveAlarm(id, changes) {
  const response = await apiPut('/alarm/' + encodeURIComponent(id), changes);
  if (!response || (response.code !== 200 && response.code !== 0)) {
    showAlarmMessage((response && response.msg) || '保存失败，请检查数据库连接后重试', true);
    return null;
  }
  syncRecord(response.data);
  return response.data;
}

function queueMemoSave(id, value) {
  const previous = alarmState.memoSaveChains.get(id) || Promise.resolve();
  const next = previous.catch(function () { return null; }).then(function () {
    return saveAlarm(id, { handlingMemo: value });
  });
  alarmState.memoSaveChains.set(id, next);
  return next;
}

function bindAlarmEvents() {
  document.querySelectorAll('.alarm-row').forEach(function (row) {
    row.addEventListener('click', function (event) {
      if (event.target.closest('.alarm-status-select') || event.target.closest('.alarm-memo')) return;
      row.classList.toggle('expanded');
    });
  });

  document.querySelectorAll('.alarm-status-select').forEach(function (select) {
    select.addEventListener('change', async function () {
      const original = alarmState.records.find(function (record) { return record.id === Number(select.dataset.id); });
      const previous = original ? alarmStatus(original.status) : 'pending';
      select.disabled = true;
      const updated = await saveAlarm(select.dataset.id, { status: select.value });
      select.disabled = false;
      if (!updated) {
        select.value = previous;
        select.className = 'alarm-status-select ' + previous;
        return;
      }
      const status = alarmStatus(updated.status);
      select.value = status;
      select.className = 'alarm-status-select ' + status;
      showAlarmMessage('告警状态已同步到数据库');
    });
  });

  document.querySelectorAll('.alarm-memo').forEach(function (textarea) {
    textarea.addEventListener('input', function () {
      const id = textarea.dataset.id;
      window.clearTimeout(alarmState.memoTimers.get(id));
      alarmState.memoTimers.set(id, window.setTimeout(async function () {
        alarmState.memoTimers.delete(id);
        const updated = await queueMemoSave(id, textarea.value);
        if (updated) showAlarmMessage('处置说明已同步到数据库');
      }, 600));
    });
    textarea.addEventListener('blur', function () {
      const id = textarea.dataset.id;
      const timer = alarmState.memoTimers.get(id);
      if (!timer) return;
      window.clearTimeout(timer);
      alarmState.memoTimers.delete(id);
      queueMemoSave(id, textarea.value).then(function (updated) {
        if (updated) showAlarmMessage('处置说明已同步到数据库');
      });
    });
  });
}

function getCount(stats, level) {
  const rows = stats && Array.isArray(stats.byLevel) ? stats.byLevel : [];
  const row = rows.find(function (item) { return item.level === level; });
  return row
    ? Number(row.count || 0)
    : alarmState.records.filter(function (record) { return record.level === level; }).length;
}

function getSourceStats() {
  if (alarmState.stats && Array.isArray(alarmState.stats.bySource)) {
    return alarmState.stats.bySource;
  }
  const counts = {};
  alarmState.records.forEach(function (record) {
    const sourceModule = record.sourceModule || 'unknown';
    counts[sourceModule] = (counts[sourceModule] || 0) + 1;
  });
  return Object.keys(counts).map(function (sourceModule) {
    return { sourceModule: sourceModule, count: counts[sourceModule] };
  });
}

function updateStats() {
  const stats = alarmState.stats;
  const total = stats && stats.total != null ? Number(stats.total) : alarmState.records.length;
  const totalNode = document.getElementById('alarm-total-count');
  if (totalNode) totalNode.textContent = total;
  const urgent = document.querySelector('.stat-card.urgent .stat-number');
  const important = document.querySelector('.stat-card.important .stat-number');
  const normal = document.querySelector('.stat-card.normal .stat-number');
  if (urgent) urgent.textContent = getCount(stats, 'urgent');
  if (important) important.textContent = getCount(stats, 'important');
  if (normal) normal.textContent = getCount(stats, 'normal');
}

function renderPieChart() {
  const ctx = document.getElementById('alarmPieChart');
  if (!ctx || typeof Chart === 'undefined') return;
  if (alarmPieInstance) alarmPieInstance.destroy();
  const sourceStats = getSourceStats();
  const labels = sourceStats.map(function (item) { return sourceText[item.sourceModule] || item.sourceModule || '未分类'; });
  const data = sourceStats.map(function (item) { return Number(item.count || 0); });
  const colors = ['#00c6f0', '#00e887', '#ff4d6a', '#f0a040', '#c084fc', '#f472b6'];
  alarmPieInstance = new Chart(ctx, {
    type: 'doughnut',
    data: { labels: labels, datasets: [{ data: data, backgroundColor: colors.slice(0, labels.length), borderWidth: 0 }] },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: 'bottom', labels: { color: '#7b8fa8', font: { size: 11 }, padding: 15 } } },
      cutout: '60%'
    }
  });
}

async function loadAlarmPage() {
  const container = document.getElementById('alarm-list');
  if (container) container.innerHTML = '<div class="alarm-empty"><i class="ri-loader-4-line alarm-loading"></i><span>正在加载告警记录...</span></div>';
  const results = await Promise.all([
    apiGet('/alarm/list?page=1&size=' + ALARM_PAGE_SIZE),
    apiGet('/alarm/stats')
  ]);
  const listResponse = results[0];
  const statsResponse = results[1];
  const page = resultData(listResponse, null);
  if (!page) {
    alarmState.records = [];
    alarmState.stats = null;
    renderAlarmList();
    showAlarmMessage((listResponse && listResponse.msg) || '告警数据加载失败，请检查数据库是否已迁移', true);
    return;
  }
  alarmState.records = Array.isArray(page.records) ? page.records : [];
  alarmState.stats = resultData(statsResponse, null);
  renderAlarmList();
  updateStats();
  renderPieChart();
}

function filterAlarm(element, type) {
  alarmState.filter = type;
  document.querySelectorAll('.filter-btn').forEach(function (button) { button.classList.remove('active'); });
  if (element) element.classList.add('active');
  renderAlarmList();
}

window.filterAlarm = filterAlarm;
window.addEventListener('DOMContentLoaded', function () {
  loadAlarmPage();
  window.setInterval(function () {
    const focused = document.activeElement;
    const editing = focused && (focused.matches('.alarm-memo') || focused.matches('.alarm-status-select'));
    if (!editing && alarmState.memoTimers.size === 0) loadAlarmPage();
  }, 30000);
});
