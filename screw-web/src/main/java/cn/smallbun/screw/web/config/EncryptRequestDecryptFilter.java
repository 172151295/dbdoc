/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求体解密过滤器
 *
 * 生产前端 VITE_GLOB_ENABLE_ENCRYPT=true 时，标记 encrypt:true 的请求
 * （PUT /system/user/profile/updatePwd、用户重置密码等）按以下线格式发送：
 * <pre>
 *   encrypt-key 头 = RSA(PKCS1v1.5) 加密后的 base64( base64(AES密钥) )
 *   请求体         = AES/ECB/PKCS7 加密的 JSON，输出 base64
 *   AES 密钥       = 32 位随机字符串，UTF-8 字节即密钥（AES-256）
 * </pre>
 * 本过滤器检测到 encrypt-key 头时自动解密并包装请求体，
 * 使 @RequestBody 正常收到明文 JSON（与前端 src/utils/encryption 约定一致）。
 *
 * 解密失败时放行原始请求，由控制器参数校验兜底报错，不影响普通请求。
 */
public class EncryptRequestDecryptFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EncryptRequestDecryptFilter.class);

    private final PrivateKey rsaPrivateKey;

    public EncryptRequestDecryptFilter(String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64.trim());
            this.rsaPrivateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("RSA 私钥初始化失败", e);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String encryptKey = request.getHeader("encrypt-key");
        String contentType = request.getContentType();
        boolean jsonBody = contentType != null && contentType.toLowerCase().contains("application/json");
        if (encryptKey == null || encryptKey.isBlank() || !jsonBody) {
            chain.doFilter(request, response);
            return;
        }
        try {
            byte[] plain = decrypt(request, encryptKey.trim());
            chain.doFilter(new DecryptedBodyRequestWrapper(request, plain), response);
        } catch (Exception e) {
            log.warn("encrypt-key 请求体解密失败，放行原始请求: {}", e.getMessage());
            chain.doFilter(request, response);
        }
    }

    /**
     * RSA 解密 encrypt-key 头得到 base64(AES key)，再解码出 AES key，
     * 用 AES/ECB/PKCS5Padding 解密请求体。
     */
    private byte[] decrypt(HttpServletRequest request, String encryptKey) throws Exception {
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
        byte[] keyWithBase64 = rsa.doFinal(Base64.getDecoder().decode(encryptKey));
        String aesKey = new String(Base64.getDecoder().decode(keyWithBase64), StandardCharsets.UTF_8);

        String bodyText = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        Cipher aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES"));
        return aes.doFinal(Base64.getDecoder().decode(bodyText));
    }

    /** 用解密后的请求体替换原请求体（同步修正 Content-Length 头） */
    private static final class DecryptedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        DecryptedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getHeader(String name) {
            if ("content-length".equalsIgnoreCase(name)) {
                return String.valueOf(body.length);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("content-length".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(String.valueOf(body.length)));
            }
            return super.getHeaders(name);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    // 同步流，无需异步读取支持
                }

                @Override
                public int read() {
                    return buffer.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
