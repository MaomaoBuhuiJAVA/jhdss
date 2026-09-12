import bpy
import json
import device_twin_sync as sync
scene=bpy.context.scene
s=scene.device_twin
s.mode="LIVE"
s.url="http://127.0.0.1:9117/jhds/api/patrol/auto/status"
sync.start_worker(s.url,s.poll_interval)
scene.camera=scene.objects["VIEW_01 | Equipment overview"]
bpy.ops.object.select_all(action="DESELECT")
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=="VIEW_3D":
            space=area.spaces.active
            space.region_3d.view_perspective="CAMERA"
            space.region_3d.view_camera_zoom=6
            space.shading.type="MATERIAL"
            space.overlay.show_overlays=False
            space.show_region_ui=True
print(json.dumps({"file":bpy.data.filepath,"objects":len(scene.objects),"mode":s.mode,"source":s.url}))
