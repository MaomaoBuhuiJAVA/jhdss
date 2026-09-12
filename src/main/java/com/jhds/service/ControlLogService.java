package com.jhds.service;

import com.jhds.entity.ControlLog;
import com.jhds.mapper.ControlLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class ControlLogService {

    private static final int COMMAND_COLUMN_LENGTH = 200;

    @Autowired
    private ControlLogMapper controlLogMapper;

    public void log(String alias, String name, String value, Integer automatic,
                    String sendCommand, String returnCommand, Integer success) {
        ControlLog log = new ControlLog();
        log.setDeviceAlias(alias);
        log.setDeviceName(name);
        log.setValue(value);
        log.setAutomatic(automatic);
        log.setSendCommand(limitCommand(sendCommand));
        log.setReturnCommand(limitCommand(returnCommand));
        log.setSuccess(success);
        log.setCreatedAt(new Date());
        controlLogMapper.insert(log);
    }

    private String limitCommand(String value) {
        return value == null || value.length() <= COMMAND_COLUMN_LENGTH
                ? value : value.substring(0, COMMAND_COLUMN_LENGTH);
    }
}
