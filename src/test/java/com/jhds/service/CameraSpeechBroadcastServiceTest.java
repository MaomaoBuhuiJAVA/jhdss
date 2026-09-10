package com.jhds.service;

import com.jhds.config.YsjProperties;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CameraSpeechBroadcastServiceTest {

    @Test(expected = IllegalStateException.class)
    public void doesNotCallEzvizWhenCameraBroadcastIsDisabled() {
        XfyunTtsService tts = mock(XfyunTtsService.class);
        EzvizService ezviz = mock(EzvizService.class);
        CameraSpeechBroadcastService service = new CameraSpeechBroadcastService(
                tts, ezviz, new YsjProperties());

        try {
            service.broadcast("气肥已开启");
        } finally {
            verify(tts, never()).synthesize("气肥已开启");
            verify(ezviz, never()).sendVoiceOnce(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.any(byte[].class),
                    org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Test
    public void synthesizesAndSendsApprovedMessageToDefaultCamera() {
        XfyunTtsService tts = mock(XfyunTtsService.class);
        EzvizService ezviz = mock(EzvizService.class);
        YsjProperties properties = new YsjProperties();
        properties.setDeviceSerial("TEST-CAMERA");
        properties.setChannelNo(2);
        byte[] wav = new byte[44];
        when(tts.validateMessage("气肥已开启")).thenReturn("气肥已开启");
        when(tts.synthesize("气肥已开启")).thenReturn(wav);

        CameraSpeechBroadcastService service = new CameraSpeechBroadcastService(tts, ezviz, properties);
        ReflectionTestUtils.setField(service, "enabled", true);
        service.broadcast("气肥已开启");

        verify(tts).validateMessage("气肥已开启");
        verify(ezviz).sendVoiceOnce("TEST-CAMERA", 2, wav, "xfyun-announcement.wav");
    }
}
