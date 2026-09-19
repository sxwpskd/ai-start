package org.aistart.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.model.dto.app.AppAddRequest;
import org.aistart.model.dto.app.AppQueryRequest;
import org.aistart.model.entity.App;
import org.aistart.model.entity.User;
import org.aistart.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 应用 服务层。
 *
 * @author <a>me</a>
 */
public interface AppService extends IService<App> {
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
* 获取应用封装类
*
* */
    AppVO getAppVO(App app);
/**
* 获取封装列表
* */
    List<AppVO> getAppVOList(List<App> appList);

    /*
    * 构造应用查询条件
    * */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);
/**
 * 通过对话生成项目
 *
 * @param appId        应用 id
 * @param message      用户消息
 * @param codeGenType  可选：生成触发时指定的目标模式（html/multi_file/vue_project）；
 *                     为空则按应用当前模式走普通对话
 * @param loginUser    登录用户
 * */
    Flux<String> chatToGenCode(Long appId, String message, String codeGenType, User loginUser);

    /**
     * 构思期（BASE）工作流对话（工作流通道 · 同步阻塞，非流式）
     *
     * @param appId     应用 id
     * @param message   用户消息
     * @param loginUser 登录用户
     * @return 本轮 AI 回复全文
     * */
    String thinkWorkflow(Long appId, String message, User loginUser);

    /**
     * 生成期工作流（工作流通道 · 生成触发，供 SSE 端点调用）
     * 参数校验、应用查询与鉴权在调用线程完成（异常走全局处理器的 SSE 分支）；
     * 加锁、先落用户消息、读构思、跑生成图、成功后统一落库均在 GenWorkflowExecutor 的
     * 虚拟线程内执行——加锁与解锁必须同线程（Redisson RLock 按 threadId 记录持有者）
     *
     * @param appId        应用 id
     * @param message      触发消息（按钮固定文案 / 口令 / 直创应用的初始需求）
     * @param loginUser    登录用户
     * @param stepCallback 节点完成回调（供 SSE 推送进度，可为 null）
     * @return 终态上下文的 CompletableFuture（成功/失败由调用方以回调收尾）
     * */
    CompletableFuture<WorkflowContext> genWorkflow(Long appId, String message, User loginUser,
                                                   Consumer<WorkflowContext> stepCallback);

    /**
     * 应用部署
     *
     * */
    String deployApp(Long appId, User loginUser);

    void generateAppScreenshotAsync(Long appId, String appUrl);
}
