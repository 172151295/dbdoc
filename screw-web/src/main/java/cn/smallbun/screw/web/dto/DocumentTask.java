/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.dto;

import lombok.Data;

/**
 * 文档生成任务（异步任务状态）
 */
@Data
public class DocumentTask {

    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    /**
     * 任务 ID
     */
    private String taskId;
    /**
     * 状态：RUNNING / SUCCESS / FAILED
     */
    private String status = RUNNING;
    /**
     * 当前进度序号（从 1 开始）
     */
    private int current = 0;
    /**
     * 总对象数
     */
    private int total = 0;
    /**
     * 当前正在导出的对象名（表/视图）
     */
    private String currentTable;
    /**
     * 失败原因
     */
    private String error;
    /**
     * 生成结果文件完整路径（成功后用于下载）
     */
    private String resultPath;
    /**
     * 下载文件名（含后缀）
     */
    private String downloadName;
}
