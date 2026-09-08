import argparse
import base64
import json
import sys

import cv2
import numpy as np
from ultralytics import YOLO


def respond(payload):
    sys.stdout.write(json.dumps(payload, ensure_ascii=False) + '\n')
    sys.stdout.flush()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--model', required=True)
    parser.add_argument('--device', default='0')
    parser.add_argument('--imgsz', type=int, default=1024)
    args = parser.parse_args()

    model = YOLO(args.model)
    # Warm the model once. Later frames avoid model loading and CUDA setup cost.
    model.predict(np.zeros((640, 640, 3), dtype=np.uint8), imgsz=args.imgsz,
                  conf=0.25, device=args.device, verbose=False)
    respond({'ready': True})

    for raw in sys.stdin:
        try:
            request = json.loads(raw)
            encoded = request.get('image', '')
            image = cv2.imdecode(np.frombuffer(base64.b64decode(encoded), np.uint8), cv2.IMREAD_COLOR)
            if image is None:
                raise ValueError('无法解析视频帧')
            confidence = max(0.05, min(0.95, float(request.get('confidence', 0.25))))
            result = model.predict(image, imgsz=args.imgsz, conf=confidence,
                                   device=args.device, verbose=False)[0]
            detections = []
            if result.boxes is not None:
                for cls, score, xyxy in zip(result.boxes.cls.tolist(), result.boxes.conf.tolist(), result.boxes.xyxy.tolist()):
                    detections.append({
                        'class': result.names[int(cls)],
                        'confidence': round(float(score), 4),
                        'box': [round(float(value), 1) for value in xyxy],
                    })
            respond({
                'id': request.get('id'),
                'width': int(image.shape[1]),
                'height': int(image.shape[0]),
                'detections': detections,
            })
        except Exception as error:
            respond({'error': str(error)})


if __name__ == '__main__':
    main()
