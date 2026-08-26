const API_BASE = window.location.origin + '/jhds/api';

async function apiGet(url) {
    try {
        const res = await fetch(API_BASE + url);
        return await res.json();
    } catch(e) {
        console.warn('API error:', url, e);
        return null;
    }
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
