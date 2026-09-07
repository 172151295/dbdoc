/*
 * screw-core - 简洁好用的数据库表结构文档生成工具
 * Copyright © 2020 SanLi (qinggang.zuo@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cn.smallbun.screw.core.engine.pdf;

import cn.smallbun.screw.core.engine.AbstractTemplateEngine;
import cn.smallbun.screw.core.engine.EngineConfig;
import cn.smallbun.screw.core.exception.ProduceException;
import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.util.Assert;
import cn.smallbun.screw.core.util.ExceptionUtils;
import cn.smallbun.screw.core.util.StringUtils;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Locale;
import java.util.Objects;

import static cn.smallbun.screw.core.constant.DefaultConstants.DEFAULT_ENCODING;
import static cn.smallbun.screw.core.constant.DefaultConstants.DEFAULT_LOCALE;
import static cn.smallbun.screw.core.engine.EngineTemplateType.freemarker;
import static cn.smallbun.screw.core.util.FileUtils.getFileByPath;
import static cn.smallbun.screw.core.util.FileUtils.isFileExists;

/**
 * PDF 模板引擎
 *
 * <p>使用 Freemarker 渲染 HTML 模板，再通过 openhtmltopdf 转换为 PDF。</p>
 * <p>支持 CJK（中日韩）字体，通过 JDK 内置字体回退机制处理中文显示。</p>
 *
 * @author SanLi
 * Created by qinggang.zuo@gmail.com / 2689170096@qq.com on 2020/3/21 21:20
 */
public class PdfTemplateEngine extends AbstractTemplateEngine {

    private static final Logger log           = LoggerFactory.getLogger(PdfTemplateEngine.class);

    /**
     * freemarker 配置实例化
     */
    private final Configuration configuration = new Configuration(
        Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);

    {
        try {
            String path = getEngineConfig().getCustomTemplate();
            if (StringUtils.isNotBlank(path) && isFileExists(path)) {
                String parent = Objects.requireNonNull(getFileByPath(path)).getParent();
                configuration.setDirectoryForTemplateLoading(new File(parent));
            } else {
                configuration.setTemplateLoader(
                    new ClassTemplateLoader(this.getClass(), freemarker.getTemplateDir()));
            }
            configuration.setDefaultEncoding(DEFAULT_ENCODING);
            configuration.setLocale(new Locale(DEFAULT_LOCALE));
        } catch (Exception e) {
            throw ExceptionUtils.mpe(e);
        }
    }

    public PdfTemplateEngine(EngineConfig engineConfig) {
        super(engineConfig);
    }

    /**
     * 生成 PDF 文档
     *
     * <p>流程：Freemarker 渲染 HTML → openhtmltopdf 转 PDF</p>
     *
     * @param info    {@link DataModel}
     * @param docName {@link String}
     * @throws ProduceException ProduceException
     */
    @Override
    public void produce(DataModel info, String docName) throws ProduceException {
        Assert.notNull(info, "DataModel can not be empty!");
        String path = getEngineConfig().getCustomTemplate();
        try {
            // 1. 使用 Freemarker 渲染 HTML 模板
            Template template;
            if (StringUtils.isNotBlank(path) && isFileExists(path)) {
                String fileName = new File(path).getName();
                template = configuration.getTemplate(fileName);
            } else {
                template = configuration
                    .getTemplate(getEngineConfig().getFileType().getTemplateNamePrefix()
                                 + freemarker.getSuffix());
            }

            // 渲染 HTML 到字符串
            String html;
            try (StringWriter stringWriter = new StringWriter()) {
                template.process(info, stringWriter);
                html = stringWriter.toString();
            }
            // 去掉开头的 BOM 和 Freemarker 注释（openhtmltopdf 对 XML 声明前内容敏感）
            html = html.replaceAll("^\\uFEFF?", "");
            html = html.replaceAll("(?s)^\\s*<#--.*?-->\\s*", "");
            // openhtmltopdf 严格 XML 模式：自闭合标签必须用 / 结尾
            html = html.replaceAll("<br\\s*>", "<br/>");
            html = html.replaceAll("<br\\s*/\\s*>", "<br/>");

            // 2. 使用 openhtmltopdf 将 HTML 转为 PDF
            File file = getFile(docName);
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                // 注册 JDK 内置字体作为 CJK 字体回退
                registerCjkFonts(builder);
                // 直接传入 HTML 字符串，openhtmltopdf 内置解析器处理
                builder.withHtmlContent(html, "");
                builder.toStream(os);
                builder.run();
            }

            // 打开输出目录
            openOutputDir();
        } catch (IOException | TemplateException e) {
            throw ExceptionUtils.mpe(e);
        }
    }

    /**
     * 注册 CJK 字体回退
     *
     * <p>尝试从 JDK 字体目录加载 CJK 字体，支持中文、日文、韩文显示。</p>
     * <p>优先尝试系统已安装的 CJK 字体路径，若不存在则跳过（openhtmltopdf 有内置回退）。</p>
     *
     * @param builder PdfRendererBuilder
     */
    private void registerCjkFonts(PdfRendererBuilder builder) {
        // JDK 内置字体可能路径
        String javaHome = System.getProperty("java.home");
        String[] fontPaths = {
                               // Windows
                               "C:\\Windows\\Fonts\\msyh.ttc", "C:\\Windows\\Fonts\\simsun.ttc",
                               "C:\\Windows\\Fonts\\simhei.ttf",
                               // Linux (常见)
                               "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                               "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                               "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
                               // macOS
                               "/System/Library/Fonts/PingFang.ttc", "/Library/Fonts/Songti.ttc", };

        for (String fontPath : fontPaths) {
            File fontFile = new File(fontPath);
            if (fontFile.exists()) {
                try {
                    builder.useFont(fontFile, "CJK");
                    log.debug("Registered CJK font: {}", fontPath);
                } catch (Exception e) {
                    log.debug("Failed to register font {}: {}", fontPath, e.getMessage());
                }
            }
        }
    }
}
