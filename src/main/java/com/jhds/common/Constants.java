package com.jhds.common;

public interface Constants {

    String REDIS_KEY_PREFIX = "jhds:";

    String REDIS_COMMAND_KEY = REDIS_KEY_PREFIX + "command:";
    String REDIS_SENSOR_KEY = REDIS_KEY_PREFIX + "sensor:";
    String REDIS_HEARTBEAT_KEY = REDIS_KEY_PREFIX + "heartbeat:last";

    int COMMAND_TIMEOUT = 30;

    String MODE_MANUAL = "manual";
    String MODE_AUTO = "auto";
    String MODE_AI = "ai";

    String ALARM_URGENT = "urgent";
    String ALARM_IMPORTANT = "important";
    String ALARM_NORMAL = "normal";

    interface PatrolDir {
        String LEFT = "left";
        String RIGHT = "right";
        String STOP = "stop";
    }

    interface TaskStatus {
        int PENDING = 0;
        int RUNNING = 1;
        int COMPLETED = 2;
        int DISABLED = 3;
    }

    interface AiStatus {
        int PENDING = 0;
        int ANALYZING = 1;
        int COMPLETED = 2;
        int FAILED = 3;
    }
}
