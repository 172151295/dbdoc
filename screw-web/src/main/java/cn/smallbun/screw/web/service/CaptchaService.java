/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 验证码服务
 * <p>
 * 此处返回 captchaEnabled=false（不启用图形验证码），前端登录页会自动隐藏验证码输入框。
 * 如需启用，可在此接入 kaptcha/Redis 并校验 uuid + code。
 */
@Service
public class CaptchaService {

    /**
     * 生成验证码信息
     *
     * @return { uuid, img, captchaEnabled }
     */
    public Map<String, Object> generate() {
        Map<String, Object> result = new HashMap<>();
        result.put("captchaEnabled", false);
        result.put("uuid", "");
        result.put("img", "");
        return result;
    }
}
