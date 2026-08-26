package com.jhds.service.mqtt;

import com.alibaba.fastjson.JSON;
import com.jhds.common.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CommandMatcher {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void cacheCommand(String requestId, String commandPayload) {
        String key = Constants.REDIS_COMMAND_KEY + requestId;
        redisTemplate.opsForValue().set(key, commandPayload, Constants.COMMAND_TIMEOUT, TimeUnit.SECONDS);
    }

    public String matchResponse(String requestId) {
        String key = Constants.REDIS_COMMAND_KEY + requestId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    public void removeCommand(String requestId) {
        String key = Constants.REDIS_COMMAND_KEY + requestId;
        redisTemplate.delete(key);
    }
}
