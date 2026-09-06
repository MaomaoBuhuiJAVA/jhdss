const API_BASE = window.location.origin + '/jhds/api';

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
    document.getElementById('clock').querySelector('span').textContent = str;
}
setInterval(updateClock, 1000);
updateClock();
