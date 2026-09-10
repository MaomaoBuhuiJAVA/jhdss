package com.jhds.service;

import com.jhds.config.YsjProperties;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CameraSpeechBroadcastServiceTest {

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

        new CameraSpeechBroadcastService(tts, ezviz, properties).broadcast("气肥已开启");

        verify(tts).validateMessage("气肥已开启");
        verify(ezviz).sendVoiceOnce("TEST-CAMERA", 2, wav, "xfyun-announcement.wav");
    }
}
