/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import cn.smallbun.screw.web.entity.SysMenu;
import cn.smallbun.screw.web.entity.SysUser;
import cn.smallbun.screw.web.repository.SysMenuRepository;
import cn.smallbun.screw.web.repository.SysUserRepository;
import cn.smallbun.screw.web.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统接口：用户信息 + 菜单路由（对齐 jimuqu-admin-ui 契约）
 */
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final SysUserRepository userRepository;
    private final SysMenuRepository menuRepository;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 获取当前用户信息
     * GET /system/user/getInfo
     */
    @GetMapping("/user/getInfo")
    public ApiResponse<Map<String, Object>> getInfo(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return ApiResponse.error(401, "登录状态已过期");
        }
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("permissions", List.of("*:*:*"));
        data.put("roles", List.of("admin"));
        data.put("user", toUserMap(user));
        return ApiResponse.ok(data);
    }

    /**
     * 获取当前用户个人主页信息
     * GET /system/user/profile
     * 对齐 jimuqu-admin-ui 契约：{ user, roleGroup, postGroup }
     */
    @GetMapping("/user/profile")
    public ApiResponse<Map<String, Object>> profile(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return ApiResponse.error(401, "登录状态已过期");
        }
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("user", toUserMap(user));
        data.put("roleGroup", "管理员");
        data.put("postGroup", "");
        return ApiResponse.ok(data);
    }

    /**
     * 更新当前用户个人信息（昵称/邮箱/性别/电话）
     * PUT /system/user/profile
     */
    @PutMapping("/user/profile")
    public ApiResponse<Void> updateProfile(HttpServletRequest request,
        @RequestBody Map<String, Object> body) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return ApiResponse.error(401, "登录状态已过期");
        }
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }
        if (body.containsKey("nickName")) {
            user.setNickName(str(body.get("nickName")));
        }
        if (body.containsKey("email")) {
            user.setEmail(str(body.get("email")));
        }
        if (body.containsKey("phoneNumber")) {
            user.setPhoneNumber(str(body.get("phoneNumber")));
        }
        if (body.containsKey("sex")) {
            user.setSex(str(body.get("sex")));
        }
        userRepository.save(user);
        return ApiResponse.ok();
    }

    /**
     * 修改当前用户密码（前端 VITE_GLOB_ENABLE_ENCRYPT=false，明文 JSON）
     * PUT /system/user/profile/updatePwd  body: { oldPassword, newPassword }
     * 成功后吊销当前 token，前端将跳转登录页
     */
    @PutMapping("/user/profile/updatePwd")
    public ApiResponse<Void> updatePwd(HttpServletRequest request,
        @RequestBody Map<String, Object> body) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return ApiResponse.error(401, "登录状态已过期");
        }
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }
        String oldPassword = str(body.get("oldPassword"));
        String newPassword = str(body.get("newPassword"));
        if (oldPassword == null || oldPassword.isBlank()
            || newPassword == null || newPassword.isBlank()) {
            return ApiResponse.error(500, "新旧密码不能为空");
        }
        if (newPassword.length() < 5 || newPassword.length() > 20) {
            return ApiResponse.error(500, "密码长度需在5-20个字符之间");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return ApiResponse.error(500, "旧密码错误");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            return ApiResponse.error(500, "新密码不能与旧密码相同");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        // 吊销当前 token，强制重新登录
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            tokenService.revoke(auth.substring(7));
        }
        return ApiResponse.ok();
    }

    /**
     * 获取菜单路由树
     * GET /system/menu/getRouters
     */
    @GetMapping("/menu/getRouters")
    public ApiResponse<List<Map<String, Object>>> getRouters(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return ApiResponse.error(401, "登录状态已过期");
        }
        List<SysMenu> all = menuRepository.findAllByOrderByOrderNumAscIdAsc();
        // 顶层菜单（过滤已隐藏的废弃菜单，前端无需再解析其组件）
        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu m : all) {
            if ((m.getParentId() == null || m.getParentId() == 0L)
                && !Boolean.TRUE.equals(m.getHidden())) {
                roots.add(m);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysMenu root : roots) {
            result.add(toMenuMap(root, all, null));
        }
        return ApiResponse.ok(result);
    }

    /* ---------------- 辅助 ---------------- */

    private Long currentUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return tokenService.validate(auth.substring(7));
    }

    private Map<String, Object> toMenuMap(SysMenu m, List<SysMenu> all, String parentPath) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", m.getName());
        // 子级路径需相对父级（access.ts 会拼接 parentPath + menu.path）
        map.put("path", relativize(m.getPath(), parentPath));
        map.put("component", m.getComponent());
        map.put("hidden", m.getHidden() != null && m.getHidden());
        map.put("alwaysShow", m.getAlwaysShow() != null && m.getAlwaysShow());
        if (m.getRedirect() != null && !m.getRedirect().isBlank()) {
            map.put("redirect", m.getRedirect());
        }
        if (m.getQuery() != null && !m.getQuery().isBlank()) {
            map.put("query", m.getQuery());
        }
        if (m.getExt() != null && !m.getExt().isBlank()) {
            map.put("ext", m.getExt());
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", m.getTitle());
        meta.put("icon", m.getIcon());
        meta.put("noCache", m.getNoCache() != null ? m.getNoCache() : false);
        if (m.getLink() != null && !m.getLink().isBlank()) {
            meta.put("link", m.getLink());
        }
        if (m.getActiveMenu() != null && !m.getActiveMenu().isBlank()) {
            meta.put("activeMenu", m.getActiveMenu());
        }
        map.put("meta", meta);

        List<Map<String, Object>> children = new ArrayList<>();
        for (SysMenu c : all) {
            if (m.getId() != null && m.getId().equals(c.getParentId())
                && !Boolean.TRUE.equals(c.getHidden())) {
                children.add(toMenuMap(c, all, m.getPath()));
            }
        }
        map.put("children", children);
        return map;
    }

    /**
     * 将子菜单绝对路径转为相对父级的路径
     * 例：父 "/screw" + 子 "/screw/connection" -> "connection"
     */
    private String relativize(String childPath, String parentPath) {
        if (parentPath == null || parentPath.isBlank()) {
            return childPath;
        }
        if (childPath == null || childPath.isBlank()) {
            return childPath;
        }
        if (childPath.startsWith(parentPath)) {
            String relative = childPath.substring(parentPath.length());
            return relative.startsWith("/") ? relative.substring(1) : relative;
        }
        return childPath;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Map<String, Object> toUserMap(SysUser u) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", u.getId());
        user.put("userName", u.getUserName());
        user.put("nickName", u.getNickName());
        user.put("avatar", u.getAvatar());
        user.put("avatarUrl", u.getAvatarUrl());
        user.put("email", u.getEmail());
        user.put("phoneNumber", u.getPhoneNumber());
        user.put("sex", u.getSex());
        user.put("status", u.getStatus());
        user.put("userType", u.getUserType());
        user.put("remark", u.getRemark());
        user.put("deptId", u.getDeptId());
        user.put("deptName", u.getDeptName());
        user.put("loginIp", u.getLoginIp());
        user.put("loginDate", u.getLoginDate());
        user.put("createTime", u.getCreateTime());
        user.put("roles", List.of());
        return user;
    }
}
