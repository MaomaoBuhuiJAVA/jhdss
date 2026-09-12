package com.jhds.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class PatrolCaptureGroup {

    private final int row;
    private final String point;
    private final String pointLabel;
    private final List<Path> frames = new ArrayList<>();

    PatrolCaptureGroup(int row, String point, String pointLabel) {
        this.row = row;
        this.point = point;
        this.pointLabel = pointLabel;
    }

    int getRow() { return row; }
    String getPoint() { return point; }
    String getPointLabel() { return pointLabel; }
    List<Path> getFrames() { return Collections.unmodifiableList(frames); }
    void addFrame(Path frame) { frames.add(frame); }
    void sortFrames() { frames.sort(Comparator.comparing(path -> path.getFileName().toString())); }
}
