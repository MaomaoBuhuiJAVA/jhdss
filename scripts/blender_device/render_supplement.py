"""Render photo-comparison views without changing the saved model."""
import bpy
import sys
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

HERE=Path(__file__).resolve().parent
OUT=HERE.parents[1]/"artifacts"/"blender-device"
out=OUT if "--final" in sys.argv else OUT/"revisions"/"pass1"
out.mkdir(exist_ok=True)
sys.path.insert(0,str(HERE))
import device_twin_sync as sync
sync.register()
scene=bpy.context.scene
s=scene.device_twin
s.mode="MANUAL"
s.axis_x=.43
s.axis_y=.38
s.pan_deg=0
s.tilt_deg=-12
sync.apply_pose(scene)
bpy.context.view_layer.update()
prefs=bpy.context.preferences.addons["cycles"].preferences
prefs.compute_device_type="OPTIX"
prefs.get_devices()
for d in prefs.devices:
    d.use=d.type=="OPTIX"
scene.cycles.device="GPU"
scene.render.engine="CYCLES"
scene.cycles.samples=48
scene.cycles.use_denoising=True
scene.render.resolution_x=1500
scene.render.resolution_y=1250
scene.render.resolution_percentage=100
camera=scene.objects["VIEW_01 | Equipment overview"]
scene.camera=camera


def render(name,pos,target,lens=55):
    camera.location=pos
    camera.rotation_euler=(Vector(target)-camera.location).to_track_quat("-Z","Y").to_euler()
    camera.data.lens=lens
    if "04_camera" in name or "07_camera" in name:
        bpy.context.view_layer.update()
        bounds=[o.matrix_world@Vector(corner) for o in bpy.data.collections["03 | Camera and nozzle"].objects
                if o.type in {"MESH","CURVE"} for corner in o.bound_box]
        for _ in range(30):
            projected=[world_to_camera_view(scene,camera,p) for p in bounds]
            if all(.055<p.x<.945 and .055<p.y<.945 and p.z>0 for p in projected):
                break
            camera.data.lens*=.92
        else:
            raise AssertionError("Unable to frame the complete camera and nozzle")
        print("CAMERA_RIG_FRAMING_OK",name,camera.data.lens,flush=True)
    scene.render.filepath=str(out/name)
    bpy.ops.render.render(write_still=True)
    print("REVIEW_VIEW_DONE",name,flush=True)


target=scene.objects["CAMERA_TILT"].matrix_world.translation
views=[
    ("01_overview.png",(3.6,-5.8,3.8),(0,.17,1.24),51),
    ("02_rear_services.png",(-3.8,5.4,3.1),(0,.36,1.20),52),
    ("03_gantry_detail.png",(2.15,-3.3,3.95),(-.13,0,2.30),62),
    ("04_camera_closeup.png",target+Vector((.50,-.52,-.18)),target+Vector((.055,0,.08)),32),
    ("06_weather_detail.png",(2.45,-.05,2.30),(1.39,.68,2.055),52),
    ("07_camera_rear_slots.png",target+Vector((.80,.43,.13)),target+Vector((.055,0,.08)),37),
    ("08_plants_and_services.png",(-2.4,3.0,2.18),(.0,.45,.97),60)]
if scene.get("side_service_layout"):
    replacements={
        "01_overview.png":((4.7,-6.0,3.8),(.27,0,1.22),51),
        "02_rear_services.png":((5.8,.05,2.75),(1.05,0,1.12),54),
        "08_plants_and_services.png":((4.4,-2.2,2.65),(.91,0,.91),54),
    }
    views=[(name,*replacements[name]) if name in replacements else (name,pos,target,lens) for name,pos,target,lens in views]
    views.append(("10_side_service_layout.png",(6.1,-.015,2.23),(1.05,0,1.18),51))
for args in [v for v in views if "--details-only" not in sys.argv or v[0] in {"04_camera_closeup.png","07_camera_rear_slots.png"}]:
    render(*args)
if "--motion" in sys.argv:
    scene.render.engine="BLENDER_WORKBENCH"
    scene.display.shading.light="STUDIO"
    scene.display.shading.color_type="MATERIAL"
    scene.display.shading.show_shadows=True
    scene.display.shading.show_cavity=True
    scene.display.shading.cavity_type="BOTH"
    scene.render.resolution_x=900
    scene.render.resolution_y=800
    motion=OUT/"motion_frames"
    motion.mkdir(exist_ok=True)
    s.mode="DEMO"
    for i,frame in enumerate(range(1,241,10)):
        scene.frame_set(frame)
        sync.apply_pose(scene)
        bpy.context.view_layer.update()
        render(str(motion/('%03d.png'%i)),(4.2,-5.1,4.3),(.20,0,1.55),50)
print("SUPPLEMENT_REVIEW_RENDER_COMPLETE",flush=True)
