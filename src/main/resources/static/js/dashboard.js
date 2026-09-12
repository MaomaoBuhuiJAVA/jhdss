let dashboardOverview = null;
let dashboardPageAlert = null;

const DASHBOARD_ALERT_FALLBACK = {
  title: '嫁接苗异常告警',
  modalTitle: '嫁接苗异常告警',
  description: '⚠️嫁接苗存在异常特征，请及时处理！',
  imagesJson: '["/jhds/images/alerts/graft-union-anomaly.png","/jhds/images/alerts/graft-cut-anomaly.png"]'
};

const DASHBOARD_SUGGESTIONS = [
  {
    title: '当前樱桃生长状况良好，建议：',
    items: ['1号大棚增加营养液 EC 值至 1.5', '3号大棚注意近期湿度偏低', '预计未来3天有降雨，注意棚内通风']
  },
  {
    title: '环境数据整体处于适宜区间，建议：',
    items: ['2号大棚午后湿度升高，提前开启循环风机', '4号大棚光照较强，适当调整遮阳时段', '夜间温差增大，保持棚温不低于 18°C']
  },
  {
    title: '土壤养分消耗出现轻微变化，建议：',
    items: ['1号大棚下一轮灌溉补充钾肥 8%', '2号大棚维持当前 pH 配液参数', '3号大棚 EC 波动偏小，可延长监测周期']
  },
  {
    title: '病虫害模型未发现高风险特征，建议：',
    items: ['继续保持每日两次轨道巡检', '重点复查种植架 3 的叶片背面', '雨后及时排湿，降低白粉病发生概率']
  }
];

const DASHBOARD_WIND_DIRECTIONS = ['北风', '东北风', '东风', '东南风', '南风', '西南风', '西风', '西北风'];
const dashboardWeatherState = {
  temperature: 24.1,
  humidity: 61,
  windSpeed: 2.4,
  windDirection: '东南风',
  lightIntensity: 68900,
  condition: '多云'
};
let dashboardSuggestionIndex = 0;

function dashboardNumber(value, fallback) {
  if (value === null || value === undefined || value === '') return fallback;
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function dashboardClamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function dashboardWindDirection(value, fallback) {
  if (value === null || value === undefined || value === '') return fallback;
  const degrees = Number(value);
  if (!Number.isFinite(degrees)) return String(value);
  const normalized = ((degrees % 360) + 360) % 360;
  return DASHBOARD_WIND_DIRECTIONS[Math.round(normalized / 45) % DASHBOARD_WIND_DIRECTIONS.length];
}

function renderWeatherThumb() {
  setDashboardText('wt-temp', dashboardWeatherState.temperature.toFixed(1) + '\u00B0C');
  setDashboardText('wt-humidity', Math.round(dashboardWeatherState.humidity) + '%');
  setDashboardText('wt-wind', dashboardWeatherState.windSpeed.toFixed(1) + 'm/s');
  setDashboardText('wt-wind-dir', dashboardWeatherState.windDirection);
  setDashboardText('wt-light', Math.round(dashboardWeatherState.lightIntensity / 100) * 100 + ' lux');
  setDashboardText('wt-condition', dashboardWeatherState.condition);
}

async function loadWeatherThumb() {
  const res = await apiGet('/weather/current');
  if (res && res.data) {
    const d = res.data;
    dashboardWeatherState.temperature = dashboardNumber(d.temperature, dashboardWeatherState.temperature);
    dashboardWeatherState.humidity = dashboardNumber(d.humidity, dashboardWeatherState.humidity);
    dashboardWeatherState.windSpeed = dashboardNumber(d.windSpeed, dashboardWeatherState.windSpeed);
    dashboardWeatherState.windDirection = dashboardWindDirection(d.windDirection, dashboardWeatherState.windDirection);
    dashboardWeatherState.lightIntensity = dashboardNumber(d.lightIntensity, dashboardWeatherState.lightIntensity);
    dashboardWeatherState.condition = d.condition || d.weather || dashboardWeatherState.condition;
  }
  renderWeatherThumb();
}

function fluctuateWeatherThumb() {
  dashboardWeatherState.temperature = dashboardClamp(dashboardWeatherState.temperature + (Math.random() - 0.5) * 0.5, 18, 32);
  dashboardWeatherState.humidity = dashboardClamp(dashboardWeatherState.humidity + Math.round((Math.random() - 0.5) * 4), 45, 78);
  dashboardWeatherState.windSpeed = dashboardClamp(dashboardWeatherState.windSpeed + (Math.random() - 0.5) * 0.6, 0.6, 5.8);
  dashboardWeatherState.lightIntensity = dashboardClamp(dashboardWeatherState.lightIntensity + (Math.random() - 0.5) * 2400, 52000, 79000);
  if (Math.random() < 0.18) {
    dashboardWeatherState.windDirection = DASHBOARD_WIND_DIRECTIONS[Math.floor(Math.random() * DASHBOARD_WIND_DIRECTIONS.length)];
  }
  dashboardWeatherState.condition = dashboardWeatherState.humidity > 70 ? '阴' : (dashboardWeatherState.lightIntensity > 73000 ? '晴间多云' : '多云');
  renderWeatherThumb();
}

function renderDashboardSuggestion(index, animate) {
  const container = document.getElementById('dashboard-ai-suggestion');
  if (!container) return;
  const suggestion = DASHBOARD_SUGGESTIONS[index % DASHBOARD_SUGGESTIONS.length];
  const update = function () {
    const title = container.querySelector('p');
    const list = container.querySelector('ol');
    if (title) title.textContent = suggestion.title;
    if (list) {
      list.replaceChildren(...suggestion.items.map(function (item) {
        const row = document.createElement('li');
        row.textContent = item;
        return row;
      }));
    }
    container.dataset.suggestionIndex = String(index % DASHBOARD_SUGGESTIONS.length);
    container.classList.remove('is-changing');
    container.classList.remove('is-entering');
    void container.offsetWidth;
    container.classList.add('is-entering');
    window.setTimeout(function () {
      container.classList.remove('is-entering');
    }, 650);
  };
  if (!animate) {
    container.classList.remove('is-changing', 'is-entering');
    update();
    container.classList.remove('is-entering');
    return;
  }
  container.classList.add('is-changing');
  window.setTimeout(update, 300);
}

function rotateDashboardSuggestion() {
  if (document.hidden) return;
  dashboardSuggestionIndex = (dashboardSuggestionIndex + 1) % DASHBOARD_SUGGESTIONS.length;
  renderDashboardSuggestion(dashboardSuggestionIndex, true);
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
  renderWeatherThumb();
  renderDashboardSuggestion(dashboardSuggestionIndex, false);
  loadWeatherThumb();
  loadDashboardOverview();
  loadDashboardPageAlert();
  window.setInterval(loadDashboardOverview, 30000);
  window.setInterval(loadWeatherThumb, 30000);
  window.setInterval(fluctuateWeatherThumb, 5000);
  window.setInterval(rotateDashboardSuggestion, 7000);
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
