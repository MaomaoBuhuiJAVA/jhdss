"""Render review angles and a compact, explicitly simulated motion preview."""
import bpy
import importlib
import sys
from pathlib import Path
from mathutils import Vector
HERE=Path(__file__).resolve().parent
OUT=HERE.parents[1]/"artifacts"/"blender-device"
sys.path.insert(0,str(HERE))
import device_twin_sync as sync
if not hasattr(bpy.types.Scene,"device_twin"):
    sync.register()
scene=bpy.context.scene
settings=scene.device_twin
settings.mode="MANUAL"
settings.axis_x=.43
settings.axis_y=.38
sync.apply_pose(scene)
scene.render.resolution_x=1400
scene.render.resolution_y=1150
scene.cycles.samples=36
try:
    prefs=bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type="OPTIX"
    prefs.get_devices()
    available=[d for d in prefs.devices if d.type=="OPTIX"]
    for d in prefs.devices:
        d.use=d.type=="OPTIX"
    if available:
        scene.cycles.device="GPU"
        print("RENDER_DEVICE",available[0].name,flush=True)
except Exception as exc:
    print("CPU_RENDER",str(exc),flush=True)
views=[] if "--close-only" in sys.argv else [("VIEW_01 | Equipment overview","01_overview.png"),("VIEW_02 | Rear services","02_rear_services.png"),("VIEW_03 | XY camera and chain","03_gantry_detail.png")]
for camera,filename in views:
    scene.camera=scene.objects[camera]
    scene.render.filepath=str(OUT/filename)
    bpy.ops.render.render(write_still=True)
    print("VIEW_DONE",filename,flush=True)
camera=scene.objects["VIEW_03 | XY camera and chain"]
camera.location=(.95,-.57,2.43)
camera.rotation_euler=(Vector((-.09,-.08,2.23))-camera.location).to_track_quat("-Z","Y").to_euler()
camera.data.lens=57
scene.camera=camera
scene.render.filepath=str(OUT/"04_camera_closeup.png")
bpy.ops.render.render(write_still=True)
print("VIEW_DONE 04_camera_closeup.png",flush=True)
if "--motion" in sys.argv:
    motion=OUT/"motion_frames"
    motion.mkdir(exist_ok=True)
    camera=scene.objects["VIEW_01 | Equipment overview"]
    camera.location=(3.4,-4.8,4.3)
    camera.rotation_euler=(Vector((0,0,1.65))-camera.location).to_track_quat("-Z","Y").to_euler()
    camera.data.lens=50
    scene.camera=camera
    scene.render.engine="BLENDER_WORKBENCH"
    scene.display.shading.light="STUDIO"
    scene.display.shading.color_type="MATERIAL"
    scene.display.shading.show_shadows=True
    scene.display.shading.show_cavity=True
    scene.display.shading.cavity_type="BOTH"
    scene.display.shading.background_type="WORLD"
    scene.world.color=(.16,.18,.19)
    scene.render.resolution_x=900
    scene.render.resolution_y=800
    settings.mode="DEMO"
    for i,frame in enumerate(range(1,241,10)):
        scene.frame_set(frame)
        sync.apply_pose(scene)
        bpy.context.view_layer.update()
        scene.render.filepath=str(motion/('%03d.png'%i))
        bpy.ops.render.render(write_still=True)
    print("MOTION_FRAMES_DONE",flush=True)
