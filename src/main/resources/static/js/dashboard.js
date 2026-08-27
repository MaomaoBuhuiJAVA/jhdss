async function loadWeatherThumb() {
  const res = await apiGet('/weather/current');
  if (res && res.data) {
    const d = res.data;
    document.getElementById('wt-temp').textContent = (d.temperature ?? '--') + '\u00B0C';
    document.getElementById('wt-humidity').textContent = (d.humidity ?? '--') + '%';
    document.getElementById('wt-wind').textContent = (d.windSpeed ?? '--') + 'm/s';
    document.getElementById('wt-wind-dir').textContent = d.windDirection ?? '--';
  }
}

const DASHBOARD_MODAL_CONTENT = {
  alert: {
    title: '嫁接苗异常告警',
    html: '<div class="dashboard-modal-images">' +
      '<img src="/jhds/images/alerts/graft-union-anomaly.png" alt="嫁接苗嫁接口异常特征">' +
      '<img src="/jhds/images/alerts/graft-cut-anomaly.png" alt="嫁接苗切口异常特征">' +
      '</div><p class="dashboard-alert-message">⚠️嫁接苗存在异常特征，请及时处理！</p>'
  },
  market: {
    title: '消费者反映风味欠佳',
    html: '<p class="dashboard-market-message">据NFC追溯得到的消费者评价数据，56%消费者反映该批次樱桃糖度较低；33%消费者反映消费者反映该批次樱桃酸度过高，9%消费者反映该批次樱桃硬度较低。</p>'
  }
};

function openDashboardModal(type) {
  const content = DASHBOARD_MODAL_CONTENT[type];
  const overlay = document.getElementById('dashboard-modal');
  if (!content || !overlay) return;
  document.getElementById('dashboard-modal-title').textContent = content.title;
  document.getElementById('dashboard-modal-body').innerHTML = content.html;
  overlay.hidden = false;
  document.body.style.overflow = 'hidden';
  overlay.querySelector('.dashboard-modal-close').focus();
}

function closeDashboardModal() {
  const overlay = document.getElementById('dashboard-modal');
  if (!overlay) return;
  overlay.hidden = true;
  document.body.style.overflow = '';
}

function isTypingTarget(target) {
  return target && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName));
}

document.addEventListener('DOMContentLoaded', function () {
  loadWeatherThumb();
  const overlay = document.getElementById('dashboard-modal');
  if (overlay) {
    overlay.addEventListener('click', function (event) {
      if (event.target === overlay) closeDashboardModal();
    });
  }
  document.querySelectorAll('.alert-item[role="button"]').forEach(function (item) {
    item.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        openDashboardModal('alert');
      }
    });
  });
});

document.addEventListener('keydown', function (event) {
  if (event.key === 'Escape') {
    closeDashboardModal();
    return;
  }
  if (event.key === '1' && !isTypingTarget(event.target)) {
    const alertCard = document.getElementById('dashboard-alert-card');
    if (alertCard) alertCard.hidden = false;
  }
});
