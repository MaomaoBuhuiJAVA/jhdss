const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const runtime=process.env.CODEX_NODE_MODULES||'C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const {chromium}=require(path.join(runtime,'playwright'));
const {PNG}=require(path.join(runtime,'pngjs'));
const base=process.env.TWIN_TEST_URL||'http://127.0.0.1:9117/jhds';
const output=path.resolve('artifacts/logistics');fs.mkdirSync(output,{recursive:true});
function pixelCount(buffer){
    const png=PNG.sync.read(buffer);let count=0;
    for(let i=0;i<png.data.length;i+=4) if(Math.abs(png.data[i]-20)+Math.abs(png.data[i+1]-39)+Math.abs(png.data[i+2]-45)>75)count++;
    assert.ok(count>png.width*png.height*.03,'Visible textured map must fill the canvas');
    return {width:png.width,height:png.height,foreground:count};
}
(async()=>{
    const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--enable-webgl','--ignore-gpu-blocklist']});
    const results=[];
    try{
        for(const viewport of [{width:1440,height:1000},{width:1366,height:768},{width:1920,height:1080},{width:1024,height:900},{width:390,height:844}]){
            const page=await browser.newPage({viewport});page.setDefaultTimeout(10000);
            const errors=[],writes=[];page.on('pageerror',e=>errors.push(e.message));
            await page.route('**/api/**',route=>{if(!['GET','HEAD'].includes(route.request().method())){writes.push(route.request().url());return route.fulfill({status:403,body:'Read-only test'});}return route.continue();});
            const start=Date.now();await page.goto(base+'/after-sales',{waitUntil:'domcontentloaded'});
            await page.waitForSelector('#logistics-map-3d[data-state=ready]',{timeout:30000});
            const loadMs=Date.now()-start,host=page.locator('#logistics-map-3d');await host.scrollIntoViewIfNeeded();
            await page.waitForTimeout(500);await page.evaluate(()=>window.logisticsMap3D.setPaused(true));
            const canvas=host.locator('canvas'),pixels=pixelCount(await canvas.screenshot());
            const layout=await page.evaluate(()=>{
                const rect=s=>document.querySelector(s).getBoundingClientRect().toJSON();
                return {map:rect('#logistics-map-3d'),board:rect('#logistics-workbench'),left:rect('.logistics-overview'),right:rect('.logistics-dispatch'),footer:rect('.logistics-board-footer')};
            });
            if(viewport.width>1100){
                assert.ok(layout.map.width/layout.board.width>.67&&layout.map.width/layout.board.width<.72,'Map should occupy approximately 70% of the desktop workspace');
                assert.ok(layout.left.right<=layout.map.left&&layout.right.left>=layout.map.right,'Sidebars must not overlap the map');
                assert.ok(layout.footer.bottom<=viewport.height,'Desktop playback controls must be visible in the first viewport');
            }else{
                assert.ok(layout.footer.top>=layout.map.bottom-1,'Playback must follow the map on small screens');
                assert.ok(layout.right.top>=layout.footer.bottom-1&&layout.left.top>=layout.footer.bottom-1);
            }
            assert.equal(await page.locator('.logistics-overview .sales-metrics article').count(),5);
            assert.equal(await page.locator('.sales-metrics').count(),1);
            assert.equal(await page.locator('.logistics-destination').count(),4);
            assert.match(await page.locator('#logistics-cold-summary').innerText(),/819/);
            const before=await page.evaluate(()=>window.logisticsMap3D.inspect());
            await page.waitForTimeout(1000);
            assert.equal((await page.evaluate(()=>window.logisticsMap3D.state('WL-0912-031'))).progress,before.vehicles[0].progress);
            await page.evaluate(()=>window.logisticsMap3D.setPaused(false));await page.waitForTimeout(1000);
            const after=await page.evaluate(()=>window.logisticsMap3D.inspect());
            assert.ok(after.vehicles[0].progress>before.vehicles[0].progress);
            assert.equal(after.vehicles[4].progress,1);assert.equal(after.vehicles[7].progress,.33);
            assert.ok(after.drawCalls<160);assert.ok(after.triangles<150000);
            assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
            await page.screenshot({path:path.join(output,'desktop-'+viewport.width+'.png')});
            if(viewport.width===1440){
                await page.locator('.logistics-risk-link').first().click();
                assert.equal(await page.locator('.logistics-order[aria-pressed=true]').getAttribute('data-order'),'WL-0912-067');
                await page.locator('.logistics-destination').first().click();
                assert.match(await page.locator('#logistics-selected-title').innerText(),/西安/);
                await page.locator('.logistics-order[data-order="WL-0912-031"]').click();
                await page.locator('#logistics-toggle-play').click();
                await page.locator('#logistics-focus').click();await page.waitForTimeout(200);
                await page.screenshot({path:path.join(output,'vehicle-detail.png')});
                const point=await page.evaluate(()=>window.logisticsMap3D.screenPoint('WL-0912-031'));
                await page.mouse.click(point.x,point.y);
                assert.equal(await page.locator('.logistics-order[aria-pressed=true]').getAttribute('data-order'),'WL-0912-031');
                await page.locator('#logistics-focus').click();
                await page.locator('#logistics-toggle-play').click();await page.waitForTimeout(500);
                const follow=await page.evaluate(()=>window.logisticsMap3D.inspect());
                assert.equal(follow.following,'WL-0912-031');
                assert.ok(Math.hypot(...follow.cameraTarget.map((n,i)=>n-follow.vehicles[0].position[i]))<1e-6);
                await page.locator('#logistics-map-reset').click();
                await page.locator('[data-layer=vehicles]').uncheck();
                assert.equal(await page.evaluate(()=>window.logisticsMap3D.inspect().layers.vehicles),false);
                await page.locator('[data-layer=vehicles]').check();
                await page.locator('#logistics-search').fill('哈尔滨');assert.equal(await page.locator('.logistics-order').count(),1);
                assert.equal((await page.evaluate(()=>window.logisticsMap3D.inspect())).vehicles.filter(v=>v.visible).length,1);
                await page.locator('#logistics-search').fill('');
                await page.locator('#logistics-state-filter').selectOption('alert');assert.equal(await page.locator('.logistics-order').count(),1);
                await page.locator('#logistics-state-filter').selectOption('');
                await page.locator('#logistics-edit').click();
                const routeBefore=await page.locator('.logistics-via-row select').evaluateAll(els=>els.map(e=>e.value));
                assert.deepEqual(routeBefore,['nanjing','wuhan','chongqing']);
                await page.locator('#logistics-order-form button[type=submit]').click();
                await page.locator('#logistics-add').click();
                await page.locator('[name=id]').fill('TEST-ORDER');await page.locator('[name=vehicle]').fill('TEST-TRUCK');
                await page.locator('.logistics-via-add').click();
                await page.locator('.logistics-via-row select').selectOption('jinan');
                await page.locator('#logistics-order-form button[type=submit]').click();
                assert.equal(await page.locator('.logistics-order').count(),9);
                await page.reload({waitUntil:'domcontentloaded'});await page.waitForSelector('#logistics-map-3d[data-state=ready]');
                assert.equal(await page.locator('.logistics-order').count(),9);
                const downloaded=page.waitForEvent('download');await page.locator('#logistics-download').click();
                const download=await downloaded;const exported=JSON.parse(fs.readFileSync(await download.path(),'utf8'));
                assert.equal(exported.length,9);
                await page.locator('#logistics-json-file').setInputFiles({name:'invalid.json',mimeType:'application/json',buffer:Buffer.from('[{"id":"bad"}]')});
                assert.equal(await page.locator('.logistics-order').count(),9);
                await page.locator('#logistics-json-file').setInputFiles({name:'empty.json',mimeType:'application/json',buffer:Buffer.from('[]')});
                await page.waitForFunction(()=>document.querySelectorAll('.logistics-order').length===0);
                assert.equal(await page.locator('.logistics-order').count(),0);
                assert.equal(await page.locator('.logistics-destination').count(),0);
                assert.match(await page.locator('#logistics-cold-summary').innerText(),/0 件/);
                await page.locator('#logistics-json-file').setInputFiles({name:'restore.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(exported))});
                await page.waitForFunction(()=>document.querySelectorAll('.logistics-order').length===9);
                await page.locator('.logistics-order[data-order=TEST-ORDER]').click();await page.locator('#logistics-edit').click();await page.locator('#logistics-delete').click();
                assert.equal(await page.locator('.logistics-order').count(),8);
                await page.locator('#open-logistics').click();
                assert.equal(await page.locator('#logistics-overlay #logistics-workbench').count(),1);
                assert.equal(await page.locator('#logistics-overlay canvas').count(),1);
                await page.locator('#close-logistics').click();assert.equal(await page.locator('#logistics-main-dock canvas').count(),1);
                assert.equal(await page.locator('#logistics-main-dock .sales-metrics').count(),1);
                await page.locator('button[aria-label="俯视地图"]').click();
                pixelCount(await canvas.screenshot());
                await page.evaluate(()=>{const d=window.LogisticsData;window.logisticsMap3D.setJobs(d.validate(Array.from({length:40},(_,i)=>({...d.defaults[i%8],id:'STRESS-'+i,vehicle:'V-'+i}))));});
                await page.waitForTimeout(1000);
                const stress=await page.evaluate(()=>window.logisticsMap3D.inspect());
                assert.equal(stress.routes,40);assert.ok(stress.drawCalls<200);assert.ok(stress.triangles<250000);
                await page.evaluate(()=>window.logisticsMap3D.setJobs(window.LogisticsData.load().jobs));
                await host.evaluate(e=>e.style.display='none');await page.waitForTimeout(200);
                const hiddenA=await page.evaluate(()=>window.logisticsMap3D.inspect());await page.waitForTimeout(250);
                const hiddenB=await page.evaluate(()=>window.logisticsMap3D.inspect());
                assert.equal(hiddenA.renderCount,hiddenB.renderCount);assert.equal(hiddenA.elapsed,hiddenB.elapsed);
                await host.evaluate(e=>e.style.removeProperty('display'));
            }
            assert.deepEqual(errors,[]);assert.deepEqual(writes,[]);
            results.push({viewport,loadMs,pixels,drawCalls:after.drawCalls,triangles:after.triangles,renderedFramesPerSecond:Math.round(10000*(after.renderCount-before.renderCount)/(after.timestampMs-before.timestampMs))/10});
            await page.close();
        }
        fs.writeFileSync(path.join(output,'browser-report.json'),JSON.stringify(results,null,2));console.log(JSON.stringify(results,null,2));
    }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
