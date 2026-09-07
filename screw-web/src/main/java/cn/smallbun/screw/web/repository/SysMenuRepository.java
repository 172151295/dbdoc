/*
 * screw-web - 数据库表结构文档生成平台
 */
package cn.smallbun.screw.web.repository;

import cn.smallbun.screw.web.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    List<SysMenu> findByParentIdOrderByOrderNumAscIdAsc(Long parentId);

    List<SysMenu> findAllByOrderByOrderNumAscIdAsc();

    Optional<SysMenu> findByName(String name);
}
