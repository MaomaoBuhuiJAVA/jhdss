import argparse
import time
from collections import defaultdict, deque
from pathlib import Path

import cv2
import torch
from ultralytics import YOLO


PROJECT_ROOT = Path(__file__).resolve().parent
DEFAULT_MODEL = PROJECT_ROOT / "weights" / "black_longhorn_best.pt"


def parse_args():
    parser = argparse.ArgumentParser(description="Real-time longhorn beetle detection")
    parser.add_argument("--model", default=str(DEFAULT_MODEL))
    parser.add_argument("--source", default="0", help="Camera index, video path, or stream URL")
    parser.add_argument("--device", default="auto", help="auto, cpu, or CUDA device such as 0")
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--confidence", type=float, default=0.25)
    parser.add_argument("--window-frames", type=int, default=5)
    parser.add_argument("--required-hits", type=int, default=3)
    parser.add_argument("--max-frame-gap", type=int, default=1)
    parser.add_argument("--minimum-average-confidence", type=float, default=0.35)
    parser.add_argument("--minimum-strong-confidence", type=float, default=0.50)
    return parser.parse_args()


def camera_source(value):
    return int(value) if value.isdigit() else value


def main():
    args = parse_args()
    model_path = Path(args.model).expanduser().resolve()
    if not model_path.is_file():
        raise FileNotFoundError(f"Model not found: {model_path}")

    window_frames = max(1, args.window_frames)
    required_hits = max(1, min(args.required_hits, window_frames))
    max_frame_gap = max(0, min(args.max_frame_gap, window_frames - 1))
    candidate_confidence = max(0.05, min(0.95, args.confidence))
    minimum_average = max(candidate_confidence, min(0.95, args.minimum_average_confidence))
    minimum_strong = max(minimum_average, min(0.99, args.minimum_strong_confidence))
    device = "0" if args.device == "auto" and torch.cuda.is_available() else (
        "cpu" if args.device == "auto" else args.device
    )

    model = YOLO(str(model_path))
    capture = cv2.VideoCapture(camera_source(args.source))
    if not capture.isOpened():
        raise RuntimeError(f"Cannot open camera or stream: {args.source}")

    histories = defaultdict(lambda: deque(maxlen=window_frames))
    last_seen = {}
    confirmed_ids = set()
    processed_frames = 0
    paused = False
    smoothed_fps = 0.0

    print(f"Model: {model_path}")
    print(f"Classes: {model.names}")
    print(f"Device: {device}")
    print(
        "Verification: "
        f"{required_hits}/{window_frames} frames, mean>={minimum_average:.2f}, "
        f"one>={minimum_strong:.2f}"
    )
    print("Press 'q' to quit, 's' to pause/resume")

    try:
        while True:
            if not paused:
                read_started = time.perf_counter()
                success, frame = capture.read()
                if not success:
                    break
                processed_frames += 1

                results = model.track(
                    frame,
                    imgsz=args.imgsz,
                    conf=candidate_confidence,
                    device=device,
                    persist=True,
                    tracker="bytetrack.yaml",
                    verbose=False,
                )
                result = results[0]
                annotated = frame.copy()
                current_ids = set()

                boxes = result.boxes
                if boxes is not None and boxes.id is not None:
                    coordinates = boxes.xyxy.cpu().tolist()
                    track_ids = boxes.id.int().cpu().tolist()
                    classes = boxes.cls.int().cpu().tolist()
                    confidences = boxes.conf.cpu().tolist()
                else:
                    coordinates, track_ids, classes, confidences = [], [], [], []

                for box, track_id, class_id, confidence in zip(
                    coordinates, track_ids, classes, confidences
                ):
                    current_ids.add(track_id)
                    last_seen[track_id] = processed_frames
                    history = histories[track_id]
                    history.append((processed_frames, float(confidence)))
                    minimum_frame = processed_frames - window_frames + 1
                    while history and history[0][0] < minimum_frame:
                        history.popleft()

                    hits = len(history)
                    average_confidence = sum(item[1] for item in history) / hits
                    maximum_confidence = max(item[1] for item in history)
                    if (
                        hits >= required_hits
                        and average_confidence >= minimum_average
                        and maximum_confidence >= minimum_strong
                    ):
                        confirmed_ids.add(track_id)
                    else:
                        confirmed_ids.discard(track_id)

                    confirmed = track_id in confirmed_ids
                    color = (0, 40, 255) if confirmed else (0, 165, 255)
                    class_name = result.names.get(class_id, str(class_id))
                    if confirmed:
                        label = (
                            f"CONFIRMED #{track_id} {class_name} "
                            f"mean={average_confidence:.2f} now={confidence:.2f}"
                        )
                    else:
                        label = f"VERIFY #{track_id} {hits}/{required_hits} conf={confidence:.2f}"
                    x1, y1, x2, y2 = (int(value) for value in box)
                    cv2.rectangle(annotated, (x1, y1), (x2, y2), color, 2)
                    cv2.putText(
                        annotated,
                        label,
                        (x1, max(22, y1 - 8)),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.55,
                        color,
                        2,
                    )

                expired_ids = [
                    track_id
                    for track_id, frame_number in last_seen.items()
                    if processed_frames - frame_number > max_frame_gap + 1
                ]
                for track_id in expired_ids:
                    histories.pop(track_id, None)
                    last_seen.pop(track_id, None)
                    confirmed_ids.discard(track_id)

                confirmed_now = len(current_ids.intersection(confirmed_ids))
                elapsed = max(time.perf_counter() - read_started, 0.0001)
                current_fps = 1.0 / elapsed
                smoothed_fps = current_fps if smoothed_fps == 0 else 0.85 * smoothed_fps + 0.15 * current_fps
                status = (
                    f"FPS {smoothed_fps:.1f} | candidates {len(current_ids)} | "
                    f"confirmed {confirmed_now} | verify {required_hits}/{window_frames}"
                )
                cv2.putText(
                    annotated,
                    status,
                    (10, 30),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.65,
                    (70, 255, 170),
                    2,
                )
                cv2.imshow("Longhorn Beetle - Multi-frame Verification", annotated)

            key = cv2.waitKey(30 if paused else 1) & 0xFF
            if key == ord("q"):
                break
            if key == ord("s"):
                paused = not paused
                print("Paused" if paused else "Resumed")
    finally:
        capture.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
