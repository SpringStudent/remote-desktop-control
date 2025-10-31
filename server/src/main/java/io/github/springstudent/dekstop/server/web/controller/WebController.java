package io.github.springstudent.dekstop.server.web.controller;

import io.github.springstudent.dekstop.common.command.CmdReqCapture;
import io.github.springstudent.dekstop.server.netty.NettyChannelBrother;
import io.github.springstudent.dekstop.server.netty.NettyChannelManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author tanpenggood
 * @date 2025/05/25 8:46
 **/
@RestController
@RequestMapping("/web")
public class WebController {

    @GetMapping("/info")
    public ResponseEntity<Map<String, Map>> info() {
        Map<String, Map> result = new HashMap<>();
        result.put("deviceCodeChannel", NettyChannelManager.getDeviceCodeChannelMap());
        result.put("channelBrother", NettyChannelManager.getChannelBrotherMap());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/unbindChannelBrother/{controlledDeviceCode}")
    public ResponseEntity<Map<String, NettyChannelBrother>> unbindChannelBrother(@PathVariable String controlledDeviceCode) {
        Map<String, NettyChannelBrother> channelBrotherMap = NettyChannelManager.getChannelBrotherMap();
        NettyChannelBrother channelBrother = channelBrotherMap.get(controlledDeviceCode);
        Optional.ofNullable(channelBrother).ifPresent(s -> NettyChannelManager.unbindChannelBrother(CmdReqCapture.STOP_CAPTURE, channelBrother.getControlled()));
        return ResponseEntity.ok(Collections.singletonMap(controlledDeviceCode, channelBrother));
    }

}
