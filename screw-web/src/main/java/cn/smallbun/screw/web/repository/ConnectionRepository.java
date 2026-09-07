/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.repository;

import cn.smallbun.screw.web.entity.DbConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConnectionRepository extends JpaRepository<DbConnection, Long> {

    /**
     * 按分组查询
     */
    List<DbConnection> findByGroupIdOrderByCreateTimeAsc(Long groupId);

    /**
     * 查询某分组下连接数量
     */
    long countByGroupId(Long groupId);
}
