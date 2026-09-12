package com.jhds.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Soft limit for the horizontal rail that carries the camera.
 *
 * The rail exposes no position feedback: left/right are momentary serial
 * frames and the only position reference is the operator-confirmed rightmost
 * end. Travel is therefore dead-reckoned from motor on/off timestamps and
 * persisted, so an application restart cannot silently unlock another full
 * safe span that would drive the carriage into the left hard limit.
 *
 * The rightmost end stays reachable, while leftward travel is capped at
 * {@code patrol.automatic.horizontal-coverage-ratio} (default 60%) of the calibrated
 * right-to-left travel for automatic patrols and manual control alike.
 */
@Slf4j
@Service
public class RailPositionService {

    /** Calibrated full right-to-left travel; also the 100% reference. */
    @Value("${patrol.automatic.horizontal-travel-ms:18422}")
    private long horizontalTravelMs;

    /** Share of the right-to-left travel the rail may ever use. */
    @Value("${patrol.automatic.horizontal-coverage-ratio:0.60}")
    private double horizontalCoverageRatio;

    @Value("${patrol.rail.position-file:./work/patrol/rail-position.txt}")
    private String positionFile;

    private final Object lock = new Object();
    private final AtomicReference<Runnable> leftLimitStopAction = new AtomicReference<>();
    private final ScheduledExecutorService limitExecutor =
            Executors.newSingleThreadScheduledExecutor(daemonFactory("rail-soft-limit"));

    /** Dead-reckoned leftward travel from the rightmost end, in milliseconds. */
    private long positionMs;
    private long moveStartedAt;
    private String activeDirection;
    private ScheduledFuture<?> leftLimitFuture;

    @PostConstruct
    public void init() {
        loadPosition();
        log.info("Rail soft limit ready: full travel {} ms, safe left ratio {}% ({} ms), position {} ms",
                fullTravelMs(), Math.round(safeRatio() * 100.0), leftLimitMs(), positionMs());
    }

    /** The stop action is supplied by PatrolService to avoid a circular dependency. */
    public void setLeftLimitStopAction(Runnable action) {
        leftLimitStopAction.set(action);
    }

    public double safeRatio() {
        if (Double.isNaN(horizontalCoverageRatio)) return 0.60;
        return Math.max(0.10, Math.min(0.97, horizontalCoverageRatio));
    }

    public long fullTravelMs() {
        return Math.max(1000L, horizontalTravelMs);
    }

    public long leftLimitMs() {
        return Math.round(fullTravelMs() * safeRatio());
    }

    public long positionMs() {
        synchronized (lock) {
            return positionMs;
        }
    }

    public long remainingLeftMs() {
        synchronized (lock) {
            return remainingLeftMsLocked();
        }
    }

    public long remainingRightMs() {
        synchronized (lock) {
            return Math.max(0L, positionMs);
        }
    }

    public boolean atLeftLimit() {
        return remainingLeftMs() <= 0L;
    }

    public boolean isMoving() {
        synchronized (lock) {
            return activeDirection != null;
        }
    }

    /**
     * Re-zeroes the estimate on the rightmost end. Called when the operator
     * confirms the rail sits on the right-bottom safe origin.
     */
    public void markRightOrigin() {
        synchronized (lock) {
            cancelLeftLimitLocked();
            positionMs = 0L;
            moveStartedAt = 0L;
            activeDirection = null;
        }
        persistPosition();
        log.info("Rail position re-zeroed at the rightmost origin");
    }

    /** Trims a requested run so it cannot cross the left soft limit. */
    public long clampHorizontalDuration(String direction, long requestedMs) {
        if (!"left".equals(direction)) return requestedMs;
        long budget = remainingLeftMs();
        if (budget <= 0L) return 0L;
        return requestedMs <= 0L ? budget : Math.min(requestedMs, budget);
    }

    /**
     * Starts dead-reckoning a move and returns the duration the caller may
     * safely run for. A left move is refused with -1 once the soft limit is
     * reached and otherwise schedules an automatic stop at that limit.
     *
     * Right travel is only tracked, never auto-stopped: the rightmost end is a
     * permitted destination, and the operator must be able to jog right until
     * the physical end even if the dead-reckoned estimate temporarily
     * overshoots. The position is clamped back to zero when the move ends.
     */
    public long beginMove(String direction, long requestedMs) {
        if (!"left".equals(direction) && !"right".equals(direction)) return -1L;
        synchronized (lock) {
            if (moveStartedAt != 0L) {
                accumulateLocked(System.currentTimeMillis());
            }
            if ("left".equals(direction)) {
                long budget = remainingLeftMsLocked();
                if (budget <= 0L) return -1L;
                long allowed = requestedMs <= 0L ? budget : Math.min(requestedMs, budget);
                startLocked("left");
                scheduleLeftLimitLocked(budget);
                return allowed;
            }
            long allowed = requestedMs <= 0L ? Long.MAX_VALUE : requestedMs;
            startLocked("right");
            return allowed;
        }
    }

    /** Stops dead-reckoning, banks the elapsed travel and persists the estimate. */
    public void endMove() {
        boolean changed;
        synchronized (lock) {
            cancelLeftLimitLocked();
            changed = moveStartedAt != 0L;
            if (changed) {
                accumulateLocked(System.currentTimeMillis());
                moveStartedAt = 0L;
                activeDirection = null;
            }
        }
        if (changed) {
            persistPosition();
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fullTravelMs", fullTravelMs());
        result.put("safeRatioPercent", Math.round(safeRatio() * 100.0));
        result.put("leftLimitMs", leftLimitMs());
        result.put("positionMs", positionMs());
        result.put("remainingLeftMs", remainingLeftMs());
        result.put("remainingRightMs", remainingRightMs());
        result.put("atLeftLimit", atLeftLimit());
        synchronized (lock) {
            result.put("movingDirection", activeDirection);
        }
        result.put("positionFile", positionPath().toString());
        return result;
    }

    /** Atomic live estimate, without banking travel or changing the soft limit. */
    public Map<String, Object> twinPosition() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long elapsed = moveStartedAt == 0L ? 0L : Math.max(0L, now - moveStartedAt);
            long estimated = positionMs;
            if ("left".equals(activeDirection)) estimated += elapsed;
            if ("right".equals(activeDirection)) estimated -= elapsed;
            estimated = Math.max(0L, Math.min(leftLimitMs(), estimated));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("x", 1.0 - (double) estimated / fullTravelMs());
            result.put("velocityX", "left".equals(activeDirection) ? -1000.0 / fullTravelMs()
                    : "right".equals(activeDirection) ? 1000.0 / fullTravelMs() : 0.0);
            result.put("minX", 1.0 - (double) leftLimitMs() / fullTravelMs());
            result.put("source", "motor-time-estimate");
            result.put("timestampMs", now);
            result.put("movingDirection", activeDirection);
            return result;
        }
    }

    private long remainingLeftMsLocked() {
        return Math.max(0L, leftLimitMs() - positionMs);
    }

    private void startLocked(String direction) {
        activeDirection = direction;
        moveStartedAt = System.currentTimeMillis();
    }

    private void accumulateLocked(long now) {
        if (moveStartedAt == 0L) return;
        long elapsed = Math.max(0L, now - moveStartedAt);
        moveStartedAt = now;
        if ("left".equals(activeDirection)) {
            positionMs = Math.min(leftLimitMs(), positionMs + elapsed);
        } else if ("right".equals(activeDirection)) {
            positionMs = Math.max(0L, positionMs - elapsed);
        }
    }

    private void scheduleLeftLimitLocked(long budgetMs) {
        cancelLeftLimitLocked();
        leftLimitFuture = limitExecutor.schedule(this::handleLeftLimitReached,
                Math.max(1L, budgetMs), TimeUnit.MILLISECONDS);
    }

    private void cancelLeftLimitLocked() {
        if (leftLimitFuture != null) {
            leftLimitFuture.cancel(false);
            leftLimitFuture = null;
        }
    }

    private void handleLeftLimitReached() {
        log.warn("Rail reached the {}% left soft limit; stopping the carriage",
                Math.round(safeRatio() * 100.0));
        Runnable action = leftLimitStopAction.get();
        if (action != null) {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.error("Rail soft-limit stop action failed", e);
            }
        }
        endMove();
    }

    private void persistPosition() {
        Path path = positionPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, String.valueOf(positionMs()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            log.warn("Unable to persist rail position to {}: {}", path, e.getMessage());
        }
    }

    private void loadPosition() {
        Path path = positionPath();
        if (!Files.exists(path)) return;
        try {
            String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) return;
            long stored = Long.parseLong(raw);
            synchronized (lock) {
                positionMs = Math.max(0L, Math.min(leftLimitMs(), stored));
            }
        } catch (IOException | NumberFormatException e) {
            log.warn("Ignoring unreadable rail position file {}: {}", path, e.getMessage());
        }
    }

    private Path positionPath() {
        String configured = positionFile == null || positionFile.trim().isEmpty()
                ? "./work/patrol/rail-position.txt" : positionFile.trim();
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private static ThreadFactory daemonFactory(final String name) {
        return new ThreadFactory() {
            private final AtomicInteger number = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name + "-" + number.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    @PreDestroy
    public void shutdown() {
        endMove();
        limitExecutor.shutdownNow();
    }
}
