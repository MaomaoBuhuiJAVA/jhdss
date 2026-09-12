"""Install the tested chain update, preserve the live scene and load the refined model via MCP."""
import bpy
import importlib
import json
import sys
from datetime import datetime
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

project=Path(r"C:/Users/Administrator/Documents/Codex/2026-08-26/https-github-com-maomaobuhuijava-jhdss-git/jhdss")
out=project/"artifacts"/"blender-device"
stage=out/"revisions"/"Supplement_Refined_Final.blend"
assert stage.exists()
backup=out/"revisions"/("Before_Refined_Apply_"+datetime.now().strftime("%Y%m%d_%H%M%S")+".blend")
bpy.ops.wm.save_as_mainfile(filepath=str(backup),copy=True)
if "device_twin_sync" in bpy.context.preferences.addons:
    bpy.ops.preferences.addon_disable(module="device_twin_sync")
bpy.ops.preferences.addon_install(filepath=str(project/"scripts"/"blender_device"/"device_twin_sync.py"),overwrite=True)
importlib.invalidate_caches()
if "device_twin_sync" in sys.modules:
    importlib.reload(sys.modules["device_twin_sync"])
bpy.ops.preferences.addon_enable(module="device_twin_sync")
bpy.ops.wm.save_userpref()

def finish_open():
    bpy.ops.wm.open_mainfile(filepath=str(stage))
    import device_twin_sync as sync
    scene=bpy.context.scene
    settings=scene.device_twin
    settings.mode="MANUAL"
    settings.axis_x=.43
    settings.axis_y=.38
    sync.apply_pose(scene)
    bpy.context.view_layer.update()
    for name,collection in [("Camera lens retaining ring","03 | Camera and nozzle"),("Weather shield offset support","05 | Cabinet reservoir and instruments")]:
        obj=scene.objects[name]
        for old in list(obj.users_collection):
            old.objects.unlink(obj)
        bpy.data.collections[collection].objects.link(obj)
    # Detail cameras follow the carriage so their views remain usable in Live mode.
    for name in ["VIEW_05 | Camera housing and nozzle","VIEW_07 | Camera rear through slots"]:
        camera=scene.objects[name]
        bounds=[o.matrix_world@Vector(c) for o in bpy.data.collections["03 | Camera and nozzle"].objects if o.type in {"MESH","CURVE"} for c in o.bound_box]
        for _ in range(30):
            projected=[world_to_camera_view(scene,camera,p) for p in bounds]
            if all(.055<p.x<.945 and .055<p.y<.945 and p.z>0 for p in projected):
                break
            camera.data.lens*=.92
        else:
            raise AssertionError("Detail camera framing failed")
        world=camera.matrix_world.copy()
        camera.parent=scene.objects["CARRIAGE_Y"]
        camera.matrix_world=world
    before=scene.objects["CAMERA_TILT"].matrix_world.translation.copy()
    settings.axis_x=.73
    settings.axis_y=.68
    sync.apply_pose(scene)
    bpy.context.view_layer.update()
    after=scene.objects["CAMERA_TILT"].matrix_world.translation.copy()
    assert abs(after.x-before.x-.618)<1e-5
    assert abs(after.y-before.y-.342)<1e-5
    settings.axis_x=.43
    settings.axis_y=.38
    sync.apply_pose(scene)
    settings.mode="LIVE"
    scene.camera=scene.objects["VIEW_01 | Equipment overview"]
    text=bpy.data.texts.get("device_twin_sync.py")
    text.clear()
    text.write((project/"scripts"/"blender_device"/"device_twin_sync.py").read_text(encoding="utf-8"))
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type=="VIEW_3D":
                space=area.spaces.active
                space.shading.type="MATERIAL"
                space.overlay.show_overlays=False
                space.show_region_ui=True
                space.region_3d.view_perspective="PERSP"
                space.region_3d.view_location=(0,.15,1.24)
                space.region_3d.view_distance=5.5
                space.region_3d.view_rotation=scene.camera.rotation_euler.to_quaternion()
    bpy.ops.wm.save_as_mainfile(filepath=str(out/"JHDS_Inspection_Gantry.blend"))
    report={"file":bpy.data.filepath,"objects":len(scene.objects),"addon_version":list(sync.bl_info["version"]),"mode":settings.mode,"gui_motion_check":True,"backup":str(backup)}
    (out/"publish_verification.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    return None

bpy.app.timers.register(finish_open,first_interval=.5)
print("Opening refined model. Previous live scene preserved:",backup)
