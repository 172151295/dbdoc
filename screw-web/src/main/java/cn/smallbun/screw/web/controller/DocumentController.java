/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import cn.smallbun.screw.web.dto.DocumentGenerateRequest;
import cn.smallbun.screw.web.dto.DocumentTask;
import cn.smallbun.screw.web.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文档生成
 */
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 支持导出的格式
     */
    @GetMapping("/formats")
    public ApiResponse<List<String>> formats() {
        return ApiResponse.ok(documentService.supportedFormats());
    }

    /**
     * 提交异步生成任务
     */
    @PostMapping("/generate")
    public ApiResponse<String> generate(@RequestBody DocumentGenerateRequest req) {
        return ApiResponse.ok(documentService.submit(req));
    }

    /**
     * 查询生成任务进度
     */
    @GetMapping("/progress/{taskId}")
    public ApiResponse<DocumentTask> progress(@PathVariable String taskId) {
        DocumentTask task = documentService.getTask(taskId);
        if (task == null) {
            return ApiResponse.error(404, "生成任务不存在或已过期");
        }
        return ApiResponse.ok(task);
    }

    /**
     * 下载生成结果
     */
    @GetMapping("/download/{taskId}")
    public ResponseEntity<byte[]> download(@PathVariable String taskId) throws Exception {
        DocumentTask task = documentService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        String downloadName = task.getDownloadName();
        byte[] bytes = documentService.readResult(task);
        String encoded = URLEncoder.encode(downloadName, StandardCharsets.UTF_8)
            .replace("+", "%20");
        String contentType = downloadName.endsWith(".xlsx")
            ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            : "application/octet-stream";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded)
            .contentType(MediaType.parseMediaType(contentType))
            .body(bytes);
    }
}
