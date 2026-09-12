import bpy
import json
import shutil
from pathlib import Path
out=Path(r"C:/Users/Administrator/Documents/Codex/2026-08-26/https-github-com-maomaobuhuijava-jhdss-git/jhdss/artifacts/blender-device")
revisions=out/"revisions"
revisions.mkdir(exist_ok=True)
backup=revisions/"Before_Supplement_Photos.blend"
if not backup.exists():
    bpy.ops.wm.save_as_mainfile(filepath=str(backup),copy=True)
for name in ["01_overview.png","04_camera_closeup.png"]:
    target=revisions/("v1_"+name)
    if not target.exists() and (out/name).exists():
        shutil.copy2(out/name,target)
print(json.dumps({"current_file":bpy.data.filepath,"backup":str(backup),"objects":len(bpy.context.scene.objects),"mode":bpy.context.scene.device_twin.mode,"status":bpy.context.scene.device_twin.status}))
