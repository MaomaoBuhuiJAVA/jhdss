const assert = require('node:assert/strict');
const {test} = require('node:test');
const state = require('../../src/main/resources/static/js/greenhouse-twin-state.js');
const packet = () => ({code: 200, data: {timestampMs: 10000,
    pose: {connected: true, x: .75, timestampMs: 10000},
    effects: Object.fromEntries(state.keys.map(k => [k, {active: true, source: 'command-response', connected: true, acknowledgedAt: 9000}]))}});
test('all eight independent effect combinations', () => {
    for (let mask = 0; mask < 8; mask++) {
        const p = packet();
        state.keys.forEach((key, i) => p.data.effects[key].active = !!(mask & (1 << i)));
        const result = state.decode(p);
        state.keys.forEach((key, i) => assert.equal(result.effects[key].active, !!(mask & (1 << i))));
    }
});
test('disconnected, missing, local and target-only states never animate', () => {
    for (const invalid of [{}, {active: '1'}, {active: true, source: 'local-saved'}, {active: true, source: 'target'}, {active: true, connected: false}]) {
        const p = packet(); p.data.effects.foliar = invalid;
        assert.equal(state.decode(p).effects.foliar.active, false);
    }
});
test('stale/offline pose freezes independently from connected pumps', () => {
    const p = packet(); p.data.pose.timestampMs = 100;
    assert.equal(state.decode(p).pose.x, undefined);
    assert.equal(state.decode(p).effects.drip.active, true);
    p.data.pose.timestampMs = 10000; p.data.pose.connected = false;
    assert.equal(state.decode(p).pose.x, undefined);
});
test('clamps pose and holds absent axes rather than inventing motion', () => {
    const p = packet(); p.data.pose.x = 12;
    assert.equal(state.decode(p).pose.x, 1);
    assert.equal(state.decode(p).pose.y, undefined);
    for (const value of [NaN, Infinity, true, '0.5', null]) {
        p.data.pose.x = value; assert.equal(state.decode(p).pose.x, undefined);
    }
});
test('decodes independent horizontal and panel motor motion', () => {
    const p = packet();
    Object.assign(p.data.pose, {x: .8, y: .2, velocityX: -.05, velocityY: .08,
        xConnected: true, yConnected: true});
    const result = state.decode(p);
    assert.equal(result.pose.x, .8);
    assert.equal(result.pose.y, .2);
    assert.equal(result.pose.velocityX, -.05);
    assert.equal(result.pose.velocityY, .08);

    p.data.pose.xConnected = false;
    const panelOnly = state.decode(p);
    assert.equal(panelOnly.pose.x, undefined);
    assert.equal(panelOnly.pose.velocityX, undefined);
    assert.equal(panelOnly.pose.y, .2);
    assert.equal(panelOnly.pose.connected, true);
});
test('invalid payload is rejected and disconnect clears all effects', () => {
    for (const p of [null, {}, {code:500}, {code:200,data:{timestampMs:'10'}}]) assert.throws(() => state.decode(p));
    state.keys.forEach(key => assert.equal(state.disconnected().effects[key].active, false));
});
test('server date rejects stale first snapshot without depending on PC time', () => {
    assert.throws(() => state.decode(packet(), 20000));
    assert.equal(state.decode(packet(), 10500).pose.x, .75);
});
