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

async function loadIotDevices() {
  const res = await apiGet('/iot/devices');
  const container = document.getElementById('device-list');
  if (!container) return;
  if (!res || !res.data || res.data.length === 0) {
    container.innerHTML = '<div class="device-item" style="justify-content:center;color:var(--text-dim);font-size:12px;cursor:default;">暂无设备</div>';
    return;
  }
  container.innerHTML = res.data.map(d => {
    const online = d.status === 1;
    return '<div class="device-item" onclick="location.href=\'/jhds/iot\'" style="cursor:pointer;">' +
      '<div class="device-icon"><i class="ri-drone-line"></i></div>' +
      '<span class="device-name">' + d.name + '</span>' +
      '<div class="device-status" style="background:' + (online ? 'var(--accent-secondary)' : 'var(--text-dim)') + ';box-shadow:' + (online ? '0 0 10px var(--accent-secondary)' : 'none') + ';' + (online ? '' : 'animation:none;') + '"></div>' +
      '</div>';
  }).join('');
}

document.addEventListener('DOMContentLoaded', function () {
  loadWeatherThumb();
  loadIotDevices();
});
