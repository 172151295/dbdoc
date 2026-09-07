/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import cn.smallbun.screw.web.entity.ConnectionGroup;
import cn.smallbun.screw.web.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分组管理
 */
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupRepository groupRepository;

    @GetMapping
    public ApiResponse<List<ConnectionGroup>> list() {
        return ApiResponse.ok(groupRepository.findAll());
    }

    @PostMapping
    public ApiResponse<ConnectionGroup> create(@RequestBody ConnectionGroup group) {
        return ApiResponse.ok(groupRepository.save(group));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConnectionGroup> update(@PathVariable Long id,
                                               @RequestBody ConnectionGroup group) {
        group.setId(id);
        return ApiResponse.ok(groupRepository.save(group));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        groupRepository.deleteById(id);
        return ApiResponse.ok();
    }
}
