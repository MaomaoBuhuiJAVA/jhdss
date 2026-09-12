# After-Sales Logistics Map

## Entry Point

Open `/jhds/after-sales`. The primary desktop workspace has a 15/70/15 layout:
sales KPI cards, cold-chain summary and destination totals on the left; the large
map in the center; orders and the selected vehicle's details on the right. The
map occupies about 70% of the workspace width, with independent sidebar scrolling.
Short desktop screens use compact KPI cards. Below 1100px the map and playback
controls come first, with the information panels below; phones stack both panels.
Existing feedback analysis, tables and batch trace remain below the main scene.
The existing logistics overlay reuses the same map workspace and WebGL context.
Other after-sales feedback and trace workflows remain in place.

## Model Source And License

- Supplied file: `b43136c9fd294505b624d240180aedd6.glb`
- Served file: `src/main/resources/static/models/logistics-map.glb`
- Source: https://sketchfab.com/3d-models/3d-b43136c9fd294505b624d240180aedd6
- Author: `1933617028`
- License: CC BY 4.0, https://creativecommons.org/licenses/by/4.0/
- SHA-256: `A30F1E4755E37554E81D69129A58B504F019819FBFDAB963F29F227BF53B9D21`

The source GLB is unchanged. The source, copied asset and Maven output have matching
hashes. Keep the visible author/license link when deploying. Terrain, rivers,
province textures and islands come from this asset; logistics overlays are added
at runtime. The asset is about 10.9 MB and contains roughly 91,000 triangles.

## Data And Coordinate Limits

This is a local virtual logistics simulation. It does not issue device commands,
dispatch vehicles, or read real fleet GPS. The supplied mesh has no CRS or GPS
metadata and its relief is stylized. City anchors in `logistics-data.js` are
visually calibrated model XY coordinates, with Z up. They are approximate display
locations, not survey coordinates. The original model is not an authoritative
navigation or administrative boundary dataset.

Routes pass through ordered city waypoints and are projected onto the terrain.
They are schematic intercity corridors, not road-network navigation. The displayed
progress advances with configured speed and playback rate in compressed simulation
time. Arriving vehicles remain at the destination; stopped and delivered vehicles
do not move. The editor's transport status is a configured status and arrival does
not imply real proof of delivery. Replaying or editing the dataset resets the
simulation to configured starting progress.

Up to 40 orders are validated and stored under `jhds.logistics.virtual.v2` in
browser localStorage. Editing, deletion and JSON import/export affect this browser
only. Imports replace the current virtual dataset. JSON is capped at 200 KB and
rejects duplicate order/vehicle IDs, unknown cities, repeated stops, malformed
numbers and non-integer parcel counts. The editor preserves waypoint order and
supports reordering. City and hotspot selections show linked orders and parcel
totals. Search covers identifiers and all route stops.

For a real fleet integration, first define a geographic-to-model transformation
or replace the base with georeferenced GIS terrain. Then add an authenticated
read-only fleet-state API with timestamps, stale-data handling and GPS map matching.
Do not feed longitude/latitude directly into these model XY anchors.

## Rendering And Controls

Local Three.js r169 loads the original GLB. Truck parts are instanced across all
vehicles, with cab, glass, cooling unit, colored side strips, wheels, hubs, door
rails and lights. Routes distinguish planned corridors and traveled sections.
City labels avoid one another; selected vehicles have a separate label. Five
hotspots aggregate linked parcel counts. Transparent volumes do not intercept
clicks on visible vehicles.

Label positions are projected after the camera update on every rendered frame.
Only sidebar telemetry remains throttled to 120 ms. Viewport dimensions, label
bounds and selection-based label order are cached; subpixel `translate3d` changes
are written only when needed. Collision entry spacing reduces boundary flicker.

Orbit, zoom, reset, top view, vehicle follow, pause, replay, speed and layer controls
are available. Manual orbit stops following. Hidden/offscreen maps stop rendering
and simulation advancement. Rendering is capped at 60 frames per second and pixel
ratio is capped at 1.5, with no shadow maps or
postprocessing. The default 8-order scene uses 104 draw calls and about 107,652
triangles. The 40-order ceiling bounds dynamic scene growth. Model/geometry,
materials, instancing buffers and observers are disposed on page exit.

## Verification

```powershell
mvn.cmd -q resources:resources
node --test scripts/web_twin/logistics-data.test.cjs
node scripts/web_twin/logistics.browser.cjs
node scripts/web_twin/logistics-labels.browser.cjs
```

The browser check uses the existing local Chrome/Playwright setup. Override
`TWIN_TEST_URL`, `CHROME_PATH` and `CODEX_NODE_MODULES` when needed. It blocks API
writes, uses isolated browser storage, and covers 1920x1080, 1440x1000, 1366x768,
1024x900 and 390x844.
Checks include nonblank canvas pixels, animation/pause, stationary arrivals,
clicks, following, filtering, layers, CRUD, ordered stops, persistence, JSON round
trips, popup reuse, 40-order rendering, hidden-map suspension, the 70% desktop
layout, responsive panel ordering and side-card data updates.
The focused label test checks each visible city label against the rendered camera
during wheel zoom, dragging and damping on desktop/mobile, including paused
simulation, overlap checks and city picking. `LABEL_TEST_THROTTLED=1` enables an
intentional 120 ms negative control that should fail the same-frame assertion.

Screenshots and measured load/render statistics are generated in
`artifacts/logistics/`. These are local headless Chrome measurements, not a
guarantee of frame rate on every user's GPU or network.
