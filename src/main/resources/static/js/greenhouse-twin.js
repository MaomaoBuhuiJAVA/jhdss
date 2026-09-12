/* Programmatic greenhouse digital twin, isolated from page business state. */
(function (global) {
  'use strict';

  var DEVICE = Object.freeze({
    width: 6.4, depth: 4.5, height: 5.15, extrusion: 0.16,
    baseY: 0.48, railInset: 0.36, potColumns: 4, potRows: 3
  });

  function fallbackSource(container) {
    var source = container.querySelector('.twin-fallback-source');
    return source ? source.getAttribute('src') : '/images/greenhouse-device-reference.jpg';
  }

  function showFallback(container, imageSrc, message) {
    container.innerHTML = '';
    var fallback = document.createElement('div');
    fallback.className = 'twin-fallback';
    var image = document.createElement('img');
    image.src = imageSrc;
    image.alt = '';
    var copy = document.createElement('div');
    copy.innerHTML = '<i class="ri-cube-line"></i><strong>设备三维模型暂不可用</strong><span></span>';
    copy.querySelector('span').textContent = message || '已切换至设备现场图';
    fallback.appendChild(image);
    fallback.appendChild(copy);
    container.appendChild(fallback);
  }

  function emptyApi() {
    return {
      setTrackPosition: function () {}, setScanTarget: function () {},
      setAlertTarget: function () {}, resetView: function () {},
      resize: function () {}, destroy: function () {}
    };
  }

  function createGreenhouseTwin(container, options) {
    options = options || {};
    if (!container) return emptyApi();
    var imageSrc = fallbackSource(container);
    if (!global.THREE || !global.THREE.WebGLRenderer) {
      showFallback(container, imageSrc, '浏览器未能加载 Three.js，已切换至设备现场图');
      return emptyApi();
    }
    try {
      return buildTwin(container, options, imageSrc);
    } catch (error) {
      if (global.console && console.warn) console.warn('Greenhouse twin initialization failed', error);
      showFallback(container, imageSrc, 'WebGL 初始化失败，已切换至设备现场图');
      return emptyApi();
    }
  }

  function buildTwin(container, options, imageSrc) {
    var T = global.THREE;
    var compact = !!options.compact;
    var interactive = options.interactive !== false;
    var destroyed = false;
    var listeners = [];
    var selectable = [];
    var pots = [];
    var geometries = new Set();
    var materials = new Set();

    var scene = new T.Scene();
    scene.background = new T.Color(0x031613);
    scene.fog = new T.Fog(0x031613, 13, 25);
    var camera = new T.PerspectiveCamera(compact ? 31 : 34, 1, 0.1, 70);
    var renderer = new T.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' });
    renderer.setPixelRatio(Math.min(global.devicePixelRatio || 1, compact ? 1.25 : 1.65));
    renderer.shadowMap.enabled = !compact;
    renderer.shadowMap.type = T.PCFSoftShadowMap;
    if ('outputColorSpace' in renderer && T.SRGBColorSpace) renderer.outputColorSpace = T.SRGBColorSpace;
    else if ('outputEncoding' in renderer && T.sRGBEncoding) renderer.outputEncoding = T.sRGBEncoding;
    renderer.domElement.setAttribute('aria-label', '可旋转缩放的温室轨道设备三维模型');
    renderer.domElement.style.cursor = interactive ? 'grab' : 'default';
    renderer.domElement.style.touchAction = interactive ? 'none' : 'auto';
    container.innerHTML = '';
    container.appendChild(renderer.domElement);

    var model = new T.Group();
    model.name = 'GreenhouseTwin';
    scene.add(model);

    function material(config) {
      var value = new T.MeshStandardMaterial(config);
      materials.add(value);
      return value;
    }
    var mats = {
      aluminium: material({ color: 0xbcc9c7, metalness: 0.68, roughness: 0.34 }),
      aluminiumDark: material({ color: 0x62716f, metalness: 0.62, roughness: 0.42 }),
      rail: material({ color: 0x151d1c, metalness: 0.46, roughness: 0.38 }),
      motor: material({ color: 0x121817, metalness: 0.34, roughness: 0.5 }),
      yellow: material({ color: 0xd9a329, metalness: 0.28, roughness: 0.42 }),
      platform: material({ color: 0xdbe3df, metalness: 0.22, roughness: 0.55 }),
      cabinet: material({ color: 0xbcc8c5, metalness: 0.34, roughness: 0.48 }),
      pot: material({ color: 0x182321, metalness: 0.05, roughness: 0.86 }),
      soil: material({ color: 0x3c2e22, roughness: 0.98 }),
      stem: material({ color: 0x6b5335, roughness: 0.9 }),
      leaf: material({ color: 0x39794c, roughness: 0.82 }),
      leafLight: material({ color: 0x56a15e, roughness: 0.8 }),
      flower: material({ color: 0xf2f4e9, roughness: 0.72 }),
      fruit: material({ color: 0xc92d32, roughness: 0.42 }),
      orange: material({ color: 0xf27022, roughness: 0.56 }),
      scan: material({ color: 0x1dffc0, transparent: true, opacity: 0.2, depthWrite: false, side: T.DoubleSide }),
      alert: material({ color: 0xff454d, emissive: 0x6b090d, emissiveIntensity: 0.55, roughness: 0.5 })
    };

    function geometry(value) { geometries.add(value); return value; }
    var unitBox = geometry(new T.BoxGeometry(1, 1, 1));
    var postGeo = geometry(new T.BoxGeometry(DEVICE.extrusion, DEVICE.height, DEVICE.extrusion));
    var wheelGeo = geometry(new T.CylinderGeometry(0.2, 0.2, 0.13, 16));
    var potGeo = geometry(new T.CylinderGeometry(0.34, 0.43, 0.62, 4));
    var soilGeo = geometry(new T.CylinderGeometry(0.3, 0.3, 0.045, 16));
    var stemGeo = geometry(new T.CylinderGeometry(0.035, 0.055, 1, 8));
    var leafGeo = geometry(new T.SphereGeometry(0.16, 8, 5));
    var flowerGeo = geometry(new T.SphereGeometry(0.055, 7, 5));
    var fruitGeo = geometry(new T.SphereGeometry(0.09, 9, 7));

    function addMesh(name, geo, mat, position, scale, parent) {
      var mesh = new T.Mesh(geo, mat);
      mesh.name = name;
      mesh.position.set(position[0], position[1], position[2]);
      if (scale) mesh.scale.set(scale[0], scale[1], scale[2]);
      mesh.castShadow = !compact && mat !== mats.scan;
      mesh.receiveShadow = !compact && (mat === mats.platform || mat === mats.aluminium);
      (parent || model).add(mesh);
      return mesh;
    }
    function box(name, size, position, mat, parent) {
      return addMesh(name, unitBox, mat, position, size, parent);
    }
    function cylinderBetween(name, start, end, radius, mat, parent) {
      var a = new T.Vector3(start[0], start[1], start[2]);
      var b = new T.Vector3(end[0], end[1], end[2]);
      var center = a.clone().add(b).multiplyScalar(0.5);
      var mesh = addMesh(name, stemGeo, mat, [center.x, center.y, center.z], [radius / 0.045, a.distanceTo(b), radius / 0.045], parent);
      mesh.quaternion.setFromUnitVectors(new T.Vector3(0, 1, 0), b.clone().sub(a).normalize());
      return mesh;
    }
    function tube(name, points, radius, mat, parent) {
      var curve = new T.CatmullRomCurve3(points.map(function (point) { return new T.Vector3(point[0], point[1], point[2]); }));
      return addMesh(name, geometry(new T.TubeGeometry(curve, 30, radius, 7, false)), mat, [0, 0, 0], null, parent);
    }
    function setSelection(group, data) {
      group.userData.selection = data;
      selectable.push(group);
    }

    var W = DEVICE.width;
    var D = DEVICE.depth;
    var base = DEVICE.baseY;
    var top = base + DEVICE.height;

    box('plantingPlatform', [W - 0.42, 0.22, D - 0.46], [0, base, 0], mats.platform);
    box('platformApronFront', [W + 0.16, 0.34, 0.16], [0, base - 0.02, D / 2], mats.aluminiumDark);
    box('platformApronBack', [W + 0.16, 0.34, 0.16], [0, base - 0.02, -D / 2], mats.aluminiumDark);
    var corners = [[-W / 2, -D / 2], [-W / 2, D / 2], [W / 2, -D / 2], [W / 2, D / 2]];
    corners.forEach(function (point, index) {
      addMesh('framePost' + index, postGeo, mats.aluminium, [point[0], base + DEVICE.height / 2, point[1]], null);
      box('wheelBracket' + index, [0.34, 0.28, 0.25], [point[0], 0.28, point[1]], mats.aluminiumDark);
      var wheel = addMesh('wheel' + index, wheelGeo, mats.motor, [point[0], 0.12, point[1]], null);
      wheel.rotation.z = Math.PI / 2;
    });
    [base + 0.12, top].forEach(function (y, level) {
      box('frameBeamFront' + level, [W + DEVICE.extrusion, DEVICE.extrusion, DEVICE.extrusion], [0, y, D / 2], mats.aluminium);
      box('frameBeamBack' + level, [W + DEVICE.extrusion, DEVICE.extrusion, DEVICE.extrusion], [0, y, -D / 2], mats.aluminium);
      box('frameBeamLeft' + level, [DEVICE.extrusion, DEVICE.extrusion, D], [-W / 2, y, 0], mats.aluminium);
      box('frameBeamRight' + level, [DEVICE.extrusion, DEVICE.extrusion, D], [W / 2, y, 0], mats.aluminium);
    });
    box('leftMidRail', [DEVICE.extrusion, DEVICE.extrusion, D], [-W / 2, base + 2.15, 0], mats.aluminiumDark);
    box('rightMidRail', [DEVICE.extrusion, DEVICE.extrusion, D], [W / 2, base + 2.15, 0], mats.aluminiumDark);

    var xRailZ = D / 2 - DEVICE.railInset;
    box('xRailFront', [W - 0.32, 0.14, 0.18], [0, top + 0.22, xRailZ], mats.rail);
    box('xRailBack', [W - 0.32, 0.14, 0.18], [0, top + 0.22, -xRailZ], mats.rail);
    box('xRailCarrierFront', [W - 0.1, 0.1, 0.3], [0, top + 0.05, xRailZ], mats.aluminiumDark);
    box('xRailCarrierBack', [W - 0.1, 0.1, 0.3], [0, top + 0.05, -xRailZ], mats.aluminiumDark);
    var chain = new T.Group();
    chain.name = 'dragChain';
    model.add(chain);
    for (var chainIndex = 0; chainIndex < 17; chainIndex += 1) {
      var chainLink = box('dragChainLink' + chainIndex, [0.24, 0.14, 0.3], [-W / 2 + 0.48 + chainIndex * 0.31, top + 0.38, -xRailZ + 0.05], mats.motor, chain);
      chainLink.rotation.z = chainIndex > 13 ? (chainIndex - 13) * 0.06 : 0;
    }

    var carriage = new T.Group();
    carriage.name = 'inspectionCameraCarriage';
    carriage.position.x = 0.9;
    setSelection(carriage, { name: '巡检摄像头', position: 'P-07', status: '扫描中' });
    model.add(carriage);
    box('gantryBridge', [0.2, 0.2, D - 0.62], [0, top + 0.48, 0], mats.aluminiumDark, carriage);
    box('gantryRail', [0.22, 0.13, D - 0.78], [0, top + 0.3, 0], mats.rail, carriage);
    [-xRailZ, xRailZ].forEach(function (z, index) {
      box('gantryMotor' + index, [0.54, 0.44, 0.64], [0, top + 0.51, z], mats.motor, carriage);
      box('gantryMotorAccent' + index, [0.22, 0.46, 0.25], [0.17, top + 0.51, z + (index ? -0.34 : 0.34)], mats.yellow, carriage);
    });
    box('cameraCrosshead', [0.52, 0.38, 0.58], [0, top + 0.16, 0.25], mats.motor, carriage);
    box('cameraArm', [0.16, 0.92, 0.16], [0, top - 0.34, 0.25], mats.aluminiumDark, carriage);
    box('cameraGimbal', [0.46, 0.28, 0.38], [0, top - 0.86, 0.25], mats.motor, carriage);
    var lens = addMesh('cameraLens', geometry(new T.CylinderGeometry(0.13, 0.13, 0.1, 18)), mats.motor, [0, top - 0.87, 0.46], null, carriage);
    lens.rotation.x = Math.PI / 2;
    var scanCone = addMesh('scanCone', geometry(new T.ConeGeometry(0.68, 1.75, 22, 1, true)), mats.scan, [0, top - 1.76, 0.25], null, carriage);
    var scanLine = addMesh('scanLine', geometry(new T.CylinderGeometry(0.018, 0.018, 1.65, 6)), mats.scan, [0, top - 1.74, 0.25], null, carriage);

    function buildPlant(index, x, z) {
      var group = new T.Group();
      group.name = 'plantPot-P' + String(index).padStart(2, '0');
      group.position.set(x, 0, z);
      setSelection(group, { name: '种植盆 P-' + String(index).padStart(2, '0'), position: (index <= 4 ? 'A' : index <= 8 ? 'B' : 'C') + ' 区', status: '在线', moisture: '58%', ec: '1.32 mS/cm' });
      model.add(group);
      var pot = addMesh('pot', potGeo, mats.pot, [0, base + 0.45, 0], null, group);
      pot.rotation.y = Math.PI / 4;
      addMesh('soil', soilGeo, mats.soil, [0, base + 0.76, 0], null, group);
      cylinderBetween('mainStem', [0, base + 0.77, 0], [0.02, base + 2.18, 0], 0.052, mats.stem, group);
      var branches = [
        [[0, base + 1.1, 0], [-0.35, base + 1.55, 0.05]],
        [[0, base + 1.34, 0], [0.38, base + 1.78, -0.03]],
        [[0, base + 1.62, 0], [-0.28, base + 2.02, -0.08]],
        [[0, base + 1.78, 0], [0.3, base + 2.15, 0.06]]
      ];
      branches.forEach(function (branch, branchIndex) {
        cylinderBetween('branch' + branchIndex, branch[0], branch[1], 0.026, mats.stem, group);
        var end = branch[1];
        for (var leafIndex = 0; leafIndex < 2; leafIndex += 1) {
          var leaf = addMesh('leaf', leafGeo, leafIndex % 2 ? mats.leafLight : mats.leaf, [end[0] + (leafIndex ? 0.08 : -0.08), end[1] - leafIndex * 0.08, end[2] + (leafIndex ? 0.09 : -0.08)], [1.15, 0.28, 0.58], group);
          leaf.rotation.z = leafIndex ? -0.48 : 0.48;
        }
      });
      if (index % 3 === 0 || index === 7) {
        addMesh('cherryFruit', fruitGeo, mats.fruit, [0.22, base + 1.78, 0.08], null, group);
        addMesh('cherryFruit', fruitGeo, mats.fruit, [0.34, base + 1.72, 0.11], [0.88, 0.88, 0.88], group);
      }
      if (index % 2 === 0) {
        addMesh('whiteFlower', flowerGeo, mats.flower, [-0.28, base + 1.98, -0.04], [1.5, 0.55, 1.5], group);
        addMesh('whiteFlower', flowerGeo, mats.flower, [0.31, base + 2.12, 0.06], [1.35, 0.55, 1.35], group);
      }
      pots.push(group);
    }
    for (var row = 0; row < DEVICE.potRows; row += 1) {
      for (var column = 0; column < DEVICE.potColumns; column += 1) {
        buildPlant(row * DEVICE.potColumns + column + 1, -2.2 + column * 1.46, -1.35 + row * 1.35);
      }
    }

    var tank = new T.Group();
    tank.name = 'nutrientTank';
    setSelection(tank, { name: '营养液箱', position: '左侧供液区', status: '在线', level: '58%' });
    model.add(tank);
    box('tankBody', [0.82, 1.18, 0.82], [-3.02, 1.1, 1.46], mats.platform, tank);
    box('tankCap', [0.44, 0.12, 0.44], [-3.02, 1.75, 1.46], mats.motor, tank);
    var cabinet = new T.Group();
    cabinet.name = 'controlBox';
    setSelection(cabinet, { name: '配液控制箱', position: '右侧控制区', status: '在线' });
    model.add(cabinet);
    box('cabinetBody', [0.92, 1.55, 0.72], [3.0, 1.3, 1.48], mats.cabinet, cabinet);
    box('cabinetScreen', [0.48, 0.24, 0.025], [3.0, 1.5, 1.85], mats.motor, cabinet);
    box('cabinetButton', [0.08, 0.08, 0.03], [3.24, 1.22, 1.86], mats.yellow, cabinet);

    tube('orangeSupplyHose', [[-3.02, 1.7, 1.46], [-3.24, 2.55, 1.8], [-3.24, 4.35, 1.88], [-2.7, top + 0.2, 1.86]], 0.055, mats.orange, model);
    tube('cameraCable', [[-2.55, top + 0.28, -1.95], [-1.45, top + 0.58, -1.95], [-0.15, top + 0.54, -1.8], [0.72, top + 0.48, -1.35]], 0.035, mats.motor, model);
    tube('irrigationLine', [[-2.8, base + 0.85, 1.1], [-1.6, base + 0.78, 1.0], [0, base + 0.78, 1.0], [2.55, base + 0.78, 1.0]], 0.026, mats.rail, model);
    var alertRing = addMesh('alertTarget', geometry(new T.TorusGeometry(0.52, 0.035, 7, 32)), mats.alert, [0, base + 0.11, 0], null, model);
    alertRing.rotation.x = Math.PI / 2;
    alertRing.visible = false;

    var floorMaterial = new T.MeshStandardMaterial({ color: 0x061f1b, roughness: 0.92, metalness: 0.02 });
    materials.add(floorMaterial);
    var floor = new T.Mesh(geometry(new T.PlaneGeometry(18, 14)), floorMaterial);
    floor.rotation.x = -Math.PI / 2;
    floor.position.y = -0.1;
    floor.receiveShadow = !compact;
    scene.add(floor);
    var gridPoints = [];
    for (var gx = -8; gx <= 8; gx += 1) gridPoints.push(gx, -0.08, -7, gx, -0.08, 7);
    for (var gz = -7; gz <= 7; gz += 1) gridPoints.push(-9, -0.08, gz, 9, -0.08, gz);
    var gridGeometry = geometry(new T.BufferGeometry());
    gridGeometry.setAttribute('position', new T.Float32BufferAttribute(gridPoints, 3));
    var gridMaterial = new T.LineBasicMaterial({ color: 0x0d554b, transparent: true, opacity: 0.48 });
    materials.add(gridMaterial);
    scene.add(new T.LineSegments(gridGeometry, gridMaterial));

    scene.add(new T.HemisphereLight(0xb6fff0, 0x07110f, compact ? 1.35 : 1.15));
    var key = new T.DirectionalLight(0xd5fff6, compact ? 2.25 : 2.1);
    key.position.set(7, 11, 8);
    key.castShadow = !compact;
    if (!compact) {
      key.shadow.mapSize.set(1024, 1024);
      key.shadow.camera.left = -8; key.shadow.camera.right = 8;
      key.shadow.camera.top = 8; key.shadow.camera.bottom = -6;
    }
    scene.add(key);
    var rim = new T.DirectionalLight(0x38a98f, 1.1);
    rim.position.set(-7, 5, -6);
    scene.add(rim);

    var orbit = {
      theta: compact ? 0.76 : 0.72, phi: compact ? 1.08 : 1.02,
      radius: compact ? 13.8 : 13.1, target: new T.Vector3(0, 2.6, 0)
    };
    function applyOrbit() {
      orbit.phi = Math.max(0.42, Math.min(1.42, orbit.phi));
      orbit.radius = Math.max(7.2, Math.min(18.5, orbit.radius));
      camera.position.set(
        orbit.target.x + orbit.radius * Math.sin(orbit.phi) * Math.sin(orbit.theta),
        orbit.target.y + orbit.radius * Math.cos(orbit.phi),
        orbit.target.z + orbit.radius * Math.sin(orbit.phi) * Math.cos(orbit.theta)
      );
      camera.lookAt(orbit.target);
    }
    applyOrbit();

    var tooltip = document.createElement('div');
    tooltip.className = 'twin-tooltip';
    tooltip.hidden = true;
    container.appendChild(tooltip);
    var scanStatus = document.createElement('div');
    scanStatus.className = 'twin-scan-status';
    scanStatus.textContent = 'P-07 扫描中';
    container.appendChild(scanStatus);

    function listen(target, event, handler, config) {
      target.addEventListener(event, handler, config);
      listeners.push(function () { target.removeEventListener(event, handler, config); });
    }
    var pointers = new Map();
    var lastPointer = null;
    var lastPinchDistance = 0;
    var pointerMoved = false;
    function pointerDistance() {
      var values = Array.from(pointers.values());
      if (values.length < 2) return 0;
      return Math.hypot(values[0].x - values[1].x, values[0].y - values[1].y);
    }
    function onPointerDown(event) {
      pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
      lastPointer = { x: event.clientX, y: event.clientY };
      lastPinchDistance = pointerDistance();
      pointerMoved = false;
      container.classList.add('is-dragging');
      try { renderer.domElement.setPointerCapture(event.pointerId); } catch (_) {}
    }
    function onPointerMove(event) {
      if (!pointers.has(event.pointerId)) return;
      var previous = pointers.get(event.pointerId);
      pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (Math.abs(event.clientX - previous.x) + Math.abs(event.clientY - previous.y) > 2) pointerMoved = true;
      if (pointers.size > 1) {
        var distance = pointerDistance();
        if (lastPinchDistance) orbit.radius -= (distance - lastPinchDistance) * 0.025;
        lastPinchDistance = distance;
      } else if (lastPointer) {
        orbit.theta -= (event.clientX - lastPointer.x) * 0.008;
        orbit.phi += (event.clientY - lastPointer.y) * 0.008;
        lastPointer = { x: event.clientX, y: event.clientY };
      }
      applyOrbit();
    }
    function onPointerUp(event) {
      pointers.delete(event.pointerId);
      lastPointer = pointers.size ? Array.from(pointers.values())[0] : null;
      lastPinchDistance = pointerDistance();
      if (!pointers.size) container.classList.remove('is-dragging');
      try { renderer.domElement.releasePointerCapture(event.pointerId); } catch (_) {}
    }

    var raycaster = new T.Raycaster();
    var pointer = new T.Vector2();
    function onClick(event) {
      if (pointerMoved) return;
      var rect = renderer.domElement.getBoundingClientRect();
      pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
      pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
      raycaster.setFromCamera(pointer, camera);
      var hit = raycaster.intersectObjects(selectable, true)[0];
      if (!hit) { tooltip.hidden = true; return; }
      var node = hit.object;
      while (node && !node.userData.selection) node = node.parent;
      if (!node) return;
      var data = node.userData.selection;
      var details = [data.name, data.position, data.status, data.moisture ? '湿度 ' + data.moisture : '', data.ec ? 'EC ' + data.ec : '', data.level ? '液位 ' + data.level : ''].filter(Boolean);
      tooltip.textContent = details.join(' · ');
      tooltip.hidden = false;
      tooltip.style.left = Math.max(8, Math.min(rect.width - 205, event.clientX - rect.left + 12)) + 'px';
      tooltip.style.top = Math.max(8, Math.min(rect.height - 54, event.clientY - rect.top + 12)) + 'px';
      if (typeof options.onSelect === 'function') options.onSelect(tooltip.textContent, data);
    }
    if (interactive) {
      listen(renderer.domElement, 'pointerdown', onPointerDown);
      listen(renderer.domElement, 'pointermove', onPointerMove);
      listen(renderer.domElement, 'pointerup', onPointerUp);
      listen(renderer.domElement, 'pointercancel', onPointerUp);
      listen(renderer.domElement, 'wheel', function (event) {
        event.preventDefault();
        orbit.radius += event.deltaY * 0.012;
        applyOrbit();
      }, { passive: false });
      listen(renderer.domElement, 'click', onClick);
    }

    var targetX = 0.9;
    function resize() {
      if (destroyed) return;
      var width = Math.max(1, container.clientWidth || 320);
      var height = Math.max(1, container.clientHeight || 220);
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
    }
    function setTrackPosition(value) {
      var number = Number(value);
      if (!Number.isFinite(number)) number = 0.58;
      number = Math.max(0, Math.min(1, number));
      targetX = -W / 2 + 0.62 + number * (W - 1.24);
    }
    function setScanTarget(value) {
      var activeScanTarget = value ? String(value) : '';
      scanCone.visible = !!activeScanTarget;
      scanLine.visible = !!activeScanTarget;
      scanStatus.hidden = !activeScanTarget;
      scanStatus.textContent = activeScanTarget + ' 扫描中';
      carriage.userData.selection.position = activeScanTarget || '待命位置';
      carriage.userData.selection.status = activeScanTarget ? '扫描中' : '待命';
    }
    function setAlertTarget(value) {
      var index = parseInt(String(value || '').replace(/\D/g, ''), 10) - 1;
      alertRing.visible = index >= 0 && !!pots[index];
      if (alertRing.visible) alertRing.position.set(pots[index].position.x, base + 0.13, pots[index].position.z);
    }
    function resetView() {
      orbit.theta = compact ? 0.76 : 0.72;
      orbit.phi = compact ? 1.08 : 1.02;
      orbit.radius = compact ? 13.8 : 13.1;
      model.rotation.y = 0;
      tooltip.hidden = true;
      applyOrbit();
    }

    var resizeObserver = null;
    if (global.ResizeObserver) {
      resizeObserver = new ResizeObserver(resize);
      resizeObserver.observe(container);
    } else listen(global, 'resize', resize);
    resize();
    setTrackPosition(options.trackPosition);
    setScanTarget(options.scanTarget === undefined ? 'P-07' : options.scanTarget);
    setAlertTarget(options.alertTarget);

    var raf = 0;
    var lastTime = performance.now();
    function render(time) {
      if (destroyed) return;
      raf = global.requestAnimationFrame(render);
      var delta = Math.min(40, time - lastTime);
      lastTime = time;
      carriage.position.x += (targetX - carriage.position.x) * Math.min(1, delta * 0.006);
      if (!document.hidden) {
        if (options.autoRotate !== false && !pointers.size) model.rotation.y += delta * (compact ? 0.000035 : 0.000022);
        if (alertRing.visible) alertRing.material.emissiveIntensity = 0.35 + Math.sin(time * 0.006) * 0.22;
        renderer.render(scene, camera);
      }
    }
    raf = global.requestAnimationFrame(render);

    function destroy() {
      if (destroyed) return;
      destroyed = true;
      global.cancelAnimationFrame(raf);
      if (resizeObserver) resizeObserver.disconnect();
      listeners.splice(0).forEach(function (remove) { remove(); });
      geometries.forEach(function (item) { item.dispose(); });
      materials.forEach(function (item) { item.dispose(); });
      renderer.dispose();
      if (renderer.forceContextLoss) renderer.forceContextLoss();
      container.classList.remove('is-dragging');
      container.innerHTML = '';
    }
    listen(global, 'pagehide', destroy, { once: true });
    return {
      setTrackPosition: setTrackPosition, setScanTarget: setScanTarget,
      setAlertTarget: setAlertTarget, resetView: resetView,
      resize: resize, destroy: destroy, fallbackImage: imageSrc
    };
  }

  global.createGreenhouseTwin = createGreenhouseTwin;
})(window);
