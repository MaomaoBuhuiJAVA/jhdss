package com.jhds.service;

import com.jhds.entity.ControlLog;
import com.jhds.mapper.ControlLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class ControlLogService {

    @Autowired
    private ControlLogMapper controlLogMapper;

    public void log(String alias, String name, String value, Integer automatic,
                    String sendCommand, String returnCommand, Integer success) {
        ControlLog log = new ControlLog();
        log.setDeviceAlias(alias);
        log.setDeviceName(name);
        log.setValue(value);
        log.setAutomatic(automatic);
        log.setSendCommand(sendCommand);
        log.setReturnCommand(returnCommand);
        log.setSuccess(success);
        log.setCreatedAt(new Date());
        controlLogMapper.insert(log);
    }
}
