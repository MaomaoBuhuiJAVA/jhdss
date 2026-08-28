package com.jhds.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlantArchiveImageStorageServiceTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL" +
                    "wAAAAAElFTkSuQmCC");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void storesRecognizedImageWithRandomServerFileName() throws Exception {
        PlantArchiveImageStorageService service = newStorageService();
        MockMultipartFile file = new MockMultipartFile("file", "plant image.png", "image/png", ONE_PIXEL_PNG);

        String storedName = service.store(file);

        assertTrue(storedName.matches("[0-9a-f]{32}\\.png"));
        File storedFile = new File(temporaryFolder.getRoot(), storedName);
        assertTrue(storedFile.isFile());
        assertEquals(ONE_PIXEL_PNG.length, Files.size(storedFile.toPath()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonImageFileEvenWhenTheExtensionLooksLikeAnImage() throws Exception {
        PlantArchiveImageStorageService service = newStorageService();
        MockMultipartFile file = new MockMultipartFile("file", "not-an-image.png", "image/png", "not an image".getBytes("UTF-8"));

        try {
            service.store(file);
        } finally {
            assertFalse(temporaryFolder.getRoot().listFiles().length > 0);
        }
    }

    private PlantArchiveImageStorageService newStorageService() {
        PlantArchiveImageStorageService service = new PlantArchiveImageStorageService();
        ReflectionTestUtils.setField(service, "uploadPath", temporaryFolder.getRoot().getAbsolutePath());
        service.init();
        return service;
    }
}
