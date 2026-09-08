import argparse
import json
from pathlib import Path

import cv2
from ultralytics import YOLO


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--model', required=True)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--conf', type=float, default=0.25)
    args = parser.parse_args()

    model = YOLO(args.model)
    results = model.predict(source=args.input, imgsz=1024, conf=args.conf,
                            device=0, verbose=False, save=False)
    result = results[0]
    image = cv2.imread(args.input)
    if image is None:
        raise RuntimeError('无法读取上传图片')

    detections = []
    if result.boxes is not None:
        for cls, confidence, xyxy in zip(result.boxes.cls.tolist(),
                                          result.boxes.conf.tolist(),
                                          result.boxes.xyxy.tolist()):
            detections.append({
                'class': result.names[int(cls)],
                'confidence': round(float(confidence), 4),
                'box': [round(float(value), 1) for value in xyxy],
            })

    plotted = result.plot(conf=True, labels=True, boxes=True)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(output), plotted):
        raise RuntimeError('无法保存识别结果图片')

    print(json.dumps({
        'width': int(result.orig_shape[1]),
        'height': int(result.orig_shape[0]),
        'detections': detections,
    }, ensure_ascii=False))


if __name__ == '__main__':
    main()
