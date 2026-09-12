import bpy
import json
scene=bpy.context.scene
bpy.ops.object.select_all(action="DESELECT")
for obj in scene.objects:
    if obj.type in ("MESH","CURVE","FONT") and not any(c.name.startswith("06 |") or c.name.startswith("07 |") for c in obj.users_collection):
        obj.select_set(True)
for window in bpy.context.window_manager.windows:
    for area in window.screen.areas:
        if area.type=="VIEW_3D":
            region=next(r for r in area.regions if r.type=="WINDOW")
            space=area.spaces.active
            space.region_3d.view_perspective="PERSP"
            space.region_3d.view_rotation=scene.objects["VIEW_01 | Equipment overview"].rotation_euler.to_quaternion()
            space.lens=50
            with bpy.context.temp_override(window=window,area=area,region=region):
                bpy.ops.view3d.view_selected(use_all_regions=False)
            space.region_3d.update()
            area.tag_redraw()
            print(json.dumps({"distance":space.region_3d.view_distance,"target":list(space.region_3d.view_location),"perspective":space.region_3d.view_perspective}))
bpy.ops.object.select_all(action="DESELECT")
print("PANEL_OPS",[n for n in dir(bpy.ops.wm) if "panel" in n or "category" in n])
bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
