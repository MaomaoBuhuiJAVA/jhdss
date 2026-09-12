const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const runtime=process.env.CODEX_NODE_MODULES||'C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const {chromium}=require(path.join(runtime,'playwright'));
const {PNG}=require(path.join(runtime,'pngjs'));
const base=process.env.TWIN_TEST_URL||'http://127.0.0.1:9117/jhds';
const output=path.resolve('artifacts/logistics');fs.mkdirSync(output,{recursive:true});
(async()=>{
    const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--enable-webgl','--ignore-gpu-blocklist']});
    const results=[];
    try{
        for(const viewport of [{width:1440,height:1000},{width:390,height:844}]){
            const page=await browser.newPage({viewport});page.setDefaultTimeout(10000);
            const errors=[],writes=[];page.on('pageerror',e=>errors.push(e.message));
            await page.route('**/api/**',route=>{
                if(!['GET','HEAD'].includes(route.request().method())){writes.push(route.request().url());return route.fulfill({status:403,body:'Read-only test'});}
                return route.continue();
            });
            // Expose the renderer only in this isolated test browser, never in the app.
            await page.route('**/js/logistics-map.js*',async route=>{
                const response=await route.fetch();let source=await response.text();
                source=source.replace('host.prepend(renderer.domElement);','window.__labelTestRenderer=renderer;host.prepend(renderer.domElement);');
                if(process.env.LABEL_TEST_THROTTLED)source=source.replace('camera.updateMatrixWorld();updateLabels();','camera.updateMatrixWorld();if(now-lastTelemetry>120)updateLabels();');
                await route.fulfill({response,body:source});
            });
            await page.goto(base+'/after-sales',{waitUntil:'domcontentloaded'});
            await page.waitForSelector('#logistics-map-3d[data-state=ready]',{timeout:30000});
            const host=page.locator('#logistics-map-3d');await host.scrollIntoViewIfNeeded();
            await page.evaluate(()=>window.logisticsMap3D.setPaused(true));
            await page.evaluate(()=>{
                const {THREE}=window.Greenhouse3D,host=document.querySelector('#logistics-map-3d');
                const width=host.clientWidth,height=host.clientHeight,anchors=new Map(),sample=new THREE.Vector3();
                const renderer=window.__labelTestRenderer,original=renderer.render;
                const stats={frames:0,movingFrames:0,mismatchedFrames:0,checked:0,maxError:0,overlaps:0};
                let lastCamera=null;
                renderer.render=function(scene,camera){
                    original.call(this,scene,camera);
                    if(!anchors.size)scene.traverse(o=>{if(o.userData.kind==='city')anchors.set(o.userData.id,o.getWorldPosition(new THREE.Vector3()).add(new THREE.Vector3(0,0,.62)));});
                    stats.frames++;
                    const signature=camera.matrixWorld.elements.join(',');
                    if(lastCamera!==null&&signature!==lastCamera)stats.movingFrames++;
                    lastCamera=signature;
                    let frameError=0;const boxes=[];
                    for(const label of host.querySelectorAll('.logistics-city-labels button:not([hidden])')){
                        const matrix=new DOMMatrixReadOnly(label.style.transform),w=parseFloat(label.style.width);
                        const box={x:matrix.m41,y:matrix.m42,w,h:22};
                        if(boxes.some(b=>box.x<b.x+b.w-.1&&box.x+box.w>b.x+.1&&box.y<b.y+b.h-.1&&box.y+box.h>b.y+.1))stats.overlaps++;
                        boxes.push(box);
                        const anchor=anchors.get(label.dataset.city);if(!anchor)continue;
                        sample.copy(anchor).project(camera);
                        const expectedX=(sample.x*.5+.5)*width-w/2,expectedY=(-sample.y*.5+.5)*height-11;
                        const error=Math.hypot(matrix.m41-expectedX,matrix.m42-expectedY);
                        frameError=Math.max(frameError,error);stats.checked++;
                    }
                    stats.maxError=Math.max(stats.maxError,frameError);if(frameError>.05)stats.mismatchedFrames++;
                };
                window.labelMotionStats=stats;
            });
            const box=await host.boundingBox(),x=box.x+box.width*.5,y=box.y+box.height*.55;
            await page.mouse.move(x,y);
            for(let i=0;i<10;i++){await page.mouse.wheel(0,-45);await page.waitForTimeout(35);}
            await page.mouse.move(x,y);await page.mouse.down();
            for(let i=1;i<=18;i++){await page.mouse.move(x+Math.min(160,box.width*.25)*i/18,y+40*i/18);await page.waitForTimeout(20);}
            await page.mouse.up();await page.waitForTimeout(400);
            const stats=await page.evaluate(()=>window.labelMotionStats);
            assert.ok(stats.movingFrames>15,'Exercise continuous camera motion');
            assert.ok(stats.checked>150,'Check projected label anchors throughout motion');
            assert.equal(stats.mismatchedFrames,0,'Labels must use the same-frame camera pose');
            assert.ok(stats.maxError<.05,'No stale projection or integer-pixel stepping');
            assert.equal(stats.overlaps,0,'Labels must retain collision avoidance');
            assert.equal(await page.locator('.logistics-city-labels button').first().evaluate(e=>getComputedStyle(e).transitionDuration),'0s');
            await host.screenshot({path:path.join(output,'labels-motion-'+viewport.width+'.png')});
            const screenshot=await host.locator('canvas').screenshot();
            const png=PNG.sync.read(screenshot);let foreground=0;
            for(let i=0;i<png.data.length;i+=4)if(Math.abs(png.data[i]-20)+Math.abs(png.data[i+1]-39)+Math.abs(png.data[i+2]-45)>75)foreground++;
            assert.ok(foreground>png.width*png.height*.02);
            await page.evaluate(()=>window.logisticsMap3D.fit());await page.waitForTimeout(300);
            const city=page.locator('.logistics-city-labels button[data-city]:not([hidden])').first(),name=await city.innerText();
            await city.click();assert.ok((await page.locator('#logistics-selected-title').innerText()).includes(name));
            await page.evaluate(()=>window.logisticsMap3D.setLayer('labels',false));
            assert.equal(await page.locator('.logistics-city-labels button:not([hidden])').count(),0);
            await page.evaluate(()=>window.logisticsMap3D.setLayer('labels',true));
            assert.ok(await page.locator('.logistics-city-labels button:not([hidden])').count()>0);
            assert.deepEqual(errors,[]);assert.deepEqual(writes,[]);
            results.push({viewport,...stats,foreground});await page.close();
        }
        fs.writeFileSync(path.join(output,'labels-motion-report.json'),JSON.stringify(results,null,2));console.log(JSON.stringify(results,null,2));
    }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
