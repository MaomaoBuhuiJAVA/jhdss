package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.service.ControlPanelService;
import com.jhds.service.DeviceTwinState;
import com.jhds.service.RailPositionService;
import com.jhds.service.mqtt.MqttService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/device-twin")
public class DeviceTwinController {
    private final RailPositionService rail;
    private final MqttService mqtt;
    private final ControlPanelService panel;
    private final DeviceTwinState state;
    @Value("${device.twin.drip-alias:PUMP_CIRCULATION}")
    private String dripAlias;

    public DeviceTwinController(RailPositionService rail, MqttService mqtt,
                                ControlPanelService panel, DeviceTwinState state) {
        this.rail = rail; this.mqtt = mqtt; this.panel = panel; this.state = state;
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        Map<String, Object> effects = new LinkedHashMap<>();
        effects.put("foliar", panel.twinPumpStatus());
        boolean connected = mqtt.hasAvailableTransport();
        long connectionEpoch = mqtt.commandTransportEpoch();
        effects.put("drip", state.snapshot(dripAlias, connected, connectionEpoch));
        effects.put("gas", state.snapshot("PUMP_CO2", connected, connectionEpoch));
        Map<String, Object> pose = rail.twinPosition();
        Map<String, Object> panelPose = panel.twinMotionStatus();
        boolean panelConnected = Boolean.TRUE.equals(panelPose.get("connected"));
        pose.put("y", panelPose.get("y"));
        pose.put("velocityY", panelPose.get("velocityY"));
        pose.put("xConnected", connected);
        pose.put("yConnected", panelConnected);
        pose.put("connected", connected || panelConnected);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pose", pose);
        result.put("effects", effects);
        result.put("timestampMs", System.currentTimeMillis());
        return Result.ok(result);
    }
}
