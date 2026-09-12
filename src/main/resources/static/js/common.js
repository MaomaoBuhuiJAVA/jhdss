const API_BASE = window.location.origin + '/jhds/api';

const PAGE_ENTER_SELECTOR = [
    '.card',
    '.sensor-card',
    '.stat-card',
    '.chart-container',
    '.yolo-panel',
    '.ai-learn-card',
    '.ai-conversations',
    '.ai-workspace',
    '.nutrient-equipment',
    '.patrol-video',
    '.patrol-autostart-card',
    '.patrol-control-card',
    '.main > .center-area'
].join(',');

let pageEntranceCleanupTimer = null;

function initPageEntrance() {
    const main = document.querySelector('.main');
    if (!main || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const candidates = Array.from(main.querySelectorAll(PAGE_ENTER_SELECTOR)).filter(function(element) {
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.display !== 'none' && style.visibility !== 'hidden'
            && rect.width > 2 && rect.height > 2;
    });
    const modules = candidates.filter(function(element) {
        return !candidates.some(function(parent) {
            return parent !== element && parent.contains(element);
        });
    }).sort(function(a, b) {
        const ar = a.getBoundingClientRect();
        const br = b.getBoundingClientRect();
        const rowDifference = ar.top - br.top;
        return Math.abs(rowDifference) > 18 ? rowDifference : ar.left - br.left;
    });
    if (!modules.length) return;

    clearTimeout(pageEntranceCleanupTimer);
    modules.forEach(function(element, index) {
        const rect = element.getBoundingClientRect();
        const center = rect.left + rect.width / 2;
        const horizontalOffset = center < window.innerWidth * .36 ? '-8px'
            : center > window.innerWidth * .64 ? '8px' : '0px';
        element.classList.add('page-enter-module');
        element.style.setProperty('--page-enter-x', horizontalOffset);
        element.style.setProperty('--page-enter-delay', Math.min(index * 45, 360) + 'ms');
    });

    pageEntranceCleanupTimer = window.setTimeout(function() {
        modules.forEach(function(element) {
            element.classList.remove('page-enter-module');
            element.style.removeProperty('--page-enter-x');
            element.style.removeProperty('--page-enter-delay');
        });
    }, 1050);
}

async function apiGet(url) {
    // Live camera requests must not hang indefinitely while the bridge is
    // restarting. Retry short network failures so the page recovers without a
    // manual refresh; other API calls keep the same single-request behavior.
    const isCameraRequest = /\/camera\/(play-url|local-status)/.test(url);
    const attempts = isCameraRequest ? 3 : 1;
    for (let attempt = 0; attempt < attempts; attempt++) {
        try {
            const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
            const timer = controller ? setTimeout(() => controller.abort(), isCameraRequest ? 12000 : 30000) : null;
            const res = await fetch(API_BASE + url, controller ? { signal: controller.signal } : undefined);
            if (timer) clearTimeout(timer);
            const data = await res.json();
            if (data && data.code === 503 && attempt + 1 < attempts) {
                await new Promise(resolve => setTimeout(resolve, 500 * (attempt + 1)));
                continue;
            }
            return data;
        } catch(e) {
            console.warn('API error:', url, e);
            if (attempt + 1 < attempts) await new Promise(resolve => setTimeout(resolve, 500 * (attempt + 1)));
        }
    }
    return null;
}
async function apiPost(url, body) {
    try {
        const res = await fetch(API_BASE + url, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body)
        });
        return await res.json();
    } catch(e) {
        console.warn('API error:', url, e);
        return null;
    }
}
async function apiPut(url, body) {
    try {
        const res = await fetch(API_BASE + url, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body)
        });
        return await res.json();
    } catch(e) {
        console.warn('API error:', url, e);
        return null;
    }
}
async function apiDelete(url) {
    try {
        const res = await fetch(API_BASE + url, { method: 'DELETE' });
        return await res.json();
    } catch(e) {
        console.warn('API error:', url, e);
        return null;
    }
}

function updateClock() {
    const now = new Date();
    const str = now.getFullYear() + '/' + (now.getMonth()+1) + '/' + now.getDate() + ' ' +
        String(now.getHours()).padStart(2,'0') + ':' + String(now.getMinutes()).padStart(2,'0') + ':' + String(now.getSeconds()).padStart(2,'0');
    var clockRoot = document.getElementById('clock');
    var clockTarget = clockRoot && (clockRoot.querySelector('.header-clock-value') || clockRoot.querySelector('span'));
    if (clockTarget) clockTarget.textContent = str;
}
setInterval(updateClock, 1000);
updateClock();

function initDraggableAiFloat() {
    const ball = document.querySelector('.ai-float');
    if (!ball || ball.dataset.dragBound === 'true') return;
    ball.dataset.dragBound = 'true';
    ball.setAttribute('role', 'button');
    ball.setAttribute('tabindex', '0');
    ball.setAttribute('aria-label', 'AI 农业管家，拖动可调整位置，点击开始对话');
    ball.title = 'AI 农业管家（可拖动）';

    const storageKey = 'jhds-ai-float-position';
    const edgeGap = 14;
    let pointerId = null;
    let startX = 0;
    let startY = 0;
    let originLeft = 0;
    let originTop = 0;
    let dragged = false;
    let suppressClick = false;

    function clamp(left, top) {
        const maxLeft = Math.max(edgeGap, window.innerWidth - ball.offsetWidth - edgeGap);
        const maxTop = Math.max(edgeGap, window.innerHeight - ball.offsetHeight - edgeGap);
        return {
            left: Math.max(edgeGap, Math.min(maxLeft, left)),
            top: Math.max(edgeGap, Math.min(maxTop, top))
        };
    }

    function place(left, top, animate) {
        const point = clamp(left, top);
        ball.style.transition = animate ? '' : 'none';
        ball.style.left = point.left + 'px';
        ball.style.top = point.top + 'px';
        ball.style.right = 'auto';
        ball.style.bottom = 'auto';
        if (!animate) requestAnimationFrame(function() { ball.style.transition = ''; });
        return point;
    }

    function save(point) {
        try {
            localStorage.setItem(storageKey, JSON.stringify({
                side: point.left + ball.offsetWidth / 2 < window.innerWidth / 2 ? 'left' : 'right',
                topRatio: point.top / Math.max(1, window.innerHeight - ball.offsetHeight)
            }));
        } catch (e) {
            // Storage can be disabled; dragging should still work for this page.
        }
    }

    function restore() {
        try {
            const saved = JSON.parse(localStorage.getItem(storageKey) || 'null');
            if (!saved || typeof saved.topRatio !== 'number') return;
            const left = saved.side === 'left' ? edgeGap : window.innerWidth - ball.offsetWidth - edgeGap;
            place(left, saved.topRatio * Math.max(1, window.innerHeight - ball.offsetHeight), false);
        } catch (e) {
            // Ignore malformed or unavailable local storage.
        }
    }

    ball.addEventListener('pointerdown', function(event) {
        if (event.button !== 0) return;
        const rect = ball.getBoundingClientRect();
        pointerId = event.pointerId;
        startX = event.clientX;
        startY = event.clientY;
        originLeft = rect.left;
        originTop = rect.top;
        dragged = false;
        ball.setPointerCapture(pointerId);
        ball.classList.add('dragging');
    });

    ball.addEventListener('pointermove', function(event) {
        if (event.pointerId !== pointerId) return;
        const deltaX = event.clientX - startX;
        const deltaY = event.clientY - startY;
        if (!dragged && Math.hypot(deltaX, deltaY) < 5) return;
        dragged = true;
        place(originLeft + deltaX, originTop + deltaY, false);
    });

    function finishDrag(event) {
        if (event.pointerId !== pointerId) return;
        if (ball.hasPointerCapture(pointerId)) ball.releasePointerCapture(pointerId);
        pointerId = null;
        ball.classList.remove('dragging');
        if (!dragged) return;
        const rect = ball.getBoundingClientRect();
        const left = rect.left + rect.width / 2 < window.innerWidth / 2
            ? edgeGap : window.innerWidth - rect.width - edgeGap;
        const point = place(left, rect.top, true);
        save(point);
        suppressClick = true;
    }

    ball.addEventListener('pointerup', finishDrag);
    ball.addEventListener('pointercancel', finishDrag);
    ball.addEventListener('click', function(event) {
        if (!suppressClick) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        suppressClick = false;
    }, true);
    ball.addEventListener('keydown', function(event) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            window.location.href = '/jhds/ai';
        }
    });
    window.addEventListener('resize', function() {
        const rect = ball.getBoundingClientRect();
        const point = place(rect.left, rect.top, false);
        save(point);
    });
    restore();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
        initPageEntrance();
        initDraggableAiFloat();
    });
} else {
    initPageEntrance();
    initDraggableAiFloat();
}

window.addEventListener('pageshow', function(event) {
    if (event.persisted) initPageEntrance();
});
