document.addEventListener('DOMContentLoaded', function () {
    const host = document.getElementById('nutrient-twin');
    if (!host || !window.createGreenhouseTwin) return;
    const twin = window.createGreenhouseTwin(host);
    window.nutrientTwin = twin;
    document.getElementById('nutrient-twin-reset').addEventListener('click', twin.resetView);
    const screen = document.getElementById('nutrient-model-screen');
    const fullscreen = document.getElementById('nutrient-twin-fullscreen');
    fullscreen.hidden = !document.fullscreenEnabled;
    fullscreen.addEventListener('click', async function () {
        try {
            if (document.fullscreenElement) await document.exitFullscreen();
            else await screen.requestFullscreen();
        } catch (error) { fullscreen.title = '当前浏览器不支持全屏'; }
    });
    document.addEventListener('fullscreenchange', function () {
        const active = document.fullscreenElement === screen;
        fullscreen.title = active ? '退出全屏' : '全屏';
        fullscreen.setAttribute('aria-label', fullscreen.title);
        fullscreen.firstElementChild.className = active ? 'ri-fullscreen-exit-line' : 'ri-fullscreen-line';
        twin.resize();
    });
});
