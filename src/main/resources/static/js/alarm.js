let alarmPieInstance = null;
const STORAGE_STATUS = 'alarm_status';
const STORAGE_MEMO = 'alarm_memo';

const fakeAlarms = [
  { id: 1, title: '发现虫害几棵', description: 'AI图像识别检测到种植架1、2出现蚜虫聚集，建议立即进行植保处理', level: 'urgent', module: '虫情灯模块', time: '2026-03-30 13:30' },
  { id: 2, title: '植株叶面存在杂点', description: '轨道巡检摄像头检测到A2区域植株叶面出现不明杂点，疑似病害早期', level: 'important', module: 'AI轨道巡检', time: '2026-03-30 12:45' },
  { id: 3, title: '土壤湿度偏低', description: '土壤湿度传感器显示当前湿度45%，略低于设定阈值50%', level: 'normal', module: '营养液配液', time: '2026-03-30 11:20' },
  { id: 4, title: '风速超过3级', description: '气象站监测到当前风速3.2m/s，建议检查大棚通风口', level: 'normal', module: '气象站', time: '2026-03-30 10:15' },
  { id: 5, title: '营养液EC值异常', description: '土壤EC值达到2.1mS/cm，超出正常范围1.5-2.0，需调整配液比例', level: 'important', module: '营养液配液', time: '2026-03-30 09:30' },
  { id: 6, title: '轨道巡检设备离线', description: 'AI轨道巡检模块通信中断，已持续5分钟，请检查网络连接', level: 'urgent', module: 'AI轨道巡检', time: '2026-03-30 08:45' },
  { id: 7, title: '大棚温度过高', description: '大棚1温度达到38°C，超过预警阈值35°C，建议开启通风降温', level: 'important', module: '物联设备', time: '2026-03-30 14:10' },
  { id: 8, title: '二氧化碳浓度偏低', description: '大棚2内CO\u2082浓度降至280ppm，低于光合作用适宜值，建议增施CO\u2082', level: 'normal', module: '物联设备', time: '2026-03-30 13:50' },
  { id: 9, title: '光照强度不足', description: '连续阴天导致大棚内光照强度仅8000lux，建议开启补光灯', level: 'normal', module: '物联设备', time: '2026-03-30 07:30' },
  { id: 10, title: '水泵异常停机', description: '灌溉系统B水泵电流异常自动停机，需检查电机和电路', level: 'urgent', module: '营养液配液', time: '2026-03-30 06:15' },
];

function getFromStorage(key, id) {
  const data = JSON.parse(localStorage.getItem(key) || '{}');
  return data[id];
}

function setToStorage(key, id, val) {
  const data = JSON.parse(localStorage.getItem(key) || '{}');
  data[id] = val;
  localStorage.setItem(key, JSON.stringify(data));
}

function loadAlarmList() {
  const container = document.getElementById('alarm-list');
  container.innerHTML = '';
  fakeAlarms.forEach(r => {
    const status = getFromStorage(STORAGE_STATUS, r.id) || 'pending';
    const memo = getFromStorage(STORAGE_MEMO, r.id) || '';
    const levelText = { urgent: '紧急', important: '重要', normal: '一般' }[r.level];
    const div = document.createElement('div');
    div.className = 'alarm-row ' + r.level;
    div.innerHTML =
      '<div class="alarm-severity ' + r.level + '"></div>' +
      '<div class="alarm-content">' +
        '<div class="alarm-title-row">' +
          '<span class="alarm-title-text">' + r.title + '</span>' +
          '<span class="alarm-badge ' + r.level + '">' + levelText + '</span>' +
        '</div>' +
        '<div class="alarm-desc">' + r.description + '</div>' +
        '<div class="alarm-detail">' +
          '<div class="alarm-detail-row">' +
            '<span class="alarm-status-label">状态</span>' +
            '<select class="alarm-status-select ' + status + '" data-id="' + r.id + '">' +
              '<option value="pending"' + (status === 'pending' ? ' selected' : '') + '>待解决</option>' +
              '<option value="processing"' + (status === 'processing' ? ' selected' : '') + '>处理中</option>' +
              '<option value="resolved"' + (status === 'resolved' ? ' selected' : '') + '>已解决</option>' +
            '</select>' +
          '</div>' +
          '<textarea class="alarm-memo" data-id="' + r.id + '" placeholder="记录处置说明...">' + memo + '</textarea>' +
        '</div>' +
      '</div>' +
      '<div class="alarm-meta"><div class="alarm-time">' + r.time + '</div><div class="alarm-module">' + r.module + '</div></div>';
    div.addEventListener('click', function (e) {
      if (e.target.closest('.alarm-status-select') || e.target.closest('.alarm-memo')) return;
      this.classList.toggle('expanded');
    });
    container.appendChild(div);
  });
  updateCount();
  bindAlarmEvents();
}

function updateCount() {
  const counts = { urgent: 0, important: 0, normal: 0 };
  fakeAlarms.forEach(r => { counts[r.level]++; });
  document.querySelector('.stat-card.urgent .stat-number').textContent = counts.urgent;
  document.querySelector('.stat-card.important .stat-number').textContent = counts.important;
  document.querySelector('.stat-card.normal .stat-number').textContent = counts.normal;
}

function bindAlarmEvents() {
  document.querySelectorAll('.alarm-status-select').forEach(sel => {
    sel.addEventListener('change', function () {
      setToStorage(STORAGE_STATUS, this.dataset.id, this.value);
      this.className = 'alarm-status-select ' + this.value;
    });
  });
  document.querySelectorAll('.alarm-memo').forEach(ta => {
    ta.addEventListener('input', function () {
      setToStorage(STORAGE_MEMO, this.dataset.id, this.value);
    });
  });
}

async function initAlarmPieChart() {
  const ctx = document.getElementById('alarmPieChart');
  if (!ctx) return;
  if (alarmPieInstance) alarmPieInstance.destroy();
  const moduleCount = {};
  fakeAlarms.forEach(r => {
    moduleCount[r.module] = (moduleCount[r.module] || 0) + 1;
  });
  const labels = Object.keys(moduleCount);
  const data = Object.values(moduleCount);
  const colors = ['#00c6f0', '#00e887', '#ff4d6a', '#f0a040', '#c084fc', '#f472b6'];
  alarmPieInstance = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: labels,
      datasets: [{ data: data, backgroundColor: colors.slice(0, labels.length), borderWidth: 0 }]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: { legend: { position: 'bottom', labels: { color: '#7b8fa8', font: { size: 11 }, padding: 15 } } },
      cutout: '60%'
    }
  });
}

function filterAlarm(el, type) {
  el.parentElement.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  el.classList.add('active');
  document.querySelectorAll('.alarm-row').forEach(row => {
    row.style.display = (type === 'all' || row.classList.contains(type)) ? 'flex' : 'none';
  });
}

window.addEventListener('DOMContentLoaded', function () {
  loadAlarmList();
  initAlarmPieChart();
});
