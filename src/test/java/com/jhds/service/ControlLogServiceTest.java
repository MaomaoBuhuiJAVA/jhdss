package com.jhds.service;

import com.jhds.entity.ControlLog;
import com.jhds.mapper.ControlLogMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ControlLogServiceTest {

    @Test
    public void commandPayloadsAreLimitedToDatabaseColumnLength() {
        ControlLogService service = new ControlLogService();
        ControlLogMapper mapper = mock(ControlLogMapper.class);
        ReflectionTestUtils.setField(service, "controlLogMapper", mapper);
        String longValue = repeat('x', 260);

        service.log("MOTOR_DIRECTION", "轨道电机", "open", 1, longValue, longValue, 1);

        ArgumentCaptor<ControlLog> captured = ArgumentCaptor.forClass(ControlLog.class);
        verify(mapper).insert(captured.capture());
        assertEquals(200, captured.getValue().getSendCommand().length());
        assertEquals(200, captured.getValue().getReturnCommand().length());
    }

    private String repeat(char value, int count) {
        StringBuilder text = new StringBuilder(count);
        for (int index = 0; index < count; index++) text.append(value);
        return text.toString();
    }
}
