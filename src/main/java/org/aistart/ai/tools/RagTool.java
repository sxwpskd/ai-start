package org.aistart.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.aistart.rag.RagService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 语料检索工具（直连通道 · AI 自主调用）
 * 检索范围：全库不过滤类目——由 AI 按需决定看页面参考、编码规范、组件用法还是生成技能
 * （工作流通道不用本工具，由 RagNode 按图角色做类目过滤检索，决策记录第 4/35 条）
 */
@Slf4j
@Component
public class RagTool extends BaseTool {

    /**
     * 检索无命中时的返回文案
     */
    private static final String NO_RESULT_TIP = "语料库中未检索到与该需求相关的内容，请基于自身知识继续。";

    private final RagService ragService;

    public RagTool(RagService ragService) {
        this.ragService = ragService;
    }

    @Tool("检索项目语料库，获取与需求相关的页面参考、编码规范、组件用法与生成技能")
    public String ragSearch(
            @P("检索内容，用自然语言描述要检索的需求或规范主题")
            String query
    ) {
        try {
            List<RagService.RagFragment> fragments = ragService.search(query, RagService.DEFAULT_TOP_K);
            String ragText = ragService.formatFragments(fragments);
            if (StrUtil.isBlank(ragText)) {
                log.info("语料检索无命中, query: {}", query);
                return NO_RESULT_TIP;
            }
            log.info("语料检索成功, query: {}, 命中片段数: {}", query, fragments.size());
            return ragText;
        } catch (Exception e) {
            String errorMessage = "语料检索失败: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "ragSearch";
    }

    @Override
    public String getDisplayName() {
        return "语料检索工具";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String query = arguments.getStr("query");
        // 只输出短文案不回显片段全文——否则检索结果会随聊天历史落库，膨胀记忆（同 ReadThinkTool 处理）
        return String.format("""
                [工具调用] %s
                已检索语料库：%s
                """, getDisplayName(), StrUtil.blankToDefault(query, ""));
    }
}