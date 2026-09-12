import bpy
import json
print(json.dumps({
    "file": bpy.data.filepath,
    "version": bpy.app.version_string,
    "scenes": [s.name for s in bpy.data.scenes],
    "scene": bpy.context.scene.name,
    "objects": [{"name": o.name, "type": o.type} for o in bpy.context.scene.objects][:30],
    "addons": list(bpy.context.preferences.addons.keys()),
}, ensure_ascii=True))
