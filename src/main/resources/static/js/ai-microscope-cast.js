(function() {
    'use strict';

    var overlay = document.getElementById('microscopeCastOverlay');
    var video = document.getElementById('microscopeCastVideo');
    var title = document.getElementById('microscopeCastTitle');
    var status = document.getElementById('microscopeCastStatus');
    var time = document.getElementById('microscopeCastTime');
    var sound = document.getElementById('microscopeCastSound');
    var fullscreen = document.getElementById('microscopeCastFullscreen');
    var triggers = Array.prototype.slice.call(document.querySelectorAll('.microscope-cast-trigger'));
    var lastFocused = null;
    if (!overlay || !video || !triggers.length) return;

    function formatTime(value) {
        if (!Number.isFinite(value)) return '00:00';
        var seconds = Math.max(0, Math.floor(value));
        return String(Math.floor(seconds / 60)).padStart(2, '0') + ':'
                + String(seconds % 60).padStart(2, '0');
    }

    function updateTime() {
        if (time) time.textContent = formatTime(video.currentTime) + ' / ' + formatTime(video.duration);
    }

    function updateSound() {
        var muted = video.muted;
        sound.setAttribute('aria-pressed', muted ? 'false' : 'true');
        sound.setAttribute('aria-label', muted ? '开启声音' : '关闭声音');
        sound.title = muted ? '开启声音' : '关闭声音';
        sound.innerHTML = '<i class="' + (muted ? 'ri-volume-mute-line' : 'ri-volume-up-line') + '"></i>';
    }

    function showStatus(message, error) {
        if (!status) return;
        status.hidden = false;
        status.classList.toggle('error', Boolean(error));
        status.innerHTML = '<i class="' + (error ? 'ri-error-warning-line' : 'ri-loader-4-line')
                + '"></i><span>' + message + '</span>';
    }

    function openCast(trigger) {
        lastFocused = trigger;
        if (title) title.textContent = trigger.getAttribute('data-cast-title') || '镜检投屏';
        overlay.hidden = false;
        document.body.classList.add('microscope-cast-open');
        video.muted = true;
        video.src = trigger.getAttribute('data-video-src') || '';
        video.load();
        updateSound();
        updateTime();
        showStatus('正在连接镜检画面', false);
        video.play().catch(function() {
            showStatus('点击画面继续播放', true);
        });
        var close = overlay.querySelector('[data-cast-close]');
        if (close) close.focus({ preventScroll: true });
    }

    function closeCast() {
        if (overlay.hidden) return;
        if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen().catch(function() {});
        video.pause();
        video.removeAttribute('src');
        video.load();
        overlay.hidden = true;
        document.body.classList.remove('microscope-cast-open');
        if (lastFocused && lastFocused.focus) lastFocused.focus({ preventScroll: true });
    }

    triggers.forEach(function(trigger) {
        trigger.addEventListener('click', function() { openCast(trigger); });
    });
    overlay.querySelectorAll('[data-cast-close]').forEach(function(button) {
        button.addEventListener('click', closeCast);
    });
    video.addEventListener('playing', function() { if (status) status.hidden = true; });
    video.addEventListener('waiting', function() { showStatus('正在加载镜检画面', false); });
    video.addEventListener('error', function() { showStatus('镜检视频暂时无法播放', true); });
    video.addEventListener('timeupdate', updateTime);
    video.addEventListener('durationchange', updateTime);
    video.addEventListener('click', function() {
        if (video.paused) video.play();
        else video.pause();
    });
    sound.addEventListener('click', function() {
        video.muted = !video.muted;
        updateSound();
        if (video.paused) video.play();
    });
    fullscreen.addEventListener('click', function() {
        if (document.fullscreenElement) {
            document.exitFullscreen();
        } else if (overlay.requestFullscreen) {
            overlay.requestFullscreen();
        }
    });
    document.addEventListener('keydown', function(event) {
        if (event.key === 'Escape' && !overlay.hidden && !document.fullscreenElement) closeCast();
    });
})();
