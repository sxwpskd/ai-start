package org.aistart.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.aistart.ai.model.BaseCodeResult;
import org.aistart.ai.model.HtmlCodeResult;
import org.aistart.ai.model.MultiFileCodeResult;
import org.aistart.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class CodeFileSaver {

    // 文件保存根目录
    private static final String FILE_SAVE_ROOT_DIR =
            System.getProperty("user.dir") + "/tmp/code_output";
    /**
     * 保存 base
     */
    public static File saveBaseCodeResult(BaseCodeResult result) {
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.BASE.getValue());
        writeToFile(baseDirPath, "result.txt",
                result.getRole()+" "+result.getWorkflow()+" "+result.getResult()+
                        " 输出tokens："+result.getCompletionTokens()+
                        "。消耗总tokens："+result.getTotalTokens()+
                        "。消耗总时间："+result.getProcessingTime());
        return new File(baseDirPath);
    }
    /**
     * 保存 HtmlCodeResult
     */
    public static File saveHtmlCodeResult(HtmlCodeResult result) {
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode()+
                " 输出tokens："+result.getCompletionTokens()+
                "。消耗总tokens："+result.getTotalTokens()+
                "。消耗总时间："+result.getProcessingTime());
        return new File(baseDirPath);
    }

    /**
     * 保存 MultiFileCodeResult
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult result) {
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
        writeToFile(baseDirPath, "result.txt", " 输出tokens："+result.getCompletionTokens()+
                "。消耗总tokens："+result.getTotalTokens()+
                "。消耗总时间："+result.getProcessingTime());
        return new File(baseDirPath);
    }

    /**
     * 构建唯一目录路径：tmp/code_output/bizType_雪花ID
     */
    private static String buildUniqueDir(String bizType) {
        String uniqueDirName = StrUtil.
                format("{}_{}",
                        bizType,
                        IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR +
                File.separator +
                uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 写入单个文件
     */
    private static void writeToFile(String dirPath, String filename, String content) {
        String filePath = dirPath + File.separator + filename;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}
