/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 资源/消息接口（SSE 与持久化消息桩）
 * <p>
 * jimuqu-admin-ui 开启 SSE(VITE_GLOB_SSE_ENABLE=true)，登录后会：
 * GET /resource/message?clientid=..&Authorization=Bearer ..  —— SSE 长连接
 * GET /resource/message/box                              —— 持久化通知列表
 * GET /resource/message/close                            —— 关闭连接
 * </p>
 */
@RestController
@RequestMapping("/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * SSE 长连接（保持连接，不主动推送；客户端可随时断开）
     */
    @GetMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(@RequestParam(required = false) String clientid,
                              @RequestParam(required = false) String Authorization) {
        SseEmitter emitter = new SseEmitter(0L);
        // 发送首个事件以确认连接建立；之后挂起等待
        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("connected").data("ping"));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 持久化通知列表（返回空列表，前端无消息）
     */
    @GetMapping("/message/box")
    public ApiResponse<Map<String, Object>> box() {
        return ApiResponse.ok(Map.of(
                "noticeList", List.of(),
                "systemList", List.of()));
    }

    /**
     * 关闭 SSE 连接
     */
    @GetMapping("/message/close")
    public ApiResponse<Void> close() {
        return ApiResponse.ok();
    }
}
