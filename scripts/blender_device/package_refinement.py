"""Create a compact review sheet from the actual final Blender renders."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps, ImageStat

out=Path(__file__).resolve().parents[2]/"artifacts"/"blender-device"
sheet=Image.new("RGB",(1800,1592),(237,240,241))
draw=ImageDraw.Draw(sheet)
font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",22)
views=[("01_overview.png","01  EQUIPMENT"),("06_weather_detail.png","02  WEATHER STATION"),
       ("04_camera_closeup.png","03  CAMERA / NOZZLE"),("08_plants_and_services.png","04  PLANTS / SERVICES")]
reports=[]
for i,(filename,title) in enumerate(views):
    image=Image.open(out/filename).convert("RGB")
    stat=ImageStat.Stat(image)
    assert min(stat.stddev)>8,filename+" is blank or low contrast"
    x=(i%2)*900
    y=(i//2)*796
    draw.text((x+18,y+13),title,font=font,fill=(32,40,43))
    preview=ImageOps.contain(image,(892,742),Image.Resampling.LANCZOS)
    sheet.paste(preview,(x+(900-preview.width)//2,y+49))
    reports.append({"file":filename,"size":list(image.size),"pixel_standard_deviation":stat.stddev})
sheet.save(out/"09_refinement_review.jpg",quality=94)
(out/"preview_verification.json").write_text(json.dumps(reports,indent=2),encoding="utf-8")
print("REVIEW_SHEET_READY",out/"09_refinement_review.jpg")
