"""Install the model-only sync panel and open the finished scene in live Blender."""
import bpy
from pathlib import Path
project=Path(r"C:/Users/Administrator/Documents/Codex/2026-08-26/https-github-com-maomaobuhuijava-jhdss-git/jhdss")
out=project/"artifacts"/"blender-device"
backup=out/"Before_Device_Model.blend"
if not backup.exists():
    bpy.ops.wm.save_as_mainfile(filepath=str(backup),copy=True)
bpy.ops.preferences.addon_install(filepath=str(project/"scripts"/"blender_device"/"device_twin_sync.py"),overwrite=True)
bpy.ops.preferences.addon_enable(module="device_twin_sync")
bpy.ops.wm.save_userpref()
def open_model():
    bpy.ops.wm.open_mainfile(filepath=str(out/"JHDS_Inspection_Gantry.blend"))
    return None
bpy.app.timers.register(open_model,first_interval=.5)
print("Device Twin installed. Opening the model; previous scene preserved in Before_Device_Model.blend")
