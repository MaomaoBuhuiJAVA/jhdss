(function () {
    'use strict';
    const assetRoot = new URL('../', document.currentScript.src);
    const stateAPI = window.GreenhouseTwinState;
    const subscribers = new Set();
    let latest = stateAPI.disconnected(), receivedAt = 0, lastStamp = 0;
    let pollTimer, inFlight, refreshAgain = false;
    function publish(state) { latest = state; subscribers.forEach(callback => callback(state)); }
    async function refresh() {
        if (inFlight) { refreshAgain = true; return; }
        clearTimeout(pollTimer);
        if (!subscribers.size || document.hidden) return;
        const controller = new AbortController();
        inFlight = controller;
        const timeout = setTimeout(() => controller.abort(), 4500);
        try {
            const response = await fetch(new URL('api/device-twin/status', assetRoot),
                {cache: 'no-store', signal: controller.signal});
            if (!response.ok) throw new Error('Twin status unavailable');
            const next = stateAPI.decode(await response.json(), Date.parse(response.headers.get('Date')));
            if (next.timestampMs <= lastStamp) throw new Error('Repeated or outdated snapshot');
            lastStamp = next.timestampMs;
            receivedAt = performance.now();
            publish(next);
        } catch (error) { publish(stateAPI.disconnected()); }
        finally {
            clearTimeout(timeout); inFlight = null;
            if (subscribers.size) pollTimer = setTimeout(refresh, refreshAgain ? 0 : 750);
            refreshAgain = false;
        }
    }
    document.addEventListener('visibilitychange', function () {
        if (document.hidden) {
            clearTimeout(pollTimer);
            if (inFlight) inFlight.abort();
            publish(stateAPI.disconnected());
        } else refresh();
    });
    window.GreenhouseTwinFeed = {refresh};
    window.createGreenhouseTwin = function (container, options) {
        options = options || {};
        const live = options.live !== false, compact = !!options.compact;
        const interactive = options.interactive !== false;
        const reduceMotion = window.matchMedia
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        const autoRotate = options.autoRotate === true && !reduceMotion;
        container.classList.add('device-twin-host');
        container.classList.toggle('is-interactive', interactive);
        const status = document.createElement('div');
        status.className = 'device-twin-message';
        status.setAttribute('role', 'status');
        status.textContent = '模型加载中';
        container.appendChild(status);
        let destroyed = false, loaded = false, frame, observer, renderer, controls, model, viewBox;
        let deviceState = stateAPI.disconnected(), chainViews = [], effectViews = {}, rigs = {};
        // The deployed camera powers up at the confirmed right/bottom origin.
        const target = {x: 1, y: 0, pan_deg: 0, tilt_deg: 0}, pose = Object.assign({}, target);
        const localVelocity = {x: 0, y: 0};
        let lastFrame = performance.now(), elapsed = 0, lastRender = 0, poseReceivedAt = 0;
        const header = container.closest('.twin-card, .nutrient-equipment');
        const badge = header && header.querySelector('[data-twin-connection]');
        const states = document.createElement('div');
        states.className = 'device-twin-effects';
        const names = {foliar: '叶面肥', drip: '滴灌', gas: '气肥'}, labels = {};
        for (const key of stateAPI.keys) {
            const label = document.createElement('span');
            label.dataset.effect = key; labels[key] = label; states.appendChild(label);
        }
        container.appendChild(states);
        function setState(state) {
            deviceState = state;
            poseReceivedAt = performance.now();
            if (!state.pose.connected) Object.assign(target, pose);
            for (const key of Object.keys(target)) if (Number.isFinite(state.pose[key])) target[key] = state.pose[key];
            if (badge) {
                badge.textContent = state.pose.connected ? '轨道估算' : '同步未连接';
                badge.dataset.connected = String(state.pose.connected);
            }
            for (const key of stateAPI.keys) {
                const item = state.effects[key];
                labels[key].textContent = names[key] + ' ' + (!item.known ? '未知' : item.active ? '开启' : '关闭');
                labels[key].dataset.active = String(item.active);
                labels[key].title = !item.known ? '暂无有效设备回执' : (item.source === 'measured' ? '设备反馈' : '最近控制回执，非流量实测');
                if (effectViews[key]) effectViews[key].object.visible = item.active;
            }
        }
        function fail(message) { status.hidden = false; status.textContent = message; container.dataset.modelState = 'error'; }
        if (!window.Greenhouse3D) {
            fail('3D 组件加载失败'); return {destroy() { container.replaceChildren(); }};
        }
        const {THREE, GLTFLoader, DRACOLoader, OrbitControls, RoomEnvironment} = window.Greenhouse3D;
        const scene = new THREE.Scene();
        scene.background = new THREE.Color(0x202b2d);
        const camera = new THREE.PerspectiveCamera(35, 1, .05, 80);
        camera.up.set(0, 0, 1);
        let environmentTarget;
        const draco = new DRACOLoader();
        draco.setDecoderPath(new URL('js/vendor/draco/gltf/', assetRoot).href);
        draco.setWorkerLimit(2);
        try {
            renderer = new THREE.WebGLRenderer({antialias: true, alpha: false});
            renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, compact ? 1.5 : 2));
            renderer.outputColorSpace = THREE.SRGBColorSpace;
            renderer.toneMapping = THREE.ACESFilmicToneMapping;
            renderer.toneMappingExposure = 1.05;
            renderer.domElement.setAttribute('aria-label', '温室巡检与施肥设备三维模型');
            renderer.domElement.setAttribute('tabindex', '0');
            container.prepend(renderer.domElement);
            const pmrem = new THREE.PMREMGenerator(renderer), environment = new RoomEnvironment();
            environmentTarget = pmrem.fromScene(environment, .04);
            scene.environment = environmentTarget.texture;
            environment.dispose(); pmrem.dispose();
            const hemi = new THREE.HemisphereLight(0xf0f9ff, 0x596963, 1.6);
            hemi.position.set(0, 0, 5); scene.add(hemi);
            const key = new THREE.DirectionalLight(0xffffff, 1.6);
            key.position.set(2, -4, 6); scene.add(key);
            const fill = new THREE.DirectionalLight(0xb5d9e5, .8);
            fill.position.set(-3, 2, 4); scene.add(fill);
            controls = new OrbitControls(camera, renderer.domElement);
            controls.enableDamping = true; controls.enablePan = !compact;
            controls.enabled = interactive;
            controls.autoRotate = autoRotate;
            controls.autoRotateSpeed = Number.isFinite(options.autoRotateSpeed)
                ? options.autoRotateSpeed : .6;
            controls.minDistance = 1.7; controls.maxDistance = 12;
            controls.maxPolarAngle = Math.PI * .49;
            controls.addEventListener('start', function () {
                if (!interactive) return;
                controls.autoRotate = false;
                container.classList.add('is-dragging');
            });
            controls.addEventListener('end', function () {
                container.classList.remove('is-dragging');
            });
            renderer.domElement.addEventListener('webglcontextlost', contextLost);
            renderer.domElement.addEventListener('webglcontextrestored', contextRestored);
        } catch (error) {
            if (renderer) renderer.dispose(); draco.dispose();
            fail('浏览器无法启用 3D 渲染'); return {destroy() { container.replaceChildren(); }};
        }
        function contextLost(event) { event.preventDefault(); fail('3D 渲染连接中断'); }
        function contextRestored() { status.hidden = loaded; container.dataset.modelState = 'ready'; }
        function resize() {
            if (destroyed) return;
            const w = Math.max(1, container.clientWidth), h = Math.max(1, container.clientHeight), oldAspect = camera.aspect;
            camera.aspect = w / h; camera.updateProjectionMatrix(); renderer.setSize(w, h, false);
            if (Math.abs(oldAspect - camera.aspect) > .2) resetView();
        }
        function resetView() {
            const box = loaded
                ? (viewBox || (viewBox = new THREE.Box3().setFromObject(model))).clone()
                : new THREE.Box3(new THREE.Vector3(-1.3, -.9, 0), new THREE.Vector3(1.9, .9, 2.7));
            const center = box.getCenter(new THREE.Vector3());
            const direction = new THREE.Vector3(1.05, -1.65, .9).normalize();
            const right = new THREE.Vector3().crossVectors(camera.up, direction).normalize();
            const up = new THREE.Vector3().crossVectors(direction, right);
            const tan = Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
            let distance = 0;
            for (const x of [box.min.x, box.max.x]) for (const y of [box.min.y, box.max.y]) for (const z of [box.min.z, box.max.z]) {
                const offset = new THREE.Vector3(x, y, z).sub(center), depth = offset.dot(direction);
                distance = Math.max(distance, depth + Math.abs(offset.dot(right)) / (tan * camera.aspect),
                    depth + Math.abs(offset.dot(up)) / tan);
            }
            const damping = controls.enableDamping;
            controls.enableDamping = false;
            controls.reset();
            camera.position.copy(center).addScaledVector(direction, distance * 1.05);
            controls.target.copy(center);
            controls.update();
            controls.enableDamping = damping;
            controls.saveState();
        }
        observer = new ResizeObserver(resize); observer.observe(container); resize(); resetView();
        function addChains(metadata) {
            return metadata.chains.map(chain => {
                const template = model.getObjectByName('WEB_TEMPLATE_' + chain.name);
                if (!template || !template.isMesh) throw new Error('Missing chain template');
                template.removeFromParent();
                const mesh = new THREE.InstancedMesh(template.geometry, template.material, chain.fractions.length);
                mesh.frustumCulled = false; rigs[chain.parent].add(mesh);
                const cables = [-.014, .014].map(offset => {
                    const geometry = new THREE.BufferGeometry();
                    geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(65 * 3), 3));
                    const line = new THREE.Line(geometry, new THREE.LineBasicMaterial({color: 0x202524}));
                    line.frustumCulled = false; rigs[chain.parent].add(line);
                    return {offset, line};
                });
                return {chain, mesh, cables};
            });
        }
        const transform = new THREE.Object3D();
        const turn = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 0, 1), Math.PI / 2);
        const yAxis = new THREE.Vector3(0, 1, 0);
        function updateChains() {
            for (const view of chainViews) {
                const c = view.chain, alongY = c.axis === 'Y';
                const endpoint = alongY ? rigs.CARRIAGE_Y.position.y : rigs.GANTRY_X.position.x;
                const bend = (c.length + c.fixed + endpoint - Math.PI * c.radius) / 2, lower = bend - c.fixed;
                function sample(distance, offset) {
                    let along, z, angle;
                    if (distance < lower) { along = c.fixed + distance; z = c.base_z; angle = 0; }
                    else if (distance < lower + Math.PI * c.radius) {
                        const phase = (distance - lower) / c.radius - Math.PI / 2;
                        along = bend + c.radius * Math.cos(phase); z = c.base_z + c.radius + c.radius * Math.sin(phase); angle = phase + Math.PI / 2;
                    } else { along = bend - (distance - lower - Math.PI * c.radius); z = c.base_z + 2 * c.radius; angle = Math.PI; }
                    transform.position.set(alongY ? c.side + offset : along, alongY ? along : c.side + offset, z);
                    transform.quaternion.setFromAxisAngle(yAxis, -angle);
                    if (alongY) transform.quaternion.premultiply(turn);
                    transform.updateMatrix();
                }
                c.fractions.forEach((fraction, i) => { sample(fraction * c.length, 0); view.mesh.setMatrixAt(i, transform.matrix); });
                view.mesh.instanceMatrix.needsUpdate = true;
                for (const cable of view.cables) {
                    const positions = cable.line.geometry.attributes.position;
                    for (let i = 0; i < positions.count; i++) {
                        sample(i * c.length / (positions.count - 1), cable.offset);
                        positions.setXYZ(i, transform.position.x, transform.position.y, transform.position.z);
                    }
                    positions.needsUpdate = true;
                }
            }
        }
        function particles(count, color, size, opacity, parent, mist) {
            const geometry = new THREE.BufferGeometry();
            geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(count * 3), 3));
            const material = new THREE.ShaderMaterial({transparent: true, depthWrite: false,
                uniforms: {color: {value: new THREE.Color(color)}, opacity: {value: opacity}, size: {value: size}, pixelScale: {value: 400}, mist: {value: mist ? 1 : 0}},
                vertexShader: 'uniform float size; uniform float pixelScale; void main(){vec4 p=modelViewMatrix*vec4(position,1.0); gl_Position=projectionMatrix*p; gl_PointSize=clamp(size*pixelScale/max(0.1,-p.z),1.0,90.0);}',
                fragmentShader: 'uniform vec3 color; uniform float opacity; uniform float mist; float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);} float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);} void main(){float r=length(gl_PointCoord-0.5)*2.0; if(r>1.0) discard; float n=mix(1.0,0.35+0.65*noise(gl_PointCoord*9.0),mist); gl_FragColor=vec4(color,opacity*pow(1.0-r*r,2.0)*n);}'});
            const object = new THREE.Points(geometry, material);
            object.frustumCulled = false; object.visible = false; parent.add(object);
            return {object, positions: geometry.attributes.position};
        }
        let potAnchors = [];
        function addEffects() {
            const nozzle = model.getObjectByName('FX_FOLIAR');
            if (!nozzle) throw new Error('Missing nozzle');
            potAnchors = Array.from({length: 12}, (_, i) => model.getObjectByName('FX_POT_' + String(i + 1).padStart(2, '0')));
            if (potAnchors.some(anchor => !anchor)) throw new Error('Missing pot anchors');
            effectViews.foliar = particles(compact ? 200 : 440, 0xa8e8ff, .025, .95, nozzle);
            effectViews.gas = particles(12 * (compact ? 24 : 44), 0xffffff, .38, .23, rigs.DEVICE_ROOT, true);
            const drops = new THREE.InstancedMesh(new THREE.SphereGeometry(1, 6, 5),
                new THREE.MeshBasicMaterial({color: 0x53c6fa, transparent: true, opacity: .95}), 12 * 8);
            drops.frustumCulled = false; drops.visible = false; rigs.DEVICE_ROOT.add(drops);
            effectViews.drip = {object: drops};
        }
        const fract = value => value - Math.floor(value);
        function updateEffects(time) {
            for (const key of ['foliar', 'gas']) {
                const view = effectViews[key];
                if (!view.object.visible) continue;
                view.object.material.uniforms.pixelScale.value = renderer.domElement.height;
                for (let i = 0; i < view.positions.count; i++) {
                    const phase = fract(time * (key === 'foliar' ? .7 : .24) + i * .61803398875);
                    const angle = i * 2.39996 + (key === 'gas' ? time * .28 : 0);
                    if (key === 'foliar') {
                        const radius = Math.sqrt(fract(i * .754877)) * phase * .43;
                        view.positions.setXYZ(i, Math.cos(angle) * radius, Math.sin(angle) * radius, -phase * 1.24);
                    } else {
                        const a = potAnchors[i % 12].position, radius = .18 + .12 * fract(i * .4142) + phase * .07;
                        view.positions.setXYZ(i, a.x + Math.cos(angle) * radius, a.y + Math.sin(angle) * radius, a.z + .02 + phase * .35);
                    }
                }
                view.positions.needsUpdate = true;
            }
            const drops = effectViews.drip.object;
            if (drops.visible) {
                for (let i = 0; i < drops.count; i++) {
                    const a = potAnchors[Math.floor(i / 8)].position, phase = fract(time * 1.25 + i * .618);
                    transform.position.set(a.x + (i % 2 ? .075 : -.075), a.y - .09, a.z + .28 * (1 - phase));
                    transform.quaternion.identity(); transform.scale.set(.012, .012, .025); transform.updateMatrix();
                    drops.setMatrixAt(i, transform.matrix);
                }
                drops.instanceMatrix.needsUpdate = true; transform.scale.set(1, 1, 1);
            }
        }
        const loader = new GLTFLoader().setDRACOLoader(draco);
        const ready = Promise.all([
            loader.loadAsync(new URL('models/inspection-gantry.glb?v=plants-20260912', assetRoot).href),
            fetch(new URL('models/inspection-gantry.json?v=plants-20260912', assetRoot)).then(response => {
                if (!response.ok) throw new Error('Missing model metadata'); return response.json();
            })
        ]).then(([gltf, metadata]) => {
            model = gltf.scene;
            if (destroyed) { disposeTree(model); return; }
            for (const name of ['DEVICE_ROOT', 'GANTRY_X', 'CARRIAGE_Y', 'CAMERA_PAN', 'CAMERA_TILT']) {
                rigs[name] = model.getObjectByName('WEB_' + name);
                if (!rigs[name]) throw new Error('Incomplete model rig');
            }
            scene.add(model); chainViews = addChains(metadata); addEffects(); loaded = true;
            setState(deviceState); resetView(); status.hidden = true; container.dataset.modelState = 'ready';
        }).catch(error => { if (!destroyed) { fail('模型加载失败，请刷新重试'); console.error('Device twin:', error); } });
        function animate(now) {
            if (destroyed) return;
            frame = requestAnimationFrame(animate);
            if (document.hidden || now - lastRender < (compact ? 50 : 32)) return;
            const dt = Math.min(.1, (now - lastFrame) / 1000);
            lastFrame = now; lastRender = now; elapsed += dt;
            if (live && receivedAt && now - receivedAt > 6000) setState(stateAPI.disconnected());
            if (loaded) {
                if (deviceState.pose.xConnected && !localVelocity.x
                        && Number.isFinite(deviceState.pose.x) && Number.isFinite(deviceState.pose.velocityX)) {
                    const ahead = Math.min(1.5, (now - poseReceivedAt) / 1000);
                    target.x = Math.max(deviceState.pose.minX || 0, Math.min(1,
                        deviceState.pose.x + deviceState.pose.velocityX * ahead));
                }
                if (deviceState.pose.yConnected && !localVelocity.y
                        && Number.isFinite(deviceState.pose.y) && Number.isFinite(deviceState.pose.velocityY)) {
                    const ahead = Math.min(1.5, (now - poseReceivedAt) / 1000);
                    target.y = Math.max(0, Math.min(1,
                        deviceState.pose.y + deviceState.pose.velocityY * ahead));
                }
                target.x = Math.max(deviceState.pose.minX || 0, Math.min(1, target.x + localVelocity.x * dt));
                target.y = Math.max(0, Math.min(1, target.y + localVelocity.y * dt));
                for (const key of Object.keys(pose)) pose[key] += (target[key] - pose[key]) * (1 - Math.exp(-dt * 12));
                // The authored axes point toward the control box. The installed
                // camera starts at the opposite, lower corner of the gantry.
                rigs.GANTRY_X.position.x = 1.03 - pose.x * 2.06;
                rigs.CARRIAGE_Y.position.y = .57 - pose.y * 1.14;
                rigs.CAMERA_PAN.rotation.z = THREE.MathUtils.degToRad(pose.pan_deg);
                rigs.CAMERA_TILT.rotation.x = THREE.MathUtils.degToRad(pose.tilt_deg);
                updateChains(); updateEffects(elapsed);
            }
            controls.update(); renderer.render(scene, camera);
        }
        setState(latest);
        if (live) { subscribers.add(setState); refresh(); }
        frame = requestAnimationFrame(animate);
        function disposeTree(root) {
            const geometries = new Set(), materials = new Set();
            root.traverse(object => {
                if (object.geometry) geometries.add(object.geometry);
                if (object.material) (Array.isArray(object.material) ? object.material : [object.material]).forEach(m => materials.add(m));
            });
            geometries.forEach(g => g.dispose()); materials.forEach(m => m.dispose());
        }
        const api = {
            ready, resetView, resize,
            setTrackPosition(value) { if (!live && Number.isFinite(value)) target.x = Math.max(0, Math.min(1, value)); },
            setAxisMotion(axis, direction) {
                if (axis === 'x') {
                    localVelocity.x = direction === 'left' ? -.055 : direction === 'right' ? .055 : 0;
                } else if (axis === 'y') {
                    localVelocity.y = direction === 'forward' ? .087 : direction === 'backward' ? -.087 : 0;
                }
            },
            setScanTarget() {}, setAlertTarget() {},
            inspect() {
                const nozzle = model && model.getObjectByName('FX_FOLIAR');
                return {loaded, pose: Object.assign({}, pose), effects: deviceState.effects,
                    nozzle: nozzle ? nozzle.getWorldPosition(new THREE.Vector3()).toArray() : null,
                    pots: potAnchors.length, chains: chainViews.length,
                    drawCalls: renderer.info.render.calls, triangles: renderer.info.render.triangles,
                    camera: camera.position.toArray(), autoRotate: controls.autoRotate,
                    rigPosition: loaded ? {x: rigs.GANTRY_X.position.x, y: rigs.CARRIAGE_Y.position.y} : null,
                    localVelocity: Object.assign({}, localVelocity)};
            },
            destroy() {
                if (destroyed) return;
                destroyed = true; subscribers.delete(setState);
                if (!subscribers.size) { clearTimeout(pollTimer); if (inFlight) inFlight.abort(); }
                cancelAnimationFrame(frame); observer.disconnect(); controls.dispose(); draco.dispose(); disposeTree(scene);
                if (environmentTarget) environmentTarget.dispose();
                renderer.domElement.removeEventListener('webglcontextlost', contextLost);
                renderer.domElement.removeEventListener('webglcontextrestored', contextRestored);
                renderer.dispose(); renderer.forceContextLoss(); container.replaceChildren();
                window.removeEventListener('pagehide', onPageHide);
            }
        };
        function onPageHide(event) { if (!event.persisted) api.destroy(); }
        window.addEventListener('pagehide', onPageHide);
        return api;
    };
})();
