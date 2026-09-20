package org.aistart.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aistart.ai.AiCodeGenTypeRoutingService;
import org.aistart.ai.AiCodeGenTypeRoutingServiceFactory;
import org.aistart.constant.AppConstant;
import org.aistart.core.AIfacade.AiCodeGeneratorFacade;
import org.aistart.core.builder.VueProjectBuilder;
import org.aistart.core.handler.StreamHandlerExecutor;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.exception.ThrowUtils;
import org.aistart.langgraph4j.CodeGenConcurrentWorkflow;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.langgraph4j.workflow.BaseWorkflowExecutor;
import org.aistart.langgraph4j.workflow.GenWorkflowExecutor;
import org.aistart.model.dto.app.AppAddRequest;
import org.aistart.model.dto.app.AppQueryRequest;
import org.aistart.model.entity.App;
import org.aistart.mapper.AppMapper;
import org.aistart.model.entity.User;
import org.aistart.model.enums.ChatHistoryMessageTypeEnum;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.aistart.model.vo.AppVO;
import org.aistart.model.vo.UserVO;
import org.aistart.service.AppService;
import org.aistart.service.ChatHistoryService;
import org.aistart.service.ScreenshotService;
import org.aistart.service.UserService;
import org.aistart.utils.ThinkFileUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a>me</a>
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{
    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;
    @Resource
    private UserService userService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ScreenshotService screenshotService;
    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;
    @Resource
    private BaseWorkflowExecutor baseWorkflowExecutor;
    @Resource
    private GenWorkflowExecutor genWorkflowExecutor;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 生成防重锁的 key 前缀（按应用维度互斥，开发文档决策记录第 24 条）
     */
    private static final String GEN_LOCK_PREFIX = "gen_workflow:lock:";


    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 初始生成类型：为空默认构思期（BASE，生成类型由"生成代码"触发时选定）；
        // 非空必须是合法模式——前端首页显式选型（跳过构思期直接生成），创建即单向锁定
        String initCodeGenType = appAddRequest.getCodeGenType();
        CodeGenTypeEnum initTypeEnum = CodeGenTypeEnum.getEnumByValue(initCodeGenType);
        ThrowUtils.throwIf(StrUtil.isNotBlank(initCodeGenType) && initTypeEnum == null,
                ErrorCode.PARAMS_ERROR, "不支持的代码生成类型：" + initCodeGenType);
        app.setCodeGenType(initTypeEnum == null ? CodeGenTypeEnum.BASE.getValue() : initTypeEnum.getValue());
        // 旧的创建时 AI 路由选型保留备查（历史上结果只 log 未落库，实际未生效）
        // AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        // CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), app.getCodeGenType());
        return app.getId();
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, String codeGenType, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型（空值兜底为 BASE：历史应用视为构思期，兼容旧数据）
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            if (StrUtil.isBlank(codeGenTypeStr)) {
                codeGenTypeEnum = CodeGenTypeEnum.BASE;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
            }
        }
        // 5. 生成触发：可选参数指定目标模式（四种等价模式的切换，决策记录第 16 条）
        // 仅构思期（BASE）可触发；已在生成期时忽略参数按现有类型走，前端按钮成功即隐藏
        CodeGenTypeEnum targetTypeEnum = null;
        if (StrUtil.isNotBlank(codeGenType)) {
            CodeGenTypeEnum paramTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
            // 非空但非法/传 BASE 均拒绝，不吞脏数据
            ThrowUtils.throwIf(paramTypeEnum == null || paramTypeEnum == CodeGenTypeEnum.BASE,
                    ErrorCode.PARAMS_ERROR, "不支持的代码生成类型");
            if (codeGenTypeEnum == CodeGenTypeEnum.BASE) {
                targetTypeEnum = paramTypeEnum;
            }
        }
        // 6. 确定本次请求实际使用的模式与发给 AI 的消息
        // 生成触发：读构思全文拼增强提示词（think 文件不存在时 readThink 返回提示文本）；
        // 落库的用户消息仍为原始文案，构思全文不重复进聊天记录
        CodeGenTypeEnum actualTypeEnum = codeGenTypeEnum;
        String aiMessage = message;
        if (targetTypeEnum != null) {
            actualTypeEnum = targetTypeEnum;
            aiMessage = message + "\n\n以下是当前的应用构思文档：\n" + ThinkFileUtils.readThink(appId);
        }
        // 7. 通过校验后，添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 8. 调用 AI 生成代码（流式）
        Flux<String> contentFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(aiMessage, actualTypeEnum, appId);
        // 9. 收集AI响应内容并在完成后记录到对话历史
        Flux<String> handledFlux = streamHandlerExecutor.doExecute(contentFlux, chatHistoryService, appId, loginUser, actualTypeEnum);
        // 10. 生成成功后落库目标类型：单向锁定（阶段体感的锚点——失败留构思期可重选）
        if (targetTypeEnum != null) {
            CodeGenTypeEnum lockedTypeEnum = targetTypeEnum;
            return handledFlux.doOnComplete(() -> {
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCodeGenType(lockedTypeEnum.getValue());
                boolean updated = this.updateById(updateApp);
                // 流已完成，此处异常无法回传前端，落库失败仅记录日志（下次触发仍可重试）
                if (!updated) {
                    log.error("应用 {} 生成成功但 codeGenType 落库失败，仍处构思期", appId);
                    return;
                }
                log.info("应用 {} 生成成功，codeGenType 已锁定为 {}", appId, lockedTypeEnum.getValue());
            });
        }
        return handledFlux;

        // 5. 调用 AI 生成代码
        //return  aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
    }

    /**
     * 构思期（BASE）工作流对话（工作流通道 · 同步阻塞，非流式）
     * 与直连通道的区别：不经 Facade/流式链路，改由 LangGraph4j 编排（rag 占位 → 构思生成）
     */
    @Override
    public String thinkWorkflow(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以对话
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型（空值兜底为 BASE：历史应用视为构思期，兼容旧数据）
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            if (StrUtil.isBlank(codeGenTypeStr)) {
                codeGenTypeEnum = CodeGenTypeEnum.BASE;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
            }
        }
        // 5. 仅构思期可走构思工作流（前端仅在构思期显示入口，此处为防御）
        ThrowUtils.throwIf(codeGenTypeEnum != CodeGenTypeEnum.BASE,
                ErrorCode.PARAMS_ERROR, "应用已进入生成期，无法执行构思工作流");
        // 6. 先落库用户消息：记忆回灌会跳过最新一条（limit(1, maxCount)），
        // 靠它排除的正是本轮消息，否则会误吞一条真实历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 7. 同步阻塞执行构思工作流（虚拟线程 + 超时控制）
        WorkflowContext resultContext = baseWorkflowExecutor.execute(appId, loginUser.getId(), codeGenTypeEnum, message);
        ThrowUtils.throwIf(resultContext == null, ErrorCode.SYSTEM_ERROR, "构思工作流未返回结果");
        String aiReply = resultContext.getThinkReply();
        // 8. AI 回复落库（执行完成后落库，内容与前端展示一致）
        if (StrUtil.isNotBlank(aiReply)) {
            chatHistoryService.addChatMessage(appId, aiReply, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        }
        return aiReply;
    }

    /**
     * 生成期工作流（工作流通道 · 生成触发）
     * 参数校验、应用查询与鉴权在请求线程完成（异常由全局处理器按 SSE 请求转为 business-error 事件）；
     * 加锁、落库、跑图、解锁整段提交到虚拟线程执行（加锁与解锁必须同线程）
     */
    @Override
    public CompletableFuture<WorkflowContext> genWorkflow(Long appId, String message, User loginUser,
                                                          Consumer<WorkflowContext> stepCallback) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以触发生成
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 应用当前生成类型（空值兜底 BASE：历史应用视为构思期；生成期应用沿用已锁定类型）
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(app.getCodeGenType());
        // 5. 整段业务逻辑提交虚拟线程：加锁与解锁必须同线程（Redisson RLock 按 threadId 记录持有者）
        return genWorkflowExecutor.submit(() ->
                runGenWorkflow(app, codeGenTypeEnum, message, loginUser, stepCallback));
    }

    /**
     * 生成工作流异步段（运行在 GenWorkflowExecutor 的虚拟线程内）
     * 加锁 → 先落用户消息 → 读构思 → 跑生成图 → 成功后统一落库 → 解锁
     */
    private WorkflowContext runGenWorkflow(App app, CodeGenTypeEnum codeGenType, String message,
                                           User loginUser, Consumer<WorkflowContext> stepCallback) {
        Long appId = app.getId();
        Long userId = loginUser.getId();
        // 按应用维度加锁：同一应用同时只允许一个生成（防双击与重复触发）
        RLock lock = redissonClient.getLock(GEN_LOCK_PREFIX + appId);
        // 不等待：拿不到立即失败——防重需要的是拒绝并发，而非排队
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "应用正在生成中，请稍候再试");
        }
        try {
            // 1. 先落用户消息：记忆回灌靠"跳过最新一条"排除本轮消息，顺序不可反（决策记录第 20 条）
            chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), userId);
            // 2. 读构思文档入上下文：文件不存在则置空不拼（直创生成期应用无构思文档）
            String thinkContext = ThinkFileUtils.existsThink(appId) ? ThinkFileUtils.readThink(appId) : null;
            // 3. 构建初始上下文：路由依据为应用初始需求；构思全文用独立字段，不并入 originalPrompt
            WorkflowContext initialContext = WorkflowContext.builder()
                    .appId(appId)
                    .userId(userId)
                    .codeGenType(codeGenType)
                    .thinkContext(thinkContext)
                    .originalPrompt(app.getInitPrompt())
                    .currentStep("初始化")
                    .build();
            // 4. 同步执行生成图（节点完成回调供 SSE 推送进度）
            WorkflowContext resultContext = new CodeGenConcurrentWorkflow().executeWorkflow(initialContext, stepCallback);
            ThrowUtils.throwIf(resultContext == null, ErrorCode.SYSTEM_ERROR, "生成工作流未返回结果");
            // 5. 全图成功后统一落库（决策记录第 21 条）：生成类型单向锁定 + AI 回复全文
            lockGenerationType(appId, resultContext);
            saveGenReply(appId, userId, resultContext);
            return resultContext;
        } finally {
            // 超时中断会置中断标志，解锁失败时由看门狗在约 30s 后自动过期兜底
            try {
                lock.unlock();
            } catch (Exception e) {
                log.error("生成工作流释放锁失败，appId: {}（锁将由看门狗自动过期）", appId, e);
            }
        }
    }

    /**
     * 生成成功后落库生成类型（单向锁定；失败仅记日志，应用仍留构思期可重试）
     */
    private void lockGenerationType(Long appId, WorkflowContext context) {
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (generationType == null) {
            log.error("应用 {} 生成成功但未返回生成类型，codeGenType 未落库", appId);
            return;
        }
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setCodeGenType(generationType.getValue());
        if (!this.updateById(updateApp)) {
            log.error("应用 {} 生成成功但 codeGenType 落库失败，仍处构思期", appId);
            return;
        }
        log.info("应用 {} 生成成功，codeGenType 已锁定为 {}", appId, generationType.getValue());
    }

    /**
     * 生成回复全文落库（内容与直连通道一致；空内容不落库）
     */
    private void saveGenReply(Long appId, Long userId, WorkflowContext context) {
        String genReply = context.getGenReply();
        if (StrUtil.isBlank(genReply)) {
            log.warn("生成工作流未产出可落库的 AI 回复，appId: {}", appId);
            return;
        }
        chatHistoryService.addChatMessage(appId, genReply, ChatHistoryMessageTypeEnum.AI.getValue(), userId);
    }

    /**
     * 解析应用当前生成类型：空值兜底 BASE（历史应用视为构思期），非空但非法则抛错、不吞脏数据
     */
    private CodeGenTypeEnum resolveCodeGenType(String codeGenTypeStr) {
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum != null) {
            return codeGenTypeEnum;
        }
        if (StrUtil.isBlank(codeGenTypeStr)) {
            return CodeGenTypeEnum.BASE;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
    }

/**
 * 部署应用方法
 * @param appId 应用ID
 * @param loginUser 登录用户信息
 * @return 返回应用部署URL
 */
    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验：检查应用ID和用户登录状态
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息：根据ID获取应用详情
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
// 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }

        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 9. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");

        // 10. 构建应用访问 URL
        /*测试环境*/
        //String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        /*/
        /* 生产环境*/
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);

        // 11. 异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;

    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }

}
