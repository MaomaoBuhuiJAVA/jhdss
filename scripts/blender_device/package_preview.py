"""Package rendered previews and the portable Blender add-on."""
from pathlib import Path
import shutil
import json
from PIL import Image, ImageChops, ImageStat

root=Path(__file__).resolve().parents[2]
out=root/"artifacts"/"blender-device"
files=sorted((out/"motion_frames").glob("*.png"))
frames=[Image.open(p).convert("RGB") for p in files]
if len(frames)!=24:
    raise RuntimeError("Expected 24 rendered motion frames")
difference=ImageChops.difference(frames[0],frames[6])
if max(ImageStat.Stat(difference).mean)<.5:
    raise RuntimeError("Motion preview did not visibly change")
frames[0].save(out/"camera_motion_demo.gif",save_all=True,append_images=frames[1:],duration=160,loop=0,optimize=True)
shutil.copy2(root/"scripts"/"blender_device"/"device_twin_sync.py",out/"device_twin_sync.py")
print(json.dumps({"frames":len(frames),"mean_pixel_motion":ImageStat.Stat(difference).mean,"gif_bytes":(out/"camera_motion_demo.gif").stat().st_size}))
