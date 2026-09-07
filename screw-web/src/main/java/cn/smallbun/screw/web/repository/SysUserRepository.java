/*
 * screw-web - 数据库表结构文档生成平台
 */
package cn.smallbun.screw.web.repository;

import cn.smallbun.screw.web.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUserName(String userName);

    boolean existsByUserName(String userName);
}
