import bpy
import json
import time
import device_twin_sync as sync
scene=bpy.context.scene
s=scene.device_twin
sync.tick()
bpy.context.view_layer.update()
camera=scene.objects["CAMERA_TILT"]
report={"mode":s.mode,"status":s.status,"detail":s.detail,"normalised_x":s.axis_x,"normalised_y":s.axis_y,"camera_world":list(camera.matrix_world.translation),"packet_age_seconds":time.monotonic()-sync._received if sync._received else None}
print(json.dumps(report))
if s.status.startswith("LIVE"):
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
