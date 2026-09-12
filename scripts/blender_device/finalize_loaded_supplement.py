"""Finish publishing after the MCP-triggered file-load event has settled."""
import ast
import bpy
import json
import traceback
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

project=Path(r"C:/Users/Administrator/Documents/Codex/2026-08-26/https-github-com-maomaobuhuijava-jhdss-git/jhdss")
out=project/"artifacts"/"blender-device"
backup=sorted((out/"revisions").glob("Before_Refined_Apply_*.blend"))[-1]
assert bpy.context.scene.get("supplement_review_complete")
source=ast.parse((project/"scripts"/"blender_device"/"publish_supplement.py").read_text(encoding="utf-8"))
function=next(n for n in source.body if isinstance(n,ast.FunctionDef) and n.name=="finish_open")
module=ast.Module(body=function.body[1:-1],type_ignores=[])
try:
    exec(compile(module,"publish_supplement.py","exec"),globals())
except Exception:
    print(traceback.format_exc())
    raise
print((out/"publish_verification.json").read_text(encoding="utf-8"))
