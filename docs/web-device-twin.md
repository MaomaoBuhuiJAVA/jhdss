# Web Device Twin

The homepage and rail inspection thumbnails and the nutrient page main display
load the same photo-derived Blender model. The source blend is not modified by
the web exporter. The corrected service platform remains on the short +X side.

## Model and Effects

- Source: `artifacts/blender-device/JHDS_Inspection_Gantry.blend`.
- Runtime asset: `src/main/resources/static/models/inspection-gantry.glb`.
- Nursery revision (2026-09-12): twelve replacement plants, with two reviewed
  detail passes. Source backup, comparison renders, and preservation checks
  are in `artifacts/blender-device/revisions/new-plants/`. Equipment and live
  state contracts are unchanged; the GLB contains five vertex-colored plant
  groups and is approximately 3 MB for the complete model.
- Export: `scripts/blender_device/export_web.py`, run in background Blender.
- The exported model is Z-up in metres. The viewer uses Z-up too; do not apply
  an additional glTF Y-up conversion.
- Rig: `WEB_DEVICE_ROOT / WEB_GANTRY_X / WEB_CARRIAGE_Y / WEB_CAMERA_PAN /
  WEB_CAMERA_TILT`. Three drag chains are instanced and reshaped with the rig.
- `FX_FOLIAR` is parented to the moving carriage at the downward nozzle outlet.
- `FX_POT_01` through `FX_POT_12` locate pot soil and the drip/mist effects.
- White mist is a visualization of gas fertilization, not an image of actual
  CO2, which is invisible. Drips on all pots indicate the shared circuit state,
  not twelve independently measured flow rates.

Small flower centres, stems, petals and fruit are simplified for web use.
Static meshes are combined by material and articulation boundary; the original
high-detail Blender file and packed reference photos are retained. Blender
procedural material nodes are approximated by the authored PBR base colors.

## Live State Contract

`GET /jhds/api/device-twin/status` returns the normal `{code,msg,data}` envelope.
The endpoint is read-only. It sends no motor or pump control requests. The
Bluetooth panel's reachability GET is cached for five seconds. Each visible
page polls the shared endpoint approximately every 750 ms after a response.

| Display | Current source | Meaning |
| --- | --- | --- |
| X carriage | `RailPositionService.twinPosition()` | Motor-time position estimate, not encoder feedback |
| Y carriage | `ControlPanelService.twinMotionStatus()` | Forward/backward relay-time estimate, not encoder feedback |
| Foliar spray | `CONTROL_PANEL_PUMP` | Successful HTTP response to existing panel pump command |
| Drip irrigation | `PUMP_CIRCULATION` | Successful response to the configured irrigation circulation pump command |
| Gas fertilizer | `PUMP_CO2` | Successful MQTT command response |

Set `device.twin.drip-alias` (environment variable `DEVICE_TWIN_DRIP_ALIAS`) to
the actual drip-circuit equipment alias if deployment wiring differs. The
seeded `PUMP_IRRIGATE` entry currently has no hardware command frames, so its
`LOCAL_SAVED` result is deliberately not shown as physical irrigation.

Observations are recorded in the shared command services, so manual and
automatic commands sent through those services use the same state. Browser
checkbox changes do not directly start animation. Failed/pending commands,
startup, and connection loss show unknown rather than a fabricated off/on
state. Old acknowledgements cannot reactivate after a detected reconnect.
Acknowledgements are retained while that connection session remains valid;
they are not ongoing flow measurements. A broker connection alone does not
prove a DTU or pump is online. External switches or controllers bypassing these
services require a separate device telemetry input to be represented reliably.

Snapshots contain a server timestamp, pose timestamp, normalized X position,
normalized X velocity per second, source, connectivity and per-effect
`active/source/connected/acknowledgedAt`. Response timestamps are checked against
the HTTP server date and must advance. A failed request or a six-second data
gap clears the effects and freezes motion. Hidden pages suspend polling and
refresh when visible. All views load the same asset and read the same backend.

The model starts at the deployed right/bottom power-on position (`x=1`, `y=0`).
The authored rig axes face the control-box side, so the physical origin is
rendered at the opposite lower corner, away from the box.
X and Y motion are synchronized from the command services and extrapolated
between snapshots using their calibrated full-travel times. Both axes remain
dead-reckoned estimates because the current hardware exposes relay state but no
encoder feedback. PTZ angles stay at their model positions until a calibrated
camera SDK feed is connected.

## Build and Verification

Three.js r169, GLTFLoader, OrbitControls and Draco are served locally, without
CDN dependency for the model. Their notices are under `static/js/vendor`.
Rebuild the vendor bundle:

```powershell
npm.cmd install --prefix tmp/web-twin-build --no-audit --no-fund three@0.169.0 esbuild@0.25.9
./tmp/web-twin-build/node_modules/.bin/esbuild.cmd scripts/web_twin/vendor-entry.js --bundle --minify --format=iife --global-name=Greenhouse3D --outfile=src/main/resources/static/js/vendor/greenhouse-3d.js --legal-comments=eof
```

Copy the Three package LICENSE and its `examples/jsm/libs/draco/gltf` decoder
files when upgrading Three. GLB and WASM are explicitly unfiltered Maven
resources. Source and `target/classes` binary hashes must match.

```powershell
mvn.cmd test
node --test scripts/web_twin/state.test.cjs
node scripts/web_twin/browser.test.cjs
```

Browser tests use the running app, intercept twin snapshots, and block all
non-GET API requests. They do not run real motors/pumps. Set `TWIN_TEST_URL`,
`CHROME_PATH`, and `CODEX_NODE_MODULES` to override local test paths. Tests check
desktop/mobile framing and canvas pixels, moving nozzle anchoring, independent
and simultaneous effects, on/off/offline, orbit, reset, fullscreen and overflow.
Screenshots and the result report are in `artifacts/blender-device/web-check`.

The live app must be restarted after Java changes, and a full browser refresh
may be needed after cached template changes. Never restart during an active
patrol or irrigation cycle just to preview a model change.
