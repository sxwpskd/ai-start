package org.aistart.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aistart.constant.AppConstant;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 构思（think）文件工具类
 * 负责将构思 Markdown 落盘，并渲染为可预览的 HTML 页面
 * 说明：构思源文件与渲染产物永远成对写出，故不做拆分
 */
@Slf4j
public class ThinkFileUtils {

    /**
     * 渲染模板在类路径下的位置
     */
    private static final String TEMPLATE_PATH = "template/think-template.html";

    /**
     * 模板中的内容占位符
     */
    private static final String CONTENT_PLACEHOLDER = "${CONTENT}";

    /**
     * 构思源文件前缀
     */
    private static final String THINK_FILE_PREFIX = "think_";

    /**
     * 构思源文件扩展名
     */
    private static final String THINK_FILE_SUFFIX = ".md";

    /**
     * 渲染产物文件名（复用静态资源访问的目录默认页）
     */
    private static final String RENDER_FILE_NAME = "index.html";

    /**
     * 构思文档尚未创建时的提示文本（供 AI 判断首轮场景）
     */
    private static final String THINK_NOT_EXIST_TIP = "构思文档尚未创建";

    /**
     * Markdown 解析器与渲染器，线程安全，静态复用
     */
    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());

    private static final Parser PARSER = Parser.builder()
            .extensions(EXTENSIONS)
            .build();

    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .build();

    private ThinkFileUtils() {
    }

    /**
     * 保存构思：写出构思源文件（Markdown），并同步渲染出预览页面（HTML）
     *
     * @param appId    应用 id（即会话 id）
     * @param markdown 构思内容（完整 Markdown 全文，覆盖式保存，最新构思生效）
     * @return 渲染产物的相对路径，形如 base_1/index.html
     */
    public static String saveThink(Long appId, String markdown) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        }
        if (StrUtil.isBlank(markdown)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "构思内容不能为空");
        }
        // 目录命名与代码生成产物保持一致：{codeGenType}_{appId}
        String dirName = getThinkDirName(appId);
        File thinkFile = getThinkFile(appId);
        File renderFile = FileUtil.file(AppConstant.CODE_OUTPUT_ROOT_DIR, dirName, RENDER_FILE_NAME);
        try {
            // 写出构思源文件（B5 生成代码时读取该文件拼接增强提示词）
            FileUtil.writeString(markdown, thinkFile, StandardCharsets.UTF_8);
            log.info("构思文件已保存: {}", thinkFile.getAbsolutePath());
            // 渲染出预览页面
            FileUtil.writeString(renderHtml(markdown), renderFile, StandardCharsets.UTF_8);
            log.info("构思预览页已生成: {}", renderFile.getAbsolutePath());
        } catch (Exception e) {
            String errorMessage = "构思文件保存失败: " + e.getMessage();
            log.error(errorMessage, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
        }
        // 返回相对路径，避免绝对路径暴露给 AI
        return dirName + "/" + RENDER_FILE_NAME;
    }

    /**
     * 读取构思文档全文
     *
     * @param appId 应用 id（即会话 id）
     * @return 构思文档的 Markdown 全文；文档尚未创建时返回提示文本
     */
    public static String readThink(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        }
        File thinkFile = getThinkFile(appId);
        if (!thinkFile.exists()) {
            return THINK_NOT_EXIST_TIP;
        }
        try {
            return FileUtil.readString(thinkFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            String errorMessage = "构思文件读取失败: " + e.getMessage();
            log.error(errorMessage, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
        }
    }

    /**
     * 判断构思文档是否已创建（仅供生成触发前的存在性预检使用）
     *
     * @param appId 应用 id（即会话 id）
     * @return 构思源文件存在返回 true
     */
    public static boolean existsThink(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        }
        return getThinkFile(appId).exists();
    }

    /**
     * 构建构思产物所在目录名，与代码生成产物命名规则一致：{codeGenType}_{appId}
     */
    private static String getThinkDirName(Long appId) {
        return CodeGenTypeEnum.BASE.getValue() + "_" + appId;
    }

    /**
     * 构建构思源文件（Markdown）
     */
    private static File getThinkFile(Long appId) {
        String thinkFileName = THINK_FILE_PREFIX + appId + THINK_FILE_SUFFIX;
        return FileUtil.file(AppConstant.CODE_OUTPUT_ROOT_DIR, getThinkDirName(appId), thinkFileName);
    }

    /**
     * 将 Markdown 渲染为完整的 HTML 页面
     *
     * @param markdown 构思 Markdown 内容
     * @return 渲染后的 HTML 页面
     */
    private static String renderHtml(String markdown) throws Exception {
        Node document = PARSER.parse(markdown);
        String renderedContent = RENDERER.render(document);
        String template;
        try (InputStream inputStream = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        return template.replace(CONTENT_PLACEHOLDER, renderedContent);
    }
}
