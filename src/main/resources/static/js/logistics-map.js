(function () {
    'use strict';
    const host=document.getElementById('logistics-map-3d');
    if(!host) return;
    const base=new URL('../',document.currentScript.src);
    const loading=host.querySelector('.logistics-map-loading');
    window.createLogisticsMap=function(options) {
        const {THREE,GLTFLoader,OrbitControls}=window.Greenhouse3D;
        const {cities,states}=window.LogisticsData;
        let destroyed=false,loaded=false,visible=false,paused=false,elapsed=0,rate=1,last=performance.now(),lastDraw=0,frame,renderCount=0;
        let selected=null,following=null,model,routeViews=[],cityViews=[],heatViews=[],lastTelemetry=0;
        let viewportWidth=0,viewportHeight=0,labelOrder=[],labelOrderDirty=true;
        const labelBoxes=[];
        const layers={routes:true,vehicles:true,cities:true,hotspots:true,labels:true};
        const filter={query:'',status:''};
        const resources={geometries:new Set(),materials:new Set(),textures:new Set()};
        const scene=new THREE.Scene();scene.background=new THREE.Color(0x14272d);
        const camera=new THREE.PerspectiveCamera(36,1,.15,400);camera.up.set(0,0,1);
        const renderer=new THREE.WebGLRenderer({antialias:true,alpha:false,powerPreference:'high-performance'});
        renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.5));renderer.outputColorSpace=THREE.SRGBColorSpace;
        renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.1;
        host.prepend(renderer.domElement);renderer.domElement.setAttribute('aria-label','中国物流三维地图');
        const controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;
        controls.addEventListener('start',()=>{following=null;});
        controls.minDistance=3;controls.maxDistance=180;controls.maxPolarAngle=Math.PI*.46;
        const hemi=new THREE.HemisphereLight(0xe7f5ff,0x71807a,2.5);hemi.position.set(0,0,50);scene.add(hemi);
        const sun=new THREE.DirectionalLight(0xfff4e4,2.0);sun.position.set(-12,-16,40);scene.add(sun);
        const dataRoot=new THREE.Group();scene.add(dataRoot);
        const labelRoot=document.createElement('div');labelRoot.className='logistics-city-labels';host.append(labelRoot);
        const vehicleLabel=document.createElement('button');vehicleLabel.className='logistics-vehicle-label';vehicleLabel.hidden=true;labelRoot.append(vehicleLabel);
        const vehicleLabelView={label:vehicleLabel,rect:{x:0,y:0,w:0,h:22},transform:''};
        vehicleLabel.addEventListener('click',()=>{if(selected)focus(selected);});
        const ray=new THREE.Raycaster(),pointer=new THREE.Vector2();
        const scratch=new THREE.Object3D(),projection=new THREE.Vector3(),matrix=new THREE.Matrix4();
        let trucks=[],bounds;
        const heightCache=new Map();
        const meshes=[];
        function geometry(g){resources.geometries.add(g);return g;}
        function material(m){resources.materials.add(m);return m;}
        const lightMaterial=material(new THREE.MeshStandardMaterial({color:0xe6f0ee,roughness:.48,metalness:.15}));
        const darkMaterial=material(new THREE.MeshStandardMaterial({color:0x172528,roughness:.65}));
        const glassMaterial=material(new THREE.MeshStandardMaterial({color:0x176c8a,roughness:.2,metalness:.5}));
        const rimMaterial=material(new THREE.MeshStandardMaterial({color:0xa3b2b8,roughness:.3,metalness:.6}));
        const lampMaterial=material(new THREE.MeshBasicMaterial({color:0xffebbf}));
        const rearMaterial=material(new THREE.MeshBasicMaterial({color:0xff5446}));
        const stripeMaterial=material(new THREE.MeshStandardMaterial({color:0x1a927e,roughness:.45}));
        const cube=geometry(new THREE.BoxGeometry(1,1,1));
        // CylinderGeometry is aligned to Y by default; the truck axle runs across Y.
        const tire=geometry(new THREE.CylinderGeometry(.083,.083,.066,10));
        const rim=geometry(new THREE.CylinderGeometry(.036,.036,.071,10));
        const truckParts=[
            [cube,darkMaterial,[-.015,0,.15],[.82,.27,.06]],
            [cube,lightMaterial,[-.13,0,.34],[.59,.31,.33]],
            [cube,lightMaterial,[.30,0,.27],[.23,.30,.28]],
            [cube,glassMaterial,[.365,0,.36],[.105,.305,.1]],
            [cube,lightMaterial,[.27,0,.44],[.25,.31,.025]],
            [cube,darkMaterial,[.426,0,.225],[.015,.22,.065]],
            [cube,lightMaterial,[-.135,0,.515],[.61,.32,.024]],
            ...[-.157,.157].map(y=>[cube,stripeMaterial,[-.13,y,.32],[.58,.004,.04]]),
            [cube,rimMaterial,[-.437,0,.34],[.014,.025,.29]],
            [cube,rimMaterial,[-.44,-.117,.34],[.015,.009,.23]],
            [cube,rimMaterial,[-.44,.117,.34],[.015,.009,.23]],
            [cube,lightMaterial,[.157,0,.53],[.075,.20,.08]],
            [cube,darkMaterial,[.19,0,.54],[.01,.145,.043]],
            ...[-.12,.12].map(y=>[cube,lampMaterial,[.433,y,.25],[.015,.046,.026]]),
            ...[-.11,.11].map(y=>[cube,rearMaterial,[-.445,y,.22],[.012,.047,.025]]),
            ...[-.28,.28].flatMap(x=>[-.175,.175].flatMap(y=>[
                [tire,darkMaterial,[x,y,.10],[1,1,1]], [rim,rimMaterial,[x,y,.10],[1,1,1]]]))
        ];
        function createTruckInstances(count) {
            trucks.forEach(o=>{dataRoot.remove(o);o.dispose();});
            trucks=truckParts.map(([geo,mat,p,s])=>{
                const mesh=new THREE.InstancedMesh(geo,mat,count);mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
                mesh.frustumCulled=false;mesh.userData.kind='truck';dataRoot.add(mesh);
                scratch.position.fromArray(p);scratch.scale.fromArray(s);scratch.rotation.set(0,0,0);scratch.updateMatrix();
                mesh.userData.partMatrix=scratch.matrix.clone();return mesh;
            });
        }
        function surface(x,y){
            const key=x.toFixed(2)+','+y.toFixed(2);if(heightCache.has(key))return heightCache.get(key);
            ray.set(new THREE.Vector3(x,y,12),new THREE.Vector3(0,0,-1));
            const hit=ray.intersectObjects(meshes,false)[0];const z=hit?hit.point.z:bounds.max.z;
            heightCache.set(key,z);return z;
        }
        function cityPosition(id){const c=cities.find(c=>c.id===id);return new THREE.Vector3(c.x,c.y,surface(c.x,c.y)+.16);}
        function makeRoute(job,index){
            const controls=[job.from,...job.via,job.to].map(cityPosition);
            const plan=new THREE.CatmullRomCurve3(controls,false,'centripetal');
            const raw=plan.getPoints(Math.max(48,controls.length*18));
            // Sample the supplied terrain once, then reuse cumulative distance.
            raw.forEach(p=>p.z=surface(p.x,p.y)+.18);
            for(let pass=0;pass<2;pass++)for(let i=1;i<raw.length-1;i++)raw[i].z=Math.max(raw[i].z,(raw[i-1].z+raw[i+1].z)/2);
            const path=new THREE.CurvePath();for(let i=1;i<raw.length;i++)path.add(new THREE.LineCurve3(raw[i-1],raw[i]));
            const color=states[job.status].color;
            const tube=new THREE.Mesh(new THREE.TubeGeometry(path,raw.length*2,.040,5,false),new THREE.MeshBasicMaterial({color,transparent:true,opacity:.32}));
            tube.userData={kind:'route',id:job.id};dataRoot.add(tube);
            const trail=new THREE.Line(new THREE.BufferGeometry().setFromPoints(path.getSpacedPoints(160)),new THREE.LineBasicMaterial({color}));
            trail.userData={kind:'route',id:job.id};dataRoot.add(trail);
            const length=path.getLength();
            return {job,index,path,tube,trail,length,progress:job.progress,position:new THREE.Vector3(),heading:0,visible:true};
        }
        function labelFor(city){
            const button=document.createElement('button');button.type='button';button.textContent=city.name;button.dataset.city=city.id;
            button.addEventListener('click',()=>options.onCity(city.id));labelRoot.append(button);return button;
        }
        function addCities(){
            const pinGeo=geometry(new THREE.CylinderGeometry(.10,.10,.10,12));pinGeo.rotateX(Math.PI/2);
            const poleGeo=geometry(new THREE.CylinderGeometry(.018,.018,.40,6));poleGeo.rotateX(Math.PI/2);
            const hubMat=material(new THREE.MeshBasicMaterial({color:0xf9d981}));
            cityViews=cities.map(city=>{
                const p=cityPosition(city.id),group=new THREE.Group();group.position.copy(p);
                const pin=new THREE.Mesh(pinGeo,hubMat);pin.position.z=.43;group.add(pin);
                const pole=new THREE.Mesh(poleGeo,hubMat);pole.position.z=.20;group.add(pole);
                group.userData={kind:'city',id:city.id};dataRoot.add(group);
                const label=labelFor(city),rect={x:0,y:0,w:city.name.length*12+15,h:22};label.style.width=rect.w+'px';label.hidden=true;
                return {city,group,label,position:p,rect,transform:''};
            });
            labelOrderDirty=true;
            const heatGeo=geometry(new THREE.CylinderGeometry(.33,.33,1,6));heatGeo.rotateX(Math.PI/2);
            const ringGeo=geometry(new THREE.RingGeometry(.70,.76,48));
            heatViews=['shanghai','guangzhou','wuhan','beijing','chengdu'].map((id,i)=>{
                const p=cityPosition(id);const group=new THREE.Group();group.position.copy(p);group.position.x-=.60;group.position.y-=.35;
                const mat=material(new THREE.MeshBasicMaterial({color:i===0?0xff6978:0xffbe58,transparent:true,opacity:.16,depthWrite:false}));
                const bar=new THREE.Mesh(heatGeo,mat);group.add(bar);
                const ring=new THREE.Mesh(ringGeo,material(new THREE.MeshBasicMaterial({color:mat.color,transparent:true,opacity:.65,side:THREE.DoubleSide})));ring.position.z=.04;group.add(ring);
                group.userData={kind:'hotspot',id};dataRoot.add(group);return {id,group,bar,ring};
            });
        }
        function matches(j){const q=filter.query.toLowerCase();return (!filter.status||filter.status===j.status)&&(!q||[j.id,j.vehicle,...[j.from,...j.via,j.to].map(id=>cities.find(c=>c.id===id).name)].join(' ').toLowerCase().includes(q));}
        function setJobs(jobs){
            routeViews.forEach(v=>{for(const o of[v.tube,v.trail]){dataRoot.remove(o);o.geometry.dispose();o.material.dispose();}});
            routeViews=jobs.map(makeRoute);createTruckInstances(jobs.length);elapsed=0;
            heatViews.forEach(h=>{const count=jobs.filter(j=>[j.from,...j.via,j.to].includes(h.id)).reduce((n,j)=>n+j.parcels,0);h.count=count;const height=.55+Math.min(2.5,count/150);h.bar.scale.z=height;h.bar.position.z=height/2;});
            if(heightCache.size>20000)heightCache.clear();
            following=null;labelOrderDirty=true;applyFilter();updateVehicles();options.onReady?.();
        }
        function applyFilter(){routeViews.forEach(v=>{v.visible=matches(v.job);v.tube.visible=v.visible&&layers.routes;v.trail.visible=v.visible&&layers.routes;});}
        function updateVehicles(){
            const period=elapsed;
            routeViews.forEach((v,i)=>{
                const moving=['transit','alert','delayed'].includes(v.job.status)&&v.job.speed>0;
                v.progress=moving?Math.min(1,v.job.progress+period*v.job.speed/(Math.max(v.length,2)*170)):v.job.progress;
                v.position.copy(v.path.getPointAt(v.progress));v.position.z+=.04;
                const tangent=v.path.getTangentAt(Math.min(.9999,v.progress));v.heading=Math.atan2(tangent.y,tangent.x);
                scratch.position.copy(v.position);scratch.rotation.set(0,0,v.heading);
                const scale=v.visible&&layers.vehicles?1.35:0;scratch.scale.setScalar(scale);scratch.updateMatrix();
                trucks.forEach(mesh=>{matrix.multiplyMatrices(scratch.matrix,mesh.userData.partMatrix);mesh.setMatrixAt(i,matrix);});
                v.trail.geometry.setDrawRange(0,Math.max(2,Math.ceil(v.progress*161)));
            });
            trucks.forEach(mesh=>mesh.instanceMatrix.needsUpdate=true);
        }
        function fit(top=false){
            following=null;
            resize();if(!bounds)return;
            const center=bounds.getCenter(new THREE.Vector3());center.z=0;
            const size=bounds.getSize(new THREE.Vector3()),tan=Math.tan(THREE.MathUtils.degToRad(camera.fov/2));
            const distance=Math.max(size.x/camera.aspect,size.y)*.62/tan;
            controls.target.copy(center);camera.position.copy(center).add(new THREE.Vector3(0,top?-.01:-.48,1).normalize().multiplyScalar(distance));controls.update();
        }
        function focus(id){const v=routeViews.find(v=>v.job.id===id);if(!v)return;select(id);following=id;controls.target.copy(v.position);camera.position.copy(v.position).add(new THREE.Vector3(0,-5,8));controls.update();}
        function select(id){if(selected!==id){following=null;labelOrderDirty=true;}selected=id;routeViews.forEach(v=>{v.tube.material.opacity=v.job.id===id?.9:.45;});}
        function resize(){const w=host.clientWidth,h=host.clientHeight;if(!w||!h)return;viewportWidth=w;viewportHeight=h;const old=camera.aspect;camera.aspect=w/h;camera.updateProjectionMatrix();renderer.setSize(w,h,false);if(bounds&&Math.abs(old-camera.aspect)>.25)fit();}
        function placeLabel(view,show){
            if(view.label.hidden!==!show)view.label.hidden=!show;
            if(!show)return;
            const transform='translate3d('+view.rect.x.toFixed(2)+'px,'+view.rect.y.toFixed(2)+'px,0)';
            if(view.transform!==transform){view.label.style.transform=transform;view.transform=transform;}
            labelBoxes.push(view.rect);
        }
        function updateLabels(){
            labelBoxes.length=0;const width=viewportWidth,height=viewportHeight;
            const active=routeViews.find(v=>v.job.id===selected&&v.visible);
            let vehicleShown=false;
            if(active&&layers.vehicles&&layers.labels){
                projection.copy(active.position);projection.z+=1;projection.project(camera);
                const x=(projection.x*.5+.5)*width,y=(-projection.y*.5+.5)*height;
                const w=Math.min(200,active.job.vehicle.length*8+18),rect=vehicleLabelView.rect;rect.x=x-w/2;rect.y=y-11;rect.w=w;
                if(projection.z>-1&&projection.z<1&&x>w/2&&x<width-w/2&&y>50&&y<height-45){
                    if(vehicleLabel.textContent!==active.job.vehicle){vehicleLabel.textContent=active.job.vehicle;vehicleLabel.title='跟踪 '+active.job.vehicle;vehicleLabel.style.width=w+'px';}
                    vehicleShown=true;
                }
            }
            placeLabel(vehicleLabelView,vehicleShown);
            if(labelOrderDirty){
                const job=routeViews.find(v=>v.job.id===selected)?.job;
                const priority=v=>job&&(v.city.id===job.from||v.city.id===job.to);
                labelOrder=[...cityViews.filter(priority),...cityViews.filter(v=>!priority(v))];labelOrderDirty=false;
            }
            for(const v of labelOrder){
                projection.copy(v.position);projection.z+=.62;projection.project(camera);
                const x=(projection.x*.5+.5)*width,y=(-projection.y*.5+.5)*height,rect=v.rect,w=rect.w;
                rect.x=x-w/2;rect.y=y-11;
                // A little extra entry spacing prevents labels flickering at collision edges.
                const gap=v.label.hidden?10:6;
                const show=layers.labels&&layers.cities&&projection.z<1&&projection.z>-1&&x>w/2&&x<width-w/2&&y>12&&y<height-45
                    &&!labelBoxes.some(b=>rect.x<b.x+b.w+gap&&rect.x+rect.w+gap>b.x&&rect.y<b.y+b.h+gap&&rect.y+rect.h+gap>b.y);
                placeLabel(v,show);
            }
        }
        let down;
        function onDown(event){down={x:event.clientX,y:event.clientY};}
        function onUp(event){
            if(!down||Math.hypot(event.clientX-down.x,event.clientY-down.y)>5)return;
            const rect=host.getBoundingClientRect();pointer.set((event.clientX-rect.left)/rect.width*2-1,-(event.clientY-rect.top)/rect.height*2+1);
            ray.setFromCamera(pointer,camera);ray.params.Line.threshold=.14;
            const targets=[...(layers.vehicles?trucks:[]),...routeViews.filter(v=>v.visible&&layers.routes).flatMap(v=>[v.tube]),...(layers.cities?cityViews.map(v=>v.group):[]),...(layers.hotspots?heatViews.map(v=>v.group):[])];
            const hits=ray.intersectObjects(targets,true);
            // Translucent hotspot volumes must not steal a click on a visible truck.
            const hit=hits.find(h=>h.object.userData.kind==='truck'&&routeViews[h.instanceId]?.visible)||hits[0];if(!hit)return;
            let object=hit.object;while(!object.userData.kind&&object.parent)object=object.parent;
            const {kind,id}=object.userData;
            if(kind==='truck'){const v=routeViews[hit.instanceId];if(v&&v.visible)options.onSelect(v.job.id);}
            else if(kind==='route')options.onSelect(id);else options.onCity(id,kind==='hotspot');
        }
        renderer.domElement.addEventListener('pointerdown',onDown);renderer.domElement.addEventListener('pointerup',onUp);
        renderer.domElement.addEventListener('webglcontextlost',event=>{event.preventDefault();loading.hidden=false;loading.textContent='渲染中断，请重新加载';});
        function tick(now){
            if(destroyed)return;frame=requestAnimationFrame(tick);
            if(!visible||document.hidden||!loaded){last=now;return;}
            const interval=1000/60;if(now-lastDraw<interval)return;
            lastDraw=now-(now-lastDraw)%interval;
            const dt=Math.min(.08,(now-last)/1000);last=now;
            if(!paused)elapsed+=dt*rate;updateVehicles();
            const tracked=routeViews.find(v=>v.job.id===following&&v.visible);
            if(tracked){projection.copy(tracked.position).sub(controls.target);camera.position.add(projection);controls.target.copy(tracked.position);}
            controls.update();
            // Project DOM labels using exactly the camera pose rendered in this frame.
            camera.updateMatrixWorld();updateLabels();
            if(now-lastTelemetry>120){options.onTick?.(elapsed);lastTelemetry=now;}
            renderer.render(scene,camera);renderCount++;
        }
        const observer=new ResizeObserver(resize);observer.observe(host);
        const intersection=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;last=performance.now();});intersection.observe(host);
        const ready=new GLTFLoader().loadAsync(new URL('models/logistics-map.glb',base).href,progress=>{
            loading.textContent=progress.total?'地图加载 '+Math.round(progress.loaded/progress.total*100)+'%':'地图加载中';
        }).then(gltf=>{
            model=gltf.scene;if(destroyed){disposeModel(model);return;}
            scene.add(model);model.updateMatrixWorld(true);model.traverse(o=>{if(o.isMesh)meshes.push(o);});bounds=new THREE.Box3().setFromObject(model);
            // glTF world transforms cancel: the supplied mesh is XY, with Z up.
            if(bounds.getSize(new THREE.Vector3()).z>10)throw new Error('Unexpected map orientation');
            addCities();setJobs(options.jobs);loaded=true;fit();loading.hidden=true;host.dataset.state='ready';
        }).catch(error=>{loading.hidden=false;loading.textContent='地图加载失败，请刷新重试';host.dataset.state='error';console.error(error);});
        function disposeModel(root){root.traverse(o=>{o.geometry?.dispose();for(const m of (Array.isArray(o.material)?o.material:[o.material]).filter(Boolean)){for(const v of Object.values(m))if(v?.isTexture)v.dispose();m.dispose();}});}
        resize();frame=requestAnimationFrame(tick);
        return {ready,resize,fit,focus,select,setJobs,
            setPaused(value){paused=value;},setRate(value){rate=value;},
            restart(){elapsed=0;updateVehicles();},
            setFilter(query,status){filter.query=query;filter.status=status;applyFilter();},
            setLayer(key,value){layers[key]=value;applyFilter();cityViews.forEach(v=>v.group.visible=layers.cities);heatViews.forEach(h=>h.group.visible=layers.hotspots);updateLabels();},
            state(id){const v=routeViews.find(v=>v.job.id===id);return v?{progress:v.progress,position:v.position.toArray(),distance:Math.round(v.length*80)}:null;},
            inspect(){return {loaded,renderCount,timestampMs:performance.now(),elapsed,following,cameraTarget:controls.target.toArray(),cities:cityViews.length,routes:routeViews.length,hotspots:heatViews.length,drawCalls:renderer.info.render.calls,triangles:renderer.info.render.triangles,paused,layers:{...layers},vehicles:routeViews.map(v=>({id:v.job.id,...this.state(v.job.id),visible:v.visible}))};},
            screenPoint(id){const v=routeViews.find(v=>v.job.id===id);if(!v)return null;projection.copy(v.position);projection.z+=.35;projection.project(camera);const r=host.getBoundingClientRect();return {x:r.x+(projection.x+1)*r.width/2,y:r.y+(1-projection.y)*r.height/2};},
            destroy(){destroyed=true;cancelAnimationFrame(frame);observer.disconnect();intersection.disconnect();controls.dispose();
                if(model)disposeModel(model);routeViews.forEach(v=>[v.tube,v.trail].forEach(o=>{o.geometry.dispose();o.material.dispose();}));
                trucks.forEach(m=>m.dispose());resources.geometries.forEach(g=>g.dispose());resources.materials.forEach(m=>m.dispose());renderer.dispose();renderer.forceContextLoss();renderer.domElement.remove();labelRoot.remove();}
        };
    };
})();
