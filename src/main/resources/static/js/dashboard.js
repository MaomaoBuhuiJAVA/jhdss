let dashboardOverview = null;
let dashboardPageAlert = null;

const DASHBOARD_ALERT_FALLBACK = {
  title: '嫁接苗异常告警',
  modalTitle: '嫁接苗异常告警',
  description: '⚠️嫁接苗存在异常特征，请及时处理！',
  imagesJson: '["/jhds/images/alerts/graft-union-anomaly.png","/jhds/images/alerts/graft-cut-anomaly.png"]'
};

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

function setDashboardText(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = value == null || value === '' ? '--' : value;
}

function dashboardDate(value) {
  if (!value) return '--';
  const raw = String(value).slice(0, 10);
  const parts = raw.split('-');
  return parts.length === 3 ? parts[0] + '-' + Number(parts[1]) + '-' + Number(parts[2]) : raw;
}

function dashboardEscape(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderDashboardGreenhouse(greenhouse) {
  const gh = greenhouse || {};
  setDashboardText('dashboard-greenhouse-name', gh.name);
  setDashboardText('dashboard-greenhouse-type', gh.greenhouseType);
  setDashboardText('dashboard-greenhouse-crop', gh.cropName);
  setDashboardText('dashboard-greenhouse-area', gh.area);
  setDashboardText('dashboard-greenhouse-plant-count', gh.plantCount);
  setDashboardText('dashboard-greenhouse-planting-date', dashboardDate(gh.plantingDate));
}

function renderDashboardOperations(operations) {
  const container = document.getElementById('dashboard-operation-list');
  if (!container) return;
  const rows = Array.isArray(operations) ? operations : [];
  if (!rows.length) {
    container.innerHTML = '<div class="dashboard-empty">暂无农事操作</div>';
    return;
  }
  container.innerHTML = rows.map(function (operation) {
    const icon = dashboardEscape(operation.iconClass || 'ri-tools-line');
    const color = dashboardEscape(operation.colorTheme || 'blue');
    return '<div class="operation-item">' +
      '<div class="op-icon ' + color + '"><i class="' + icon + '"></i></div>' +
      '<div class="op-info"><div class="op-name">' + dashboardEscape(operation.operationName) + '</div>' +
      '<div class="op-date">' + dashboardEscape(dashboardDate(operation.operationDate)) + '</div></div></div>';
  }).join('');
}

function renderDashboardTodos(todos) {
  const container = document.getElementById('dashboard-todo-list');
  if (!container) return;
  const rows = Array.isArray(todos) ? todos : [];
  if (!rows.length) {
    container.innerHTML = '<div class="dashboard-empty">暂无待办农事</div>';
    return;
  }
  container.innerHTML = rows.map(function (todo) {
    return '<div class="todo-row"><div class="col-week">' + dashboardEscape(todo.weekLabel) +
      '</div><div class="col-task">' + dashboardEscape(todo.taskName) +
      '</div><div class="col-action">' + dashboardEscape(todo.actionName) + '</div></div>';
  }).join('');
}

function alarmLevelLabel(level) {
  const labels = { urgent: '紧急', important: '重要', normal: '一般' };
  return labels[level] || level || '一般';
}

function renderDashboardAlarms(alarms) {
  const container = document.getElementById('dashboard-alarm-list');
  if (!container) return;
  const rows = Array.isArray(alarms) ? alarms : [];
  if (!rows.length) {
    container.innerHTML = '<div class="dashboard-empty">暂无待处理告警</div>';
    return;
  }
  container.innerHTML = rows.map(function (alarm) {
    const levelClass = ['urgent', 'important', 'normal'].indexOf(alarm.level) >= 0 ? alarm.level : 'normal';
    return '<div class="alert-item" role="button" tabindex="0" data-alarm-id="' + dashboardEscape(alarm.id) + '">' +
      '<div class="alert-header"><div class="alert-title"><i class="ri-error-warning-line"></i><span>' + dashboardEscape(alarm.title) +
      '</span></div><span class="alert-level ' + levelClass + '">' + dashboardEscape(alarmLevelLabel(alarm.level)) + '</span></div>' +
      '<div class="alert-meta"><span class="alert-time">' + dashboardEscape(alarm.createdAt || '--') +
      '</span><span class="alert-location">' + dashboardEscape(alarm.location || '--') + '</span></div></div>';
  }).join('');
}

function renderDashboardMarketFeedback(feedback) {
  const container = document.getElementById('dashboard-market-feedback-list');
  if (!container) return;
  const rows = Array.isArray(feedback) ? feedback : [];
  if (!rows.length) {
    container.innerHTML = '<div class="dashboard-empty">暂无市场反馈</div>';
    return;
  }
  container.innerHTML = rows.map(function (item) {
    return '<button class="market-feedback-item" type="button" data-market-id="' + dashboardEscape(item.id) + '">' +
      '<span class="market-feedback-icon"><i class="ri-notification-3-line"></i></span>' +
      '<span class="market-feedback-copy"><strong>' + dashboardEscape(item.title) + '</strong>' +
      '<small>' + dashboardEscape(item.summary || '') + '</small></span>' +
      '<i class="ri-arrow-right-s-line"></i></button>';
  }).join('');
}

async function loadDashboardOverview() {
  const res = await apiGet('/dashboard/overview');
  if (!res || !res.data) {
    renderDashboardGreenhouse(null);
    renderDashboardOperations([]);
    renderDashboardTodos([]);
    renderDashboardAlarms([]);
    renderDashboardMarketFeedback([]);
    return;
  }
  dashboardOverview = res.data;
  renderDashboardGreenhouse(dashboardOverview.greenhouse);
  renderDashboardOperations(dashboardOverview.operations);
  renderDashboardTodos(dashboardOverview.todos);
  renderDashboardAlarms(dashboardOverview.alarms);
  renderDashboardMarketFeedback(dashboardOverview.marketFeedback);
}

function dashboardAlertImages(content) {
  const source = content && content.imagesJson ? content.imagesJson : DASHBOARD_ALERT_FALLBACK.imagesJson;
  try {
    const images = JSON.parse(source);
    return Array.isArray(images) ? images.map(function (image) {
      if (typeof image === 'string') return { url: image, caption: '' };
      return image && typeof image === 'object' ? { url: image.url || image.imageUrl || '', caption: image.caption || '' } : null;
    }).filter(function (image) { return image && image.url; }) : [];
  } catch (e) {
    return [];
  }
}

function dashboardAlertHtml(content) {
  const data = content || DASHBOARD_ALERT_FALLBACK;
  const images = dashboardAlertImages(data);
  const imageHtml = images.map(function (image, index) {
    return '<img src="' + dashboardEscape(image.url) + '" alt="' + dashboardEscape(image.caption || ('嫁接苗异常特征图' + (index + 1))) + '">';
  }).join('');
  const description = data.description || data.summary || DASHBOARD_ALERT_FALLBACK.description;
  return '<div class="dashboard-modal-images">' + imageHtml +
    '</div><p class="dashboard-alert-message">' + dashboardEscape(description) + '</p>';
}

async function loadDashboardPageAlert() {
  const res = await apiGet('/page-alerts/dashboard-graft');
  if (res && res.data) {
    dashboardPageAlert = res.data;
    if (Number(res.data.enabled) === 0) {
      const alertCard = document.getElementById('dashboard-alert-card');
      if (alertCard) alertCard.hidden = true;
    }
  }
}

function openDashboardModal(type, item) {
  const overlay = document.getElementById('dashboard-modal');
  if (!overlay) return;
  const titleElement = document.getElementById('dashboard-modal-title');
  const bodyElement = document.getElementById('dashboard-modal-body');
  if (type === 'market') {
    const feedback = item || (dashboardOverview && dashboardOverview.marketFeedback && dashboardOverview.marketFeedback[0]);
    if (!feedback) return;
    titleElement.textContent = feedback.modalTitle || feedback.title || '市场反馈';
    bodyElement.textContent = feedback.content || feedback.summary || '';
    bodyElement.className = 'dashboard-modal-body dashboard-market-message';
  } else {
    const content = dashboardPageAlert || DASHBOARD_ALERT_FALLBACK;
    titleElement.textContent = content.modalTitle || content.title || DASHBOARD_ALERT_FALLBACK.modalTitle;
    bodyElement.className = 'dashboard-modal-body';
    bodyElement.innerHTML = dashboardAlertHtml(content);
  }
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
  loadDashboardOverview();
  loadDashboardPageAlert();
  window.setInterval(loadDashboardOverview, 30000);
  window.setInterval(loadWeatherThumb, 30000);
  window.setInterval(loadDashboardPageAlert, 60000);

  const overlay = document.getElementById('dashboard-modal');
  if (overlay) {
    overlay.addEventListener('click', function (event) {
      if (event.target === overlay) closeDashboardModal();
    });
  }

  const alarmList = document.getElementById('dashboard-alarm-list');
  if (alarmList) {
    alarmList.addEventListener('click', function (event) {
      const item = event.target.closest('.alert-item');
      if (!item || !dashboardOverview) return;
      const alarm = (dashboardOverview.alarms || []).find(function (row) {
        return String(row.id) === item.dataset.alarmId;
      });
      openDashboardModal('alert', alarm);
    });
    alarmList.addEventListener('keydown', function (event) {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      const item = event.target.closest('.alert-item');
      if (!item || !dashboardOverview) return;
      event.preventDefault();
      const alarm = (dashboardOverview.alarms || []).find(function (row) {
        return String(row.id) === item.dataset.alarmId;
      });
      openDashboardModal('alert', alarm);
    });
  }

  const marketList = document.getElementById('dashboard-market-feedback-list');
  if (marketList) {
    marketList.addEventListener('click', function (event) {
      const item = event.target.closest('.market-feedback-item');
      if (!item || !dashboardOverview) return;
      const feedback = (dashboardOverview.marketFeedback || []).find(function (row) {
        return String(row.id) === item.dataset.marketId;
      });
      openDashboardModal('market', feedback);
    });
  }
});

document.addEventListener('keydown', function (event) {
  if (event.key === 'Escape') {
    closeDashboardModal();
    return;
  }
  if (event.key === '1' && !isTypingTarget(event.target)) {
    const alertCard = document.getElementById('dashboard-alert-card');
    if (alertCard && (!dashboardPageAlert || Number(dashboardPageAlert.enabled) !== 0)) alertCard.hidden = false;
  }
});
