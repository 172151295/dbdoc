/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.config;

import cn.smallbun.screw.web.entity.SysMenu;
import cn.smallbun.screw.web.entity.SysUser;
import cn.smallbun.screw.web.repository.SysMenuRepository;
import cn.smallbun.screw.web.repository.SysUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始数据种子：
 * 1. 默认管理员 admin/admin123（BCrypt）
 * 2. 与 jimuqu-admin-ui 路由按 name 合并的 screw 菜单（顶层 Screw + 5 页面）
 */
@Slf4j
@Component
public class DataSeeder implements ApplicationRunner {

    private final SysUserRepository userRepository;
    private final SysMenuRepository menuRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DataSeeder(SysUserRepository userRepository, SysMenuRepository menuRepository) {
        this.userRepository = userRepository;
        this.menuRepository = menuRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser();
        seedMenus();
        hideUnusedMenus();
    }

    private void seedUser() {
        if (userRepository.existsByUserName("admin")) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUserName("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setNickName("管理员");
        admin.setAvatar("");
        admin.setAvatarUrl("");
        admin.setEmail("admin@screw.local");
        admin.setPhoneNumber("");
        admin.setSex("1");
        admin.setStatus("0");
        admin.setUserType("sys_user");
        admin.setRemark("系统内置管理员");
        admin.setDeptId(0L);
        admin.setDeptName("screw");
        userRepository.save(admin);
        log.info("[seed] 已创建默认管理员 admin/admin123");
    }

    private void seedMenus() {
        if (menuRepository.count() > 0) {
            return;
        }
        // name -> 父级菜单实体（创建后回填 id）
        SysMenu root = menu("Screw", "/screw", "Layout", "数据库文档", "lucide:database",
                1, null, null, false, true);
        root = menuRepository.save(root);
        Long rootId = root.getId();

        saveChild(rootId, "ScrewConnection", "/screw/connection", "screw/connection/index",
                "连接管理", "lucide:plug", 1);
        saveChild(rootId, "ScrewExport", "/screw/export", "screw/export/index",
                "对象导出", "lucide:file-output", 2);
        saveChild(rootId, "ScrewCode", "/screw/code", "screw/code/index",
                "代码生成", "lucide:braces", 4, true);
        saveChild(rootId, "ScrewCompare", "/screw/compare", "screw/compare/index",
                "库表比较", "lucide:git-compare", 5, true);

        log.info("[seed] 已创建 screw 菜单树（1 顶 + 4 子）");
    }

    /**
     * 幂等修正存量库菜单：
     * 1. 隐藏已合并的旧菜单（文档生成 / 表结构浏览 → 对象导出）
     * 2. 确保「对象导出」菜单存在（存量库 seed 早于该功能时补建）
     * 3. 隐藏未上线的菜单（代码生成 / 库表比较）
     */
    private void hideUnusedMenus() {
        hideMenu("ScrewDocument");
        hideMenu("ScrewMetadata");
        hideMenu("ScrewCode");
        hideMenu("ScrewCompare");
        ensureExportMenu();
    }

    private void hideMenu(String name) {
        menuRepository.findByName(name).ifPresent(m -> {
            if (!Boolean.TRUE.equals(m.getHidden())) {
                m.setHidden(true);
                menuRepository.save(m);
                log.info("[seed] 已隐藏菜单 {}", name);
            }
        });
    }

    /**
     * 存量库补建「对象导出」菜单（幂等：存在即跳过）
     */
    private void ensureExportMenu() {
        if (menuRepository.findByName("ScrewExport").isPresent()) {
            return;
        }
        menuRepository.findByName("Screw").ifPresent(root -> {
            SysMenu child = menu("ScrewExport", "/screw/export",
                    "screw/export/index", "对象导出", "lucide:file-output", 2,
                    root.getId(), null, false, false);
            menuRepository.save(child);
            log.info("[seed] 已补建菜单 ScrewExport（对象导出）");
        });
    }

    private void saveChild(Long parentId, String name, String path, String component,
                           String title, String icon, Integer orderNum) {
        saveChild(parentId, name, path, component, title, icon, orderNum, false);
    }

    private void saveChild(Long parentId, String name, String path, String component,
                           String title, String icon, Integer orderNum, boolean hidden) {
        SysMenu child = menu(name, path, component, title, icon, orderNum, parentId, null, hidden, false);
        menuRepository.save(child);
    }

    private SysMenu menu(String name, String path, String component, String title, String icon,
                         Integer orderNum, Long parentId, String redirect, Boolean hidden, Boolean alwaysShow) {
        SysMenu m = new SysMenu();
        m.setName(name);
        m.setPath(path);
        m.setComponent(component);
        m.setTitle(title);
        m.setIcon(icon);
        m.setOrderNum(orderNum);
        m.setParentId(parentId == null ? 0L : parentId);
        m.setRedirect(redirect);
        m.setHidden(hidden == null ? Boolean.FALSE : hidden);
        m.setAlwaysShow(alwaysShow == null ? Boolean.FALSE : alwaysShow);
        m.setNoCache(false);
        return m;
    }
}
