/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简 Token 服务（进程内存）
 * 生产可替换为 Redis + JWT；此实现满足 jimuqu-admin-ui 登录联调需求。
 */
@Service
public class TokenService {

    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();
    private final Map<Long, String> userToken = new ConcurrentHashMap<>();

    /**
     * 为用户签发 token
     */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.put(token, userId);
        return token;
    }

    /**
     * 校验 token 是否有效，返回 userId；无效返回 null
     */
    public Long validate(String token) {
        if (token == null) {
            return null;
        }
        return tokenStore.get(token);
    }

    /**
     * 使 token 失效（登出）
     */
    public void revoke(String token) {
        if (token == null) {
            return;
        }
        Long uid = tokenStore.remove(token);
        if (uid != null) {
            userToken.remove(uid);
        }
    }

    /**
     * 使某用户所有 token 失效
     */
    public void revokeByUser(Long userId) {
        String t = userToken.remove(userId);
        if (t != null) {
            tokenStore.remove(t);
        }
    }
}
