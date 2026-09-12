package com.jhds.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Confirms detections only after the same object is observed across multiple frames. */
final class TemporalDetectionVerifier {

    private final int windowFrames;
    private final int requiredHits;
    private final int maxFrameGap;
    private final double minimumIou;
    private final double minimumAverageConfidence;
    private final double minimumStrongConfidence;
    private final List<Track> tracks = new ArrayList<>();

    private long frameNumber;
    private long nextTrackId = 1;

    TemporalDetectionVerifier(int windowFrames, int requiredHits, int maxFrameGap,
                              double minimumIou, double minimumAverageConfidence,
                              double minimumStrongConfidence) {
        this.windowFrames = Math.max(1, windowFrames);
        this.requiredHits = Math.max(1, Math.min(requiredHits, this.windowFrames));
        this.maxFrameGap = Math.max(0, Math.min(maxFrameGap, this.windowFrames - 1));
        this.minimumIou = clamp(minimumIou, 0.0, 1.0);
        this.minimumAverageConfidence = clamp(minimumAverageConfidence, 0.0, 1.0);
        this.minimumStrongConfidence = clamp(minimumStrongConfidence, 0.0, 1.0);
    }

    VerificationResult update(List<Map<String, Object>> rawDetections, int imageWidth, int imageHeight) {
        frameNumber++;
        pruneExpiredTracks();

        List<Candidate> candidates = parseCandidates(rawDetections, imageWidth, imageHeight);
        Collections.sort(candidates, Comparator.comparingDouble(Candidate::confidence).reversed());
        Set<Long> matchedTrackIds = new HashSet<>();
        List<Map<String, Object>> annotatedCandidates = new ArrayList<>();
        List<Map<String, Object>> confirmedDetections = new ArrayList<>();
        int maximumHits = 0;

        for (Candidate candidate : candidates) {
            Track track = bestMatchingTrack(candidate, matchedTrackIds);
            if (track == null) {
                track = new Track(nextTrackId++, candidate.className, candidate.normalizedBox);
                tracks.add(track);
            }
            matchedTrackIds.add(track.id);
            track.observe(frameNumber, candidate.confidence, candidate.normalizedBox);
            track.pruneBefore(frameNumber - windowFrames + 1);
            track.confirmed = track.hitCount() >= requiredHits
                    && track.averageConfidence() >= minimumAverageConfidence
                    && track.maximumConfidence() >= minimumStrongConfidence;

            Map<String, Object> annotated = new LinkedHashMap<>(candidate.source);
            annotated.put("trackId", track.id);
            annotated.put("hits", track.hitCount());
            annotated.put("requiredHits", requiredHits);
            annotated.put("windowFrames", windowFrames);
            annotated.put("averageConfidence", round(track.averageConfidence()));
            annotated.put("maximumConfidence", round(track.maximumConfidence()));
            annotated.put("confirmed", track.confirmed);
            annotatedCandidates.add(annotated);
            maximumHits = Math.max(maximumHits, track.hitCount());
            if (track.confirmed) confirmedDetections.add(annotated);
        }

        return new VerificationResult(annotatedCandidates, confirmedDetections, maximumHits,
                windowFrames, requiredHits, minimumAverageConfidence, minimumStrongConfidence);
    }

    private Track bestMatchingTrack(Candidate candidate, Set<Long> matchedTrackIds) {
        Track best = null;
        double bestIou = minimumIou;
        for (Track track : tracks) {
            if (matchedTrackIds.contains(track.id) || !track.className.equals(candidate.className)
                    || frameNumber - track.lastSeenFrame > maxFrameGap + 1L) {
                continue;
            }
            double iou = intersectionOverUnion(track.lastBox, candidate.normalizedBox);
            if (iou >= bestIou) {
                bestIou = iou;
                best = track;
            }
        }
        return best;
    }

    private void pruneExpiredTracks() {
        tracks.removeIf(track -> frameNumber - track.lastSeenFrame > maxFrameGap + 1L);
        for (Track track : tracks) track.pruneBefore(frameNumber - windowFrames + 1);
    }

    private List<Candidate> parseCandidates(List<Map<String, Object>> rawDetections,
                                            int imageWidth, int imageHeight) {
        if (rawDetections == null || imageWidth <= 0 || imageHeight <= 0) {
            return Collections.emptyList();
        }
        List<Candidate> parsed = new ArrayList<>();
        for (Map<String, Object> detection : rawDetections) {
            if (detection == null || !(detection.get("confidence") instanceof Number)
                    || !(detection.get("box") instanceof List)) {
                continue;
            }
            List<?> box = (List<?>) detection.get("box");
            if (box.size() != 4 || !allNumbers(box)) continue;
            double[] normalized = new double[] {
                    number(box.get(0)) / imageWidth,
                    number(box.get(1)) / imageHeight,
                    number(box.get(2)) / imageWidth,
                    number(box.get(3)) / imageHeight
            };
            if (normalized[2] <= normalized[0] || normalized[3] <= normalized[1]) continue;
            String className = String.valueOf(detection.get("class"));
            parsed.add(new Candidate(detection, className,
                    ((Number) detection.get("confidence")).doubleValue(), normalized));
        }
        return parsed;
    }

    private static boolean allNumbers(List<?> values) {
        for (Object value : values) if (!(value instanceof Number)) return false;
        return true;
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    private static double intersectionOverUnion(double[] first, double[] second) {
        double left = Math.max(first[0], second[0]);
        double top = Math.max(first[1], second[1]);
        double right = Math.min(first[2], second[2]);
        double bottom = Math.min(first[3], second[3]);
        double intersection = Math.max(0.0, right - left) * Math.max(0.0, bottom - top);
        double firstArea = Math.max(0.0, first[2] - first[0]) * Math.max(0.0, first[3] - first[1]);
        double secondArea = Math.max(0.0, second[2] - second[0]) * Math.max(0.0, second[3] - second[1]);
        double union = firstArea + secondArea - intersection;
        return union <= 0.0 ? 0.0 : intersection / union;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    static final class VerificationResult {
        private final List<Map<String, Object>> candidates;
        private final List<Map<String, Object>> confirmed;
        private final int maximumHits;
        private final int windowFrames;
        private final int requiredHits;
        private final double minimumAverageConfidence;
        private final double minimumStrongConfidence;

        private VerificationResult(List<Map<String, Object>> candidates,
                                   List<Map<String, Object>> confirmed,
                                   int maximumHits, int windowFrames, int requiredHits,
                                   double minimumAverageConfidence,
                                   double minimumStrongConfidence) {
            this.candidates = candidates;
            this.confirmed = confirmed;
            this.maximumHits = maximumHits;
            this.windowFrames = windowFrames;
            this.requiredHits = requiredHits;
            this.minimumAverageConfidence = minimumAverageConfidence;
            this.minimumStrongConfidence = minimumStrongConfidence;
        }

        List<Map<String, Object>> candidates() { return candidates; }
        List<Map<String, Object>> confirmed() { return confirmed; }
        int maximumHits() { return maximumHits; }

        Map<String, Object> summary() {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("state", !confirmed.isEmpty() ? "confirmed"
                    : candidates.isEmpty() ? "clear" : "verifying");
            summary.put("windowFrames", windowFrames);
            summary.put("requiredHits", requiredHits);
            summary.put("maximumHits", maximumHits);
            summary.put("minimumAverageConfidence", minimumAverageConfidence);
            summary.put("minimumStrongConfidence", minimumStrongConfidence);
            return summary;
        }
    }

    private static final class Candidate {
        private final Map<String, Object> source;
        private final String className;
        private final double confidence;
        private final double[] normalizedBox;

        private Candidate(Map<String, Object> source, String className,
                          double confidence, double[] normalizedBox) {
            this.source = source;
            this.className = className;
            this.confidence = confidence;
            this.normalizedBox = normalizedBox;
        }

        private double confidence() { return confidence; }
    }

    private static final class Track {
        private final long id;
        private final String className;
        private final Deque<Observation> observations = new ArrayDeque<>();
        private double[] lastBox;
        private long lastSeenFrame;
        private boolean confirmed;

        private Track(long id, String className, double[] initialBox) {
            this.id = id;
            this.className = className;
            this.lastBox = initialBox;
        }

        private void observe(long frame, double confidence, double[] box) {
            observations.addLast(new Observation(frame, confidence));
            lastSeenFrame = frame;
            lastBox = box;
        }

        private void pruneBefore(long minimumFrame) {
            while (!observations.isEmpty() && observations.peekFirst().frame < minimumFrame) {
                observations.removeFirst();
            }
        }

        private int hitCount() { return observations.size(); }

        private double averageConfidence() {
            if (observations.isEmpty()) return 0.0;
            double total = 0.0;
            for (Observation observation : observations) total += observation.confidence;
            return total / observations.size();
        }

        private double maximumConfidence() {
            double maximum = 0.0;
            for (Observation observation : observations) {
                maximum = Math.max(maximum, observation.confidence);
            }
            return maximum;
        }
    }

    private static final class Observation {
        private final long frame;
        private final double confidence;

        private Observation(long frame, double confidence) {
            this.frame = frame;
            this.confidence = confidence;
        }
    }
}
