/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.core.Configuration;
import cn.smallbun.screw.core.engine.EngineConfig;
import cn.smallbun.screw.core.engine.EngineFileType;
import cn.smallbun.screw.core.engine.EngineTemplateType;
import cn.smallbun.screw.core.engine.ExcelSheetMode;
import cn.smallbun.screw.core.execute.DocumentationExecute;
import cn.smallbun.screw.core.process.ProcessConfig;
import cn.smallbun.screw.core.process.ProgressListener;
import cn.smallbun.screw.web.common.BizException;
import cn.smallbun.screw.web.dto.DocumentGenerateRequest;
import cn.smallbun.screw.web.dto.DocumentTask;
import cn.smallbun.screw.web.entity.DbConnection;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文档生成服务：基于 screw-core 生成 HTML/WORD/MD/EXCEL。
 * 改为异步生成（避免大海量表时 HTTP 超时）+ 逐表进度上报。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final ConnectionService connectionService;

    /**
     * 异步任务注册表：taskId -> task
     */
    private final Map<String, DocumentTask> taskMap = new ConcurrentHashMap<>();
    /**
     * 异步任务执行器
     */
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();

    /**
     * 支持导出的格式描述（供前端）
     */
    public List<String> supportedFormats() {
        return List.of("HTML", "WORD", "MD", "EXCEL", "JSON", "XML", "PDF", "DDL");
    }

    /**
     * 提交异步文档生成任务
     *
     * @param req 生成请求
     * @return taskId
     */
    public String submit(DocumentGenerateRequest req) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        DocumentTask task = new DocumentTask();
        task.setTaskId(taskId);
        task.setStatus(DocumentTask.RUNNING);
        taskMap.put(taskId, task);

        taskExecutor.submit(() -> {
            try {
                runGenerate(task, req);
                task.setStatus(DocumentTask.SUCCESS);
                task.setCurrent(task.getTotal());
            } catch (Exception e) {
                log.error("文档生成失败 taskId={}", taskId, e);
                task.setStatus(DocumentTask.FAILED);
                task.setError(e.getMessage());
            }
        });
        return taskId;
    }

    /**
     * 查询任务进度
     */
    public DocumentTask getTask(String taskId) {
        return taskMap.get(taskId);
    }

    /**
     * 异步执行生成，输出文件落到临时目录（后续通过 download 接口取出）
     */
    private void runGenerate(DocumentTask task, DocumentGenerateRequest req) throws Exception {
        DbConnection conn = connectionService.get(req.getConnectionId());
        EngineFileType fileType = mapFormat(req.getFormat());
        // DDL 格式由 DdlService 直接生成，不走 Freemarker 模板引擎
        if (fileType == EngineFileType.DDL) {
            runGenerateDdl(task, req, conn);
            return;
        }
        EngineTemplateType produceType;
        switch (fileType) {
            case EXCEL:
                produceType = EngineTemplateType.excel;
                break;
            case PDF:
                produceType = EngineTemplateType.pdf;
                break;
            default:
                // HTML, WORD, MD, JSON, XML 均走 freemarker
                produceType = EngineTemplateType.freemarker;
        }
        String fileName = "screw_" + System.currentTimeMillis() + "_" + UUID.randomUUID()
            .toString().substring(0, 8);
        Path tempDir = null;
        try (HikariDataSource ds = connectionService.buildDataSource(conn)) {
            tempDir = Files.createTempDirectory("screw-doc");
            ProcessConfig.ProcessConfigBuilder pcBuilder = ProcessConfig.builder();
            if (req.getTables() != null && !req.getTables().isEmpty()) {
                pcBuilder.designatedTableName(req.getTables());
            }
            Configuration config = Configuration.builder()
                .title(StringUtils.hasText(req.getTitle()) ? req.getTitle() : conn.getName())
                // freemarker 模板对 version/description 会调用 ?trim，null 会抛异常，故兜底空串
                .version(StringUtils.hasText(req.getVersion()) ? req.getVersion() : "")
                .description(StringUtils.hasText(req.getDescription()) ? req.getDescription() : "")
                .organization("screw-web")
                .dataSource(ds)
                .includeView(true)
                .progressListener(new ProgressListener() {
                    @Override
                    public void onProgress(int current, int total, String tableName) {
                        task.setCurrent(current);
                        task.setTotal(total);
                        task.setCurrentTable(tableName);
                    }
                })
                .produceConfig(pcBuilder.build())
                .engineConfig(EngineConfig.builder()
                    .fileType(fileType)
                    .produceType(produceType)
                    .fileName(fileName)
                    .fileOutputDir(tempDir.toString())
                    .openOutputDir(false)
                    .excelSheetMode(mapExcelSheetMode(req.getExcelSheetMode()))
                    // 文档写入阶段逐表回调，保证导出过程中弹窗实时显示当前表名
                    .produceProgressListener(new ProgressListener() {
                        @Override
                        public void onProgress(int current, int total, String tableName) {
                            task.setCurrent(current);
                            task.setTotal(total);
                            task.setCurrentTable(tableName);
                        }
                    })
                    .build())
                .build();
            File file = new File(tempDir.toFile(), fileName + fileType.getFileSuffix());
            task.setResultPath(file.getAbsolutePath());
            task.setDownloadName(conn.getName() + fileType.getFileSuffix());
            new DocumentationExecute(config).execute();
            if (!file.exists()) {
                throw new BizException("文档生成失败：未找到输出文件");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("文档生成失败: " + e.getMessage());
        } finally {
            // 临时目录保留到下载完成后再清理；这里不删除
        }
    }

    /**
     * 读取生成结果字节流
     */
    public byte[] readResult(DocumentTask task) throws Exception {
        if (task == null) {
            throw new BizException("任务不存在或已过期");
        }
        if (!DocumentTask.SUCCESS.equals(task.getStatus())) {
            throw new BizException("任务尚未完成");
        }
        File file = new File(task.getResultPath());
        if (!file.exists()) {
            throw new BizException("生成结果文件不存在或已被清理");
        }
        return Files.readAllBytes(file.toPath());
    }

    /**
     * 映射 Excel Sheet 模式
     */
    private ExcelSheetMode mapExcelSheetMode(String mode) {
        if (mode == null) {
            return null;
        }
        switch (mode.trim().toUpperCase()) {
            case "SINGLE":
                return ExcelSheetMode.SINGLE;
            case "PER_TABLE":
            default:
                return ExcelSheetMode.PER_TABLE;
        }
    }

    private EngineFileType mapFormat(String format) {
        if (format == null) {
            return EngineFileType.HTML;
        }
        switch (format.trim().toUpperCase()) {
            case "HTML":
                return EngineFileType.HTML;
            case "WORD":
                return EngineFileType.WORD;
            case "MD":
            case "MARKDOWN":
                return EngineFileType.MD;
            case "EXCEL":
            case "XLSX":
                return EngineFileType.EXCEL;
            case "JSON":
                return EngineFileType.JSON;
            case "XML":
                return EngineFileType.XML;
            case "PDF":
                return EngineFileType.PDF;
            case "DDL":
            case "SQL":
                return EngineFileType.DDL;
            default:
                throw new BizException(400, "不支持的导出格式: " + format);
        }
    }

    /**
     * DDL 专用生成流程：通过 DdlService 直接查询 JDBC 元数据生成完整 DDL
     *（表/主键/索引/注释/视图/函数）
     */
    private void runGenerateDdl(DocumentTask task, DocumentGenerateRequest req,
                                DbConnection conn) throws Exception {
        Path tempDir = null;
        try (HikariDataSource ds = connectionService.buildDataSource(conn)) {
            tempDir = Files.createTempDirectory("screw-ddl");
            String fileName = "screw_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8);
            File file = new File(tempDir.toFile(), fileName + ".sql");
            task.setResultPath(file.getAbsolutePath());
            task.setDownloadName(conn.getName() + ".sql");

            DdlService ddlService = new DdlService(ds, conn.getName());
            // 指定表名过滤
            if (req.getTables() != null && !req.getTables().isEmpty()) {
                ddlService.setTableFilter(req.getTables());
            }
            // 生成进度回调
            ddlService.setProgressListener((current, total, tableName) -> {
                task.setCurrent(current);
                task.setTotal(total);
                task.setCurrentTable(tableName);
            });
            ddlService.generate(file);
            if (!file.exists()) {
                throw new BizException("DDL 生成失败：未找到输出文件");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("DDL 生成失败: " + e.getMessage());
        }
    }

}
