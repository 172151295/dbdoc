/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.dto;

import lombok.Data;

/**
 * DB 对比请求
 */
@Data
public class DbCompareRequest {

    /**
     * 源连接 ID
     */
    private Long sourceId;
    /**
     * 目标连接 ID
     */
    private Long targetId;
    /**
     * 是否仅显示差异
     */
    private Boolean diffOnly;
}
