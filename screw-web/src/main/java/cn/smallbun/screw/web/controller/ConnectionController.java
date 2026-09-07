/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import cn.smallbun.screw.web.dto.ConnectionSaveRequest;
import cn.smallbun.screw.web.entity.DbConnection;
import cn.smallbun.screw.web.service.ConnectionService;
import cn.smallbun.screw.web.service.DbTypeSupport;
import cn.smallbun.screw.web.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 连接管理
 */
@RestController
@RequestMapping("/api/connection")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;
    private final CryptoUtil cryptoUtil;

    /**
     * 支持的数据库类型
     */
    @GetMapping("/db-types")
    public ApiResponse<List<DbTypeSupport.DbTypeInfo>> dbTypes() {
        return ApiResponse.ok(DbTypeSupport.all());
    }

    @GetMapping
    public ApiResponse<List<DbConnection>> list(@RequestParam(required = false) Long groupId) {
        return ApiResponse.ok(connectionService.listByGroup(groupId));
    }

    @PostMapping
    public ApiResponse<DbConnection> save(@RequestBody ConnectionSaveRequest req) {
        return ApiResponse.ok(connectionService.save(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<DbConnection> update(@PathVariable Long id,
                                            @RequestBody ConnectionSaveRequest req) {
        req.setId(id);
        return ApiResponse.ok(connectionService.save(req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        connectionService.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 测试连接
     */
    @PostMapping("/test")
    public ApiResponse<String> test(@RequestBody ConnectionSaveRequest req) {
        DbConnection conn = new DbConnection();
        if (req.getId() != null) {
            // 编辑已有连接：以表单最新值为准（用户可能改了地址/端口/库名/用户名/密码）
            conn = connectionService.get(req.getId());
            if (req.getDbType() != null) {
                conn.setDbType(req.getDbType());
            }
            if (req.getHost() != null) {
                conn.setHost(req.getHost());
            }
            if (req.getPort() != null) {
                conn.setPort(req.getPort());
            }
            if (req.getDatabase() != null) {
                conn.setDatabase(req.getDatabase());
            }
            if (req.getSchemaName() != null) {
                conn.setSchemaName(req.getSchemaName());
            }
            if (req.getUsername() != null) {
                conn.setUsername(req.getUsername());
            }
            // 表单填了新密码则用新密码测试（留空 = 沿用原密码）
            if (StringUtils.hasText(req.getPassword())) {
                conn.setPasswordEnc(cryptoUtil.encrypt(req.getPassword()));
            }
        } else {
            conn.setDbType(req.getDbType());
            conn.setHost(req.getHost());
            conn.setPort(req.getPort());
            conn.setDatabase(req.getDatabase());
            conn.setSchemaName(req.getSchemaName());
            conn.setUsername(req.getUsername());
            conn.setPasswordEnc(cryptoUtil.encrypt(req.getPassword()));
        }
        return ApiResponse.ok(connectionService.test(conn));
    }
}
