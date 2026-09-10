package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.config.YsjProperties;
import com.jhds.service.EzvizService;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class CameraControllerTest {

    @Test
    public void rejectsNonWavUploadWithoutCallingEzviz() {
        EzvizService service = mock(EzvizService.class);
        CameraController controller = newController(service);
        MockMultipartFile file = new MockMultipartFile(
                "voiceFile", "message.mp3", "audio/mpeg", "not audio".getBytes());

        Result<Void> result = controller.broadcastVoice(file, null, null);

        assertEquals(400, result.getCode());
        verify(service, never()).sendVoiceOnce(anyString(), anyInt(), any(byte[].class), anyString());
    }

    @Test
    public void acceptsRiffWaveAndCallsEzvizOnce() {
        EzvizService service = mock(EzvizService.class);
        CameraController controller = newController(service);
        byte[] wav = new byte[44];
        System.arraycopy("RIFF".getBytes(), 0, wav, 0, 4);
        System.arraycopy("WAVE".getBytes(), 0, wav, 8, 4);
        MockMultipartFile file = new MockMultipartFile(
                "voiceFile", "message.wav", "audio/wav", wav);

        Result<Void> result = controller.broadcastVoice(file, null, null);

        assertEquals(200, result.getCode());
        verify(service).sendVoiceOnce("TEST-CAMERA", 1, wav, "message.wav");
    }

    private CameraController newController(EzvizService service) {
        CameraController controller = new CameraController();
        YsjProperties properties = new YsjProperties();
        properties.setDeviceSerial("TEST-CAMERA");
        properties.setChannelNo(1);
        ReflectionTestUtils.setField(controller, "ezvizService", service);
        ReflectionTestUtils.setField(controller, "ysjProperties", properties);
        return controller;
    }
}
