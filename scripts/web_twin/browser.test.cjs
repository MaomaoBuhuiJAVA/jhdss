const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const runtime = process.env.CODEX_NODE_MODULES || 'C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const {chromium} = require(path.join(runtime, 'playwright'));
const {PNG} = require(path.join(runtime, 'pngjs'));
const base = process.env.TWIN_TEST_URL || 'http://127.0.0.1:9117/jhds';
const output = path.resolve('artifacts/blender-device/web-check');
fs.mkdirSync(output, {recursive: true});
function pixels(buffer) {
    const png = PNG.sync.read(buffer), data = png.data;
    let foreground = 0;
    for (let i = 0; i < data.length; i += 4) {
        if (Math.abs(data[i] - 32) + Math.abs(data[i+1] - 43) + Math.abs(data[i+2] - 45) > 60) foreground++;
    }
    assert.ok(foreground > png.width * png.height * .015, 'Canvas must contain visible model pixels');
    return {width: png.width, height: png.height, foreground};
}
function difference(a, b) {
    const x = PNG.sync.read(a), y = PNG.sync.read(b);
    let changed = 0;
    for (let i = 0; i < x.data.length; i += 4) if (Math.abs(x.data[i]-y.data[i])+Math.abs(x.data[i+1]-y.data[i+1])+Math.abs(x.data[i+2]-y.data[i+2]) > 18) changed++;
    return changed;
}
(async () => {
    const browser = await chromium.launch({executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
        headless: true, args: ['--enable-webgl', '--ignore-gpu-blocklist']});
    const results = [];
    try {
        for (const viewport of [{width:1600,height:1000}, {width:390,height:844}, {width:1366,height:768}]) {
            for (const name of ['dashboard', 'patrol', 'nutrient']) {
                const page = await browser.newPage({viewport});
                const errors = [], blockedWrites = [];
                let active = [], x = .25, y = .2, offline = false, requests = 0;
                page.on('pageerror', error => errors.push(error.message));
                await page.route('**/jhds/api/**', async route => {
                    const request = route.request();
                    if (!['GET', 'HEAD'].includes(request.method())) {
                        blockedWrites.push(request.url());
                        return route.fulfill({status: 403, json:{code:403,msg:'Read-only browser test'}});
                    }
                    if (new URL(request.url()).pathname.endsWith('/device-twin/status')) {
                        requests++;
                        if (offline) return route.fulfill({status:503,json:{code:503}});
                        const now = Date.now();
                        return route.fulfill({json:{code:200,data:{timestampMs:now,
                            pose:{x,y,velocityX:0,velocityY:0,xConnected:true,yConnected:true,
                                connected:true,source:'motor-time-estimate',timestampMs:now},
                            effects:Object.fromEntries(['foliar','drip','gas'].map(key=>[key,
                                {active:active.includes(key),connected:true,source:'command-response',acknowledgedAt:now-100}]))}}});
                    }
                    return route.continue();
                });
                const routeName = name === 'dashboard' ? '' : name;
                await page.goto(base + '/' + routeName, {waitUntil:'domcontentloaded'});
                const id = name + '-twin', api = name === 'patrol' ? 'greenhouseTwin' : name + 'Twin';
                await page.waitForFunction(api => window[api]?.inspect?.().loaded, api, {timeout:30000});
                const host = page.locator('#' + id), canvas = host.locator('canvas');
                await host.scrollIntoViewIfNeeded();
                await page.waitForTimeout(1200);
                const initial = await page.evaluate(api=>window[api].inspect(), api);
                assert.equal(initial.pots,12); assert.equal(initial.chains,3);
                assert.ok(Math.abs(initial.pose.x - .25) < .01);
                assert.ok(Math.abs(initial.pose.y - .2) < .01);
                const baseline = await canvas.screenshot();
                const stats = pixels(baseline);
                fs.writeFileSync(path.join(output, `${name}-${viewport.width}-idle.png`), await page.screenshot({fullPage:true}));
                active = ['foliar','drip','gas']; x = .8; y = .75;
                await page.evaluate(()=>GreenhouseTwinFeed.refresh());
                await page.waitForFunction(api => window[api].inspect().effects.gas.active
                    && window[api].inspect().pose.x > .79 && window[api].inspect().pose.y > .74, api);
                const moving = await page.evaluate(api=>window[api].inspect(), api);
                assert.ok(Math.abs((moving.nozzle[0]-initial.nozzle[0])+2.06*.55) < .03, 'Nozzle must follow carriage');
                assert.ok(Math.abs((moving.nozzle[1]-initial.nozzle[1])+1.14*.55) < .03, 'Nozzle must follow panel motion');
                const effectA = await canvas.screenshot();
                await page.waitForTimeout(350);
                const effectB = await canvas.screenshot();
                assert.ok(difference(effectA,effectB)>20, 'Water and mist pixels must animate');
                fs.writeFileSync(path.join(output, `${name}-${viewport.width}-active.png`), await page.screenshot({fullPage:true}));
                if (name === 'nutrient' && viewport.width === 1600) {
                    for (const key of ['foliar','drip','gas']) {
                        active = [key]; await page.evaluate(()=>GreenhouseTwinFeed.refresh());
                        await page.waitForFunction(({api,key})=>Object.entries(window[api].inspect().effects).every(([k,v])=>v.active===(k===key)), {api,key});
                        const a = await canvas.screenshot(); await page.waitForTimeout(400); const b = await canvas.screenshot();
                        assert.ok(difference(a,b)>5, key + ' must animate on its own');
                        fs.writeFileSync(path.join(output, `effect-${key}.png`), b);
                    }
                    await page.locator('#nutrient-twin-fullscreen').click();
                    await page.waitForFunction(()=>!!document.fullscreenElement);
                    await page.evaluate(()=>document.exitFullscreen());
                }
                if (name !== 'dashboard') {
                    const box = await canvas.boundingBox();
                    const before = await canvas.screenshot();
                    await page.mouse.move(box.x+box.width*.5,box.y+box.height*.5);
                    await page.mouse.down(); await page.mouse.move(box.x+box.width*.65,box.y+box.height*.55,{steps:10}); await page.mouse.up();
                    await page.waitForTimeout(500);
                    assert.ok(difference(before,await canvas.screenshot())>100, 'Orbit must change the view');
                    await page.locator('#' + name + '-twin-reset').click();
                }
                active = []; await page.evaluate(()=>GreenhouseTwinFeed.refresh());
                await page.waitForFunction(api=>Object.values(window[api].inspect().effects).every(e=>!e.active && e.known), api);
                offline = true; await page.evaluate(()=>GreenhouseTwinFeed.refresh());
                await page.waitForFunction(api=>Object.values(window[api].inspect().effects).every(e=>!e.active && !e.known), api);
                const bounds = await host.boundingBox();
                assert.ok(bounds.width <= viewport.width && bounds.x >= 0, 'Model must fit viewport');
                const overflow = await page.evaluate(()=>document.documentElement.scrollWidth > innerWidth+1);
                assert.equal(overflow,false,'Page must not overflow horizontally');
                results.push({name,viewport,canvas:stats,triangles:moving.triangles,requests,errors,blockedWrites});
                assert.equal(errors.length,0,'Page errors: '+errors.join('; '));
                const beforeDestroy = requests;
                await page.evaluate(api=>window[api].destroy(), api);
                await page.waitForTimeout(850);
                assert.equal(await host.locator('canvas').count(),0);
                assert.ok(requests <= beforeDestroy + 1, 'Destroyed viewer must stop polling');
                await page.close();
            }
        }
        const errorPage = await browser.newPage();
        await errorPage.route('**/inspection-gantry.glb*', route=>route.fulfill({status:404,body:'Missing model'}));
        await errorPage.goto(base+'/nutrient',{waitUntil:'domcontentloaded'});
        await errorPage.waitForSelector('#nutrient-twin[data-model-state="error"]');
        assert.match(await errorPage.locator('#nutrient-twin').innerText(),/模型加载失败/);
        await errorPage.close();
        fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(results,null,2));
        console.log(JSON.stringify(results,null,2));
    } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exitCode=1;});
