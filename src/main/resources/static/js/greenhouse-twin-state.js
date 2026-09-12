(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.GreenhouseTwinState = api;
})(typeof window === 'undefined' ? globalThis : window, function () {
    'use strict';
    const keys = ['foliar', 'drip', 'gas'];
    const finite = value => typeof value === 'number' && Number.isFinite(value);
    const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
    function decode(payload, serverNow) {
        if (!payload || payload.code !== 200 || !payload.data || !finite(payload.data.timestampMs)) {
            throw new Error('Invalid twin snapshot');
        }
        const data = payload.data, pose = data.pose || {};
        if (finite(serverNow) && Math.abs(serverNow - data.timestampMs) > 5000) throw new Error('Stale snapshot');
        const result = {timestampMs: data.timestampMs, pose: {}, effects: {}};
        // Server-to-server timestamp comparison avoids client clock skew.
        const validPose = pose.connected === true && finite(pose.timestampMs)
            && Math.abs(data.timestampMs - pose.timestampMs) < 5000;
        const validX = validPose && (pose.xConnected === undefined || pose.xConnected === true);
        const validY = validPose && (pose.yConnected === undefined || pose.yConnected === true);
        result.pose.connected = validX || validY;
        result.pose.xConnected = validX;
        result.pose.yConnected = validY;
        result.pose.source = pose.source;
        if (validPose) for (const key of ['x', 'y', 'pan_deg', 'tilt_deg']) {
            if ((key === 'x' && !validX) || (key === 'y' && !validY)) continue;
            if (finite(pose[key])) result.pose[key] = clamp(pose[key],
                key.endsWith('_deg') ? -180 : 0, key.endsWith('_deg') ? 180 : 1);
        }
        if (validX && finite(pose.velocityX)) result.pose.velocityX = clamp(pose.velocityX, -1, 1);
        if (validY && finite(pose.velocityY)) result.pose.velocityY = clamp(pose.velocityY, -1, 1);
        if (validX && finite(pose.minX)) result.pose.minX = clamp(pose.minX, 0, 1);
        for (const key of keys) {
            const item = (data.effects || {})[key] || {};
            const known = item.connected === true && typeof item.active === 'boolean'
                && ['command-response', 'measured'].includes(item.source)
                && finite(item.acknowledgedAt) && item.acknowledgedAt <= data.timestampMs;
            result.effects[key] = {active: known && item.active, known,
                source: known ? item.source : 'unknown', connected: item.connected === true};
        }
        return result;
    }
    function disconnected() {
        return {pose: {connected: false, xConnected: false, yConnected: false}, effects: Object.fromEntries(keys.map(key =>
            [key, {active: false, known: false, connected: false, source: 'unknown'}]))};
    }
    return {decode, disconnected, keys};
});
