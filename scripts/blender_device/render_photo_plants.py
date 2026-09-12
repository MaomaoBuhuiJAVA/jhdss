"""Review render only: does not save or mutate the authored blend on disk."""
import bpy
from pathlib import Path
import sys
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/"artifacts/blender-device/revisions/new-plants"
scene = bpy.context.scene
detail = max(o.get("detail_pass", 0) for o in scene.objects)
scene.render.engine = "CYCLES"
scene.cycles.samples = 40 if detail < 2 else 64
scene.cycles.use_denoising = True
scene.cycles.denoising_use_gpu = False
try:
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "OPTIX"
    prefs.get_devices()
    for device in prefs.devices:
        device.use = device.type == "OPTIX"
    scene.cycles.device = "GPU" if any(d.use for d in prefs.devices) else "CPU"
except Exception:
    scene.cycles.device = "CPU"
scene.render.resolution_x = 1450 if detail < 2 else 1800
scene.render.resolution_y = 1300 if detail < 2 else 1600
scene.render.resolution_percentage = 100
camera = scene.camera.copy()
camera.data = scene.camera.data.copy()
scene.collection.objects.link(camera)
scene.camera = camera
camera.data.dof.use_dof = False
views = [
    ("overview", (4.7, -6, 3.8), (.27, 0, 1.22), 51),
    ("plants", (3.7, -4.7, 3.35), (0, 0, 1.25), 72),
    ("botanical", (.62, -1.55, 1.89), (.82, -.30, 1.55), 55),
]
if "--overview-only" in sys.argv:
    views = views[:1]
for name, position, target, lens in views:
    camera.location = position
    camera.rotation_euler = (Vector(target)-camera.location).to_track_quat("-Z", "Y").to_euler()
    camera.data.lens = lens
    scene.render.filepath = str(OUT/f"pass{detail}_{name}.png")
    bpy.ops.render.render(write_still=True)
    print("RENDERED", scene.render.filepath, flush=True)
