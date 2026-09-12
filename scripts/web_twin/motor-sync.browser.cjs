const assert = require('node:assert/strict');
const path = require('node:path');
const runtime = process.env.CODEX_NODE_MODULES
    || 'C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const {chromium} = require(path.join(runtime, 'playwright'));
const base = process.env.TWIN_TEST_URL || 'http://127.0.0.1:9117/jhds';

(async () => {
    const browser = await chromium.launch({
        executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
        headless: true,
        args: ['--enable-webgl', '--ignore-gpu-blocklist']
    });
    const page = await browser.newPage({viewport: {width: 1298, height: 698}});
    const errors = [];
    let x = 1, y = 0, xDirection = 'stop', yDirection = 'stop', lastUpdate = Date.now();

    function integrate() {
        const now = Date.now(), elapsed = (now - lastUpdate) / 1000;
        x = Math.max(.4, Math.min(1, x + (xDirection === 'left' ? -.055 : xDirection === 'right' ? .055 : 0) * elapsed));
        y = Math.max(0, Math.min(1, y + (yDirection === 'forward' ? .087 : yDirection === 'backward' ? -.087 : 0) * elapsed));
        lastUpdate = now;
    }

    try {
        page.on('pageerror', error => errors.push(error.message));
        await page.route('**/jhds/api/**', async route => {
            const request = route.request(), pathname = new URL(request.url()).pathname;
            integrate();
            if (pathname.endsWith('/device-twin/status')) {
                const now = Date.now();
                return route.fulfill({json: {code: 200, data: {timestampMs: now,
                    pose: {connected: true, xConnected: true, yConnected: true, x, y,
                        velocityX: xDirection === 'left' ? -.055 : xDirection === 'right' ? .055 : 0,
                        velocityY: yDirection === 'forward' ? .087 : yDirection === 'backward' ? -.087 : 0,
                        minX: .4, source: 'motor-time-estimate', timestampMs: now},
                    effects: Object.fromEntries(['foliar', 'drip', 'gas'].map(key => [key,
                        {active: false, connected: true, source: 'command-response', acknowledgedAt: now - 1}]))}}});
            }
            if (pathname.endsWith('/patrol/control')) {
                const body = request.postDataJSON();
                xDirection = body.dir || 'stop';
                return route.fulfill({json: {code: 200, data: {}}});
            }
            if (pathname.endsWith('/control-panel/move')) {
                const body = request.postDataJSON();
                yDirection = body.enabled ? body.direction : 'stop';
                return route.fulfill({json: {code: 200, data: {}}});
            }
            if (pathname.endsWith('/patrol/auto/status')) {
                return route.fulfill({json: {code: 200, data: {running: false, state: 'IDLE', phase: '等待开始', rail: {fullTravelMs: 18422, positionMs: 0}}}});
            }
            if (pathname.endsWith('/control-panel/status')) {
                return route.fulfill({json: {code: 200, data: {reachable: true, forwardActive: false, backwardActive: false}}});
            }
            if (pathname.endsWith('/camera/play-url')) {
                return route.fulfill({json: {code: 500, msg: 'Camera disabled during isolated model test', data: null}});
            }
            return route.fulfill({json: {code: 200, data: []}});
        });

        await page.goto(base + '/patrol', {waitUntil: 'domcontentloaded'});
        await page.waitForFunction(() => window.greenhouseTwin?.inspect?.().loaded, null, {timeout: 30000});
        await page.waitForTimeout(1000);
        assert.equal(await page.locator('.motor-speed-control, #motor-speed').count(), 0,
            'The motor speed control must not be rendered');
        const initial = await page.evaluate(() => window.greenhouseTwin.inspect().pose);
        const initialRig = await page.evaluate(() => window.greenhouseTwin.inspect().rigPosition);
        assert.ok(initial.x > .99, 'Camera must start at the right origin');
        assert.ok(initial.y < .01, 'Camera must start at the bottom origin');
        assert.ok(initialRig.x < -1.02 && initialRig.y > .56,
            'Physical origin must render opposite the control box at the lower gantry corner');

        async function hold(selector, duration) {
            const button = page.locator(selector);
            await button.dispatchEvent('pointerdown', {button: 0, pointerId: 1});
            await page.waitForTimeout(duration);
            await button.dispatchEvent('pointerup', {button: 0, pointerId: 1});
            await page.waitForTimeout(500);
            return page.evaluate(() => window.greenhouseTwin.inspect().pose);
        }

        const afterLeft = await hold('#btn-left', 850);
        assert.ok(afterLeft.x < initial.x - .02, 'Left motor must move the 3D gantry left');
        const afterRight = await hold('#btn-right', 850);
        assert.ok(afterRight.x > afterLeft.x + .02, 'Right motor must move the 3D gantry right');
        const afterForward = await hold('#btn-panel-forward', 850);
        assert.ok(afterForward.y > afterRight.y + .03, 'Forward motor must move the 3D carriage up');
        const afterBackward = await hold('#btn-panel-backward', 850);
        assert.ok(afterBackward.y < afterForward.y - .03, 'Backward motor must move the 3D carriage down');

        await page.locator('#btn-right').click();
        await page.waitForTimeout(500);
        assert.equal(xDirection, 'stop', 'A short click must release instead of latching rail movement');

        const stopped = await page.evaluate(() => window.greenhouseTwin.inspect().pose);
        await page.waitForTimeout(500);
        const stillStopped = await page.evaluate(() => window.greenhouseTwin.inspect().pose);
        assert.ok(Math.abs(stillStopped.x - stopped.x) < .005 && Math.abs(stillStopped.y - stopped.y) < .005,
            '3D carriage must stop after button release');
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false);
        assert.deepEqual(errors, []);
        console.log(JSON.stringify({initial, initialRig, afterLeft, afterRight, afterForward, afterBackward, stopped: stillStopped}, null, 2));
    } finally {
        await browser.close();
    }
})().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
