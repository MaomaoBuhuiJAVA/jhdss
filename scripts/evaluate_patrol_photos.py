import argparse
import csv
import json
from collections import Counter
from pathlib import Path

import cv2
import numpy as np
from ultralytics import YOLO


IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
CLASS_LABELS = {
    "longhornbeetle": "桃红颈天牛",
    "longhorn_beetle": "桃红颈天牛",
    "ripecherries": "成熟果实",
    "ripe_cherries": "成熟果实",
    "unripecherries": "未成熟果实",
    "unripe_cherries": "未成熟果实",
}


def localized_label(value):
    normalized = str(value).strip().lower().replace(" ", "").replace("-", "_")
    return CLASS_LABELS.get(normalized, CLASS_LABELS.get(normalized.replace("_", ""), str(value)))


def read_image(path):
    return cv2.imdecode(np.fromfile(str(path), dtype=np.uint8), cv2.IMREAD_COLOR)


def write_image(path, image):
    path.parent.mkdir(parents=True, exist_ok=True)
    suffix = path.suffix if path.suffix else ".jpg"
    ok, encoded = cv2.imencode(suffix, image)
    if not ok:
        raise RuntimeError("无法编码标注图片: {}".format(path))
    encoded.tofile(str(path))


def main():
    parser = argparse.ArgumentParser(description="批量检测自动巡检照片")
    parser.add_argument("--model", required=True)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--imgsz", type=int, default=1024)
    parser.add_argument("--date", help="只检测文件名中包含的巡检日期（YYYYMMDD）")
    args = parser.parse_args()

    if args.date and (len(args.date) != 8 or not args.date.isdigit()):
        parser.error("--date 必须为 YYYYMMDD 格式")

    input_dir = Path(args.input).resolve()
    output_dir = Path(args.output).resolve()
    annotated_dir = output_dir / "annotated"
    images = sorted(
        path for path in input_dir.iterdir()
        if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES
        and (not args.date or "_{}_".format(args.date) in path.name)
    )
    if not images:
        date_message = "，巡检日期 {}".format(args.date) if args.date else ""
        raise RuntimeError("输入目录没有可识别图片: {}{}".format(input_dir, date_message))

    output_dir.mkdir(parents=True, exist_ok=True)
    model = YOLO(args.model)
    rows = []
    image_results = []
    class_counts = Counter()
    detected_images = 0

    for index, image_path in enumerate(images, 1):
        image = read_image(image_path)
        if image is None:
            image_results.append({"image": image_path.name, "error": "无法读取图片", "detections": []})
            continue
        result = model.predict(
            image, imgsz=args.imgsz, conf=args.conf,
            device=args.device, verbose=False, save=False
        )[0]
        detections = []
        if result.boxes is not None:
            for class_id, confidence, box in zip(
                    result.boxes.cls.tolist(), result.boxes.conf.tolist(), result.boxes.xyxy.tolist()):
                raw_label = result.names[int(class_id)]
                label = localized_label(raw_label)
                detection = {
                    "label": label,
                    "sourceLabel": raw_label,
                    "confidence": round(float(confidence), 4),
                    "box": [round(float(value), 1) for value in box],
                }
                detections.append(detection)
                class_counts[label] += 1
                rows.append({
                    "image": image_path.name,
                    "label": label,
                    "source_label": raw_label,
                    "confidence": detection["confidence"],
                    "x1": detection["box"][0],
                    "y1": detection["box"][1],
                    "x2": detection["box"][2],
                    "y2": detection["box"][3],
                })
        if detections:
            detected_images += 1
            write_image(annotated_dir / image_path.name, result.plot(conf=True, labels=True, boxes=True))
        image_results.append({
            "image": image_path.name,
            "width": int(result.orig_shape[1]),
            "height": int(result.orig_shape[0]),
            "detected": bool(detections),
            "detections": detections,
        })
        print("[{}/{}] {}: {}".format(index, len(images), image_path.name, len(detections)), flush=True)

    summary = {
        "model": str(Path(args.model).resolve()),
        "input": str(input_dir),
        "testDate": args.date,
        "confidence": args.conf,
        "imageCount": len(images),
        "detectedImageCount": detected_images,
        "undetectedImageCount": len(images) - detected_images,
        "detectionCount": sum(class_counts.values()),
        "classCounts": dict(class_counts),
        "labels": ["桃红颈天牛", "成熟果实", "未成熟果实"],
    }
    (output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (output_dir / "results.json").write_text(
        json.dumps({"summary": summary, "images": image_results}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    with (output_dir / "detections.csv").open("w", encoding="utf-8-sig", newline="") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=[
            "image", "label", "source_label", "confidence", "x1", "y1", "x2", "y2"
        ])
        writer.writeheader()
        writer.writerows(rows)
    print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
