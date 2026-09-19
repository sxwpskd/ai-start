package org.aistart.langgraph4j.node;

import lombok.extern.slf4j.Slf4j;
import org.aistart.core.builder.VueProjectBuilder;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.aistart.utils.SpringContextUtil;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");

            // 获取必要的参数
            String generatedCodeDir = context.getGeneratedCodeDir();
            CodeGenTypeEnum generationType = context.getGenerationType();
            String buildResultDir;
            // 一定是 Vue 项目类型：使用 VueProjectBuilder 进行构建
            try {
                VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                // D4 双构建修复（方案 A，决策记录第 32 条）：code_generator 内 Facade 的
                // onCompleteResponse 已对同一目录构建过——dist 已存在则直接用（每次流完成
                // 都会重建，属最新产物）、跳过重复的 npm install/build；不存在才真正构建
                // （兜底内嵌构建失败仅记日志不抛错的场景）
                File existingDist = new File(generatedCodeDir, "dist");
                if (existingDist.exists() && existingDist.isDirectory()) {
                    buildResultDir = existingDist.getAbsolutePath();
                    log.info("dist 已存在（Facade 内嵌构建产物），跳过重复构建: {}", buildResultDir);
                } else {
                    // 执行 Vue 项目构建（npm install + npm run build）
                    boolean buildSuccess = vueBuilder.buildProject(generatedCodeDir);
                    if (buildSuccess) {
                        // 构建成功，返回 dist 目录路径
                        buildResultDir = generatedCodeDir + File.separator + "dist";
                        log.info("Vue 项目构建成功，dist 目录: {}", buildResultDir);
                    } else {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败");
                    }
                }
            } catch (Exception e) {
                log.error("Vue 项目构建异常: {}", e.getMessage(), e);
                buildResultDir = generatedCodeDir; // 异常时返回原路径
            }


            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建节点完成，最终目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}

