package com.jhds.service;

import com.jhds.config.YsjProperties;
import org.springframework.stereotype.Service;

/** Synthesizes approved announcements and plays them through the EZVIZ camera speaker. */
@Service
public class CameraSpeechBroadcastService {

    private static final int WAV_HEADER_BYTES = 44;
    private static final int PCM_BYTES_PER_SECOND = 16000 * 2;
    private static final int MAX_RESOURCE_BUSY_ATTEMPTS = 3;

    private final XfyunTtsService xfyunTtsService;
    private final EzvizService ezvizService;
    private final YsjProperties ysjProperties;

    public CameraSpeechBroadcastService(XfyunTtsService xfyunTtsService,
                                        EzvizService ezvizService,
                                        YsjProperties ysjProperties) {
        this.xfyunTtsService = xfyunTtsService;
        this.ezvizService = ezvizService;
        this.ysjProperties = ysjProperties;
    }

    public synchronized void broadcast(String sourceText) {
        String text = xfyunTtsService.validateMessage(sourceText);
        byte[] wav = xfyunTtsService.synthesize(text);
        sendWithResourceBusyRetry(wav);
        waitForCameraPlayback(wav);
    }

    private void sendWithResourceBusyRetry(byte[] wav) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_RESOURCE_BUSY_ATTEMPTS; attempt++) {
            try {
                ezvizService.sendVoiceOnce(
                        ysjProperties.getDeviceSerial(),
                        ysjProperties.getChannelNo(),
                        wav,
                        "xfyun-announcement.wav");
                return;
            } catch (RuntimeException e) {
                lastError = e;
                if (!containsResourceBusyCode(e) || attempt == MAX_RESOURCE_BUSY_ATTEMPTS) throw e;
                sleep(2000L);
            }
        }
        throw lastError;
    }

    private boolean containsResourceBusyCode(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("111000")) return true;
            current = current.getCause();
        }
        return false;
    }

    private void waitForCameraPlayback(byte[] wav) {
        long pcmBytes = Math.max(0, wav.length - WAV_HEADER_BYTES);
        long durationMs = pcmBytes * 1000L / PCM_BYTES_PER_SECOND;
        long delayMs = Math.max(500L, Math.min(30000L, durationMs + 350L));
        sleep(delayMs);
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
