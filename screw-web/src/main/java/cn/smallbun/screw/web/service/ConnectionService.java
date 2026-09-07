/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.web.common.BizException;
import cn.smallbun.screw.web.dto.ConnectionSaveRequest;
import cn.smallbun.screw.web.entity.DbConnection;
import cn.smallbun.screw.web.repository.ConnectionRepository;
import cn.smallbun.screw.web.util.CryptoUtil;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 连接管理服务
 */
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final CryptoUtil cryptoUtil;

    /**
     * 按分组查询
     */
    public List<DbConnection> listByGroup(Long groupId) {
        if (groupId == null) {
            return connectionRepository.findAll();
        }
        return connectionRepository.findByGroupIdOrderByCreateTimeAsc(groupId);
    }

    /**
     * 保存（新建/更新）
     */
    @Transactional
    public DbConnection save(ConnectionSaveRequest req) {
        DbConnection conn;
        if (req.getId() != null) {
            conn = connectionRepository.findById(req.getId())
                .orElseThrow(() -> new BizException(404, "连接不存在"));
        } else {
            conn = new DbConnection();
        }
        conn.setGroupId(req.getGroupId());
        conn.setName(req.getName());
        conn.setDbType(req.getDbType());
        conn.setHost(req.getHost());
        conn.setPort(req.getPort());
        conn.setDatabase(req.getDatabase());
        conn.setSchemaName(req.getSchemaName());
        conn.setUsername(req.getUsername());
        conn.setRemark(req.getRemark());
        // 密码不为空才更新（编辑时留空表示不修改）
        if (StringUtils.hasText(req.getPassword())) {
            conn.setPasswordEnc(cryptoUtil.encrypt(req.getPassword()));
        }
        return connectionRepository.save(conn);
    }

    /**
     * 删除
     */
    @Transactional
    public void delete(Long id) {
        connectionRepository.deleteById(id);
    }

    /**
     * 测试连接
     */
    public String test(DbConnection conn) {
        try (HikariDataSource ds = buildDataSource(conn); Connection c = ds.getConnection()) {
            return "连接成功，数据库产品: " + c.getMetaData().getDatabaseProductName() + " "
                + c.getMetaData().getDatabaseProductVersion();
        } catch (SQLException e) {
            throw new BizException("连接失败: " + e.getMessage());
        }
    }

    /**
     * 根据连接构建数据源
     */
    public HikariDataSource buildDataSource(DbConnection conn) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(DbTypeSupport.buildUrl(conn));
        ds.setUsername(conn.getUsername());
        ds.setPassword(cryptoUtil.decrypt(conn.getPasswordEnc()));
        ds.setDriverClassName(DbTypeSupport.driver(conn.getDbType()));
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(10000);
        ds.setValidationTimeout(5000);
        ds.setIdleTimeout(60000);
        ds.setPoolName("screw-" + conn.getId());
        return ds;
    }

    /**
     * 获取连接实体
     */
    public DbConnection get(Long id) {
        return connectionRepository.findById(id)
            .orElseThrow(() -> new BizException(404, "连接不存在"));
    }
}
