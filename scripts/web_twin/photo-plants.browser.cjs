const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const runtime = process.env.CODEX_NODE_MODULES || 'C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const {chromium} = require(path.join(runtime, 'playwright'));
const {PNG} = require(path.join(runtime, 'pngjs'));
const base = process.env.TWIN_TEST_URL || 'http://127.0.0.1:9117/jhds';
const output = path.resolve('artifacts/blender-device/revisions/new-plants/browser');
fs.mkdirSync(output, {recursive: true});
function pixels(buffer) {
    const png = PNG.sync.read(buffer);
    let green = 0, bright = 0;
    for (let i = 0; i < png.data.length; i += 4) {
        const [r,g,b] = png.data.subarray(i, i+3);
        if (g > r*1.13 && g > b*1.17 && g > 25) green++;
        if (r+g+b > 270) bright++;
    }
    assert.ok(green > 40, 'New foliage must render colored pixels');
    assert.ok(bright > 150, 'Equipment must remain visible');
    return {width:png.width, height:png.height, green, bright};
}
function diff(a,b) {
    const x = PNG.sync.read(a), y = PNG.sync.read(b);
    let count = 0;
    for (let i = 0; i < x.data.length; i+=4) {
        if (Math.abs(x.data[i]-y.data[i])+Math.abs(x.data[i+1]-y.data[i+1])+Math.abs(x.data[i+2]-y.data[i+2]) > 20) count++;
    }
    return count;
}
(async()=>{
    const browser = await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',
        headless:true, args:['--enable-webgl','--ignore-gpu-blocklist']});
    const results = [];
    try {
        for (const viewport of [{width:1600,height:1000},{width:390,height:844}]) {
            for (const view of ['nutrient','dashboard','patrol']) {
                console.log('CHECK',view,viewport.width);
                const page = await browser.newPage({viewport});
                const errors = [], blockedWrites = [];
                let active = false, x = .3, y = .25;
                page.on('pageerror',error=>errors.push(error.message));
                await page.addInitScript(()=>{
                    window.__plantRenderTimes=[];
                    const draw = WebGL2RenderingContext.prototype.drawElements;
                    WebGL2RenderingContext.prototype.drawElements = function(...args) {
                        const now = performance.now(), times = window.__plantRenderTimes;
                        if (this.canvas.closest('[data-model-state]') && (!times.length || now-times[times.length-1]>12)) times.push(now);
                        return draw.apply(this,args);
                    };
                });
                await page.route('**/jhds/api/**', async route=>{
                    const req=route.request();
                    if (!['GET','HEAD'].includes(req.method())) {
                        blockedWrites.push(req.url());
                        return route.fulfill({status:403,json:{code:403,msg:'Read-only model verification'}});
                    }
                    if (new URL(req.url()).pathname.endsWith('/device-twin/status')) {
                        const timestampMs=Date.now();
                        return route.fulfill({json:{code:200,data:{timestampMs,
                            pose:{x,y,velocityX:0,velocityY:0,xConnected:true,yConnected:true,connected:true,source:'test-fixture',timestampMs},
                            effects:Object.fromEntries(['foliar','drip','gas'].map(key=>[key,{active,connected:true,source:'command-response',acknowledgedAt:timestampMs-100}]))}}});
                    }
                    return route.continue();
                });
                if (process.argv.includes('--staged')) {
                    await page.route('**/models/inspection-gantry.glb*',route=>route.fulfill({path:path.join(output,'../web/inspection-gantry.glb'),contentType:'model/gltf-binary'}));
                    await page.route('**/models/inspection-gantry.json*',route=>route.fulfill({path:path.join(output,'../web/inspection-gantry.json'),contentType:'application/json'}));
                }
                await page.goto(base+'/'+(view==='dashboard'?'':view),{waitUntil:'domcontentloaded'});
                const api=view==='patrol'?'greenhouseTwin':view+'Twin';
                await page.waitForFunction(api=>window[api]?.inspect?.().loaded,api,{timeout:45000});
                const host=page.locator('#'+view+'-twin'), canvas=host.locator('canvas');
                await host.scrollIntoViewIfNeeded();
                await page.waitForTimeout(1300);
                const initial=await page.evaluate(api=>window[api].inspect(),api);
                assert.equal(initial.pots,12); assert.equal(initial.chains,3);
                assert.ok(initial.triangles<750000,'Web geometry budget exceeded');
                const baseline=await canvas.screenshot(), pixelStats=pixels(baseline);
                await page.screenshot({path:path.join(output,`${view}-${viewport.width}.png`),fullPage:true});
                fs.writeFileSync(path.join(output,`${view}-${viewport.width}-model.png`),baseline);
                active=true; x=.78; y=.71;
                await page.evaluate(()=>GreenhouseTwinFeed.refresh());
                await page.waitForFunction(api=>window[api].inspect().pose.x>.77 && window[api].inspect().effects.gas.active,api);
                const moving=await page.evaluate(api=>window[api].inspect(),api);
                assert.ok(Math.abs((moving.nozzle[0]-initial.nozzle[0])+2.06*.48)<.04);
                assert.ok(Math.abs((moving.nozzle[1]-initial.nozzle[1])+1.14*.46)<.04);
                const a=await canvas.screenshot();
                await page.waitForTimeout(450);
                const b=await canvas.screenshot();
                assert.ok(diff(a,b)>15,'Existing water and mist must remain animated');
                if (view==='nutrient') {
                    const box=await canvas.boundingBox();
                    await page.mouse.move(box.x+box.width*.45,box.y+box.height*.5);
                    await page.mouse.down();
                    await page.mouse.move(box.x+box.width*.65,box.y+box.height*.57,{steps:12});
                    await page.mouse.up();
                    await page.waitForTimeout(500);
                    assert.ok(diff(b,await canvas.screenshot())>100,'Model orbit must work');
                    await page.locator('#nutrient-twin-reset').click();
                }
                const metrics=await page.evaluate(()=>{
                    const times=window.__plantRenderTimes.filter(t=>t>performance.now()-2000);
                    return {sampledFps:times.length>1?Math.round((times.length-1)*1000/(times[times.length-1]-times[0])):0,
                        horizontalOverflow:document.documentElement.scrollWidth>innerWidth+1};
                });
                assert.equal(metrics.horizontalOverflow,false);
                assert.deepEqual(errors,[]);
                results.push({view,viewport,pixels:pixelStats,drawCalls:initial.drawCalls,triangles:initial.triangles,
                    metrics,simulatedMotionAndEffects:true,blockedWrites,errors});
                await page.close();
            }
        }
        fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify(results,null,2));
        console.log(JSON.stringify(results,null,2));
    } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
