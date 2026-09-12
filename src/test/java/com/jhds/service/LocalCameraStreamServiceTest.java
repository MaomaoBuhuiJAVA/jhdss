package com.jhds.service;

import com.jhds.config.LocalCameraProperties;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.Assert.*;

public class LocalCameraStreamServiceTest {
    private LocalCameraStreamService service(Path directory) {
        LocalCameraStreamService service = new LocalCameraStreamService();
        LocalCameraProperties properties = new LocalCameraProperties();
        properties.setHlsPath(directory.toString());
        ReflectionTestUtils.setField(service, "properties", properties);
        return service;
    }

    @Test
    public void directoryHasOnlyOneOwnerAndCanBeReacquired() throws Exception {
        Path directory = Files.createTempDirectory("camera-lock-test");
        LocalCameraStreamService first = service(directory), second = service(directory);
        try {
            ReflectionTestUtils.invokeMethod(first, "acquireOwnership", directory);
            try {
                ReflectionTestUtils.invokeMethod(second, "acquireOwnership", directory);
                fail("A second writer must be rejected");
            } catch (RuntimeException expected) { }
            first.destroy();
            ReflectionTestUtils.invokeMethod(second, "acquireOwnership", directory);
        } finally {
            first.destroy();
            second.destroy();
            Files.deleteIfExists(directory.resolve("bridge.lock"));
            Files.delete(directory);
        }
    }

    @Test
    public void nativeCommandPreservesVideoWithoutFrameRateConversion() throws Exception {
        Path directory = Files.createTempDirectory("camera-command-test");
        LocalCameraStreamService service = service(directory);
        try {
            Object quality = ReflectionTestUtils.getField(service, "streamQuality");
            for (Object candidate : quality.getClass().getEnumConstants()) {
                if (candidate.toString().equals("UHD_4K"))
                    ReflectionTestUtils.setField(service, "streamQuality", candidate);
            }
            ReflectionTestUtils.setField(service, "nativeVideo", true);
            List<String> command = ReflectionTestUtils.invokeMethod(service, "buildCommand", directory, "/ch1/main");
            assertEquals("copy", command.get(command.indexOf("-c:v") + 1));
            assertFalse(command.contains("-r"));
            assertFalse(command.contains("-vf"));
            assertTrue(command.contains("-use_wallclock_as_timestamps"));
            ReflectionTestUtils.setField(service, "nativeVideo", false);
            command = ReflectionTestUtils.invokeMethod(service, "buildCommand", directory, "/ch1/main");
            assertTrue(command.contains("libx264"));
        } finally { Files.delete(directory); }
    }

    @Test
    public void captureIgnoresOtherGenerationSegments() throws Exception {
        Path directory = Files.createTempDirectory("camera-segment-test");
        LocalCameraStreamService service = service(directory);
        Path old = directory.resolve("segment-old-1.ts");
        Path current = directory.resolve("segment-current-1.ts");
        try {
            ReflectionTestUtils.setField(service, "segmentPrefix", "segment-current-");
            Files.write(old, new byte[2048]);
            Files.write(current, new byte[2048]);
            old.toFile().setLastModified(System.currentTimeMillis() - 2000);
            current.toFile().setLastModified(System.currentTimeMillis() - 3000);
            assertEquals(current, ReflectionTestUtils.invokeMethod(service, "newestStableSegment", 0L));
        } finally {
            Files.delete(old); Files.delete(current); Files.delete(directory);
        }
    }
}
