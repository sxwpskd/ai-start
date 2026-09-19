package org.aistart.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aistart.ai.AiCodeGenTypeRoutingService;
import org.aistart.annotation.AuthCheck;
import org.aistart.common.BaseResponse;
import org.aistart.common.DeleteRequest;
import org.aistart.common.ResultUtils;
import org.aistart.constant.AppConstant;
import org.aistart.constant.UserConstant;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.exception.ThrowUtils;
import org.aistart.langgraph4j.model.QualityResult;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.model.dto.app.*;
import org.aistart.model.entity.User;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.aistart.model.vo.AppVO;
import org.aistart.ratelimit.annotation.RateLimit;
import org.aistart.ratelimit.enums.RateLimitType;
import org.aistart.service.ProjectDownloadService;
import org.aistart.service.UserService;
import org.aistart.utils.ThinkFileUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.aistart.model.entity.App;
import org.aistart.service.AppService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 应用 控制层。
 *
 * @author <a>me</a>
 */
@RestController
@RequestMapping("/app")
@Slf4j
public class  AppController {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    @Resource
    private ProjectDownloadService projectDownloadService;

    /**
     * 生成进度心跳调度器：每 10s 向客户端补推一次当前步骤与已耗时
     * （同时保活 Nginx 反向代理连接，避免长静默被掐断）；守护线程且不主动关闭
     */
    private static final ScheduledExecutorService HEARTBEAT_SCHEDULER = Executors.newScheduledThreadPool(4,
            runnable -> Thread.ofPlatform().daemon().name("gen-sse-heartbeat").unstarted(runnable));

    /**
     * 进度心跳间隔（秒）
     */
    private static final long HEARTBEAT_SECONDS = 10;




    /**
     * 应用聊天生成代码（流式 SSE）
     *
     * @param appId       应用 ID
     * @param message     用户消息
     * @param codeGenType 可选：生成触发时指定的目标模式（html/multi_file/vue_project），
     *                    为空则为普通对话（构思期构思对话 / 生成期改码对话）
     * @param request     请求对象
     * @return 生成结果流
     */
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       @RequestParam(required = false) String codeGenType,
                                                       HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（流式）
        Flux<String> contentFlux = appService.chatToGenCode(appId, message, codeGenType, loginUser);
        // 转换为 ServerSentEvent 格式
        return contentFlux
                .map(chunk -> {
                    // 将内容包装成JSON对象
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }

    /**
     * 构思期（BASE）工作流对话（工作流通道 · 同步阻塞，非流式）
     *
     * @param thinkWorkflowRequest 构思工作流请求（appId + message）
     * @param request              请求对象
     * @return 本轮 AI 回复全文
     */
    @PostMapping("/chat/think/workflow")
    public BaseResponse<String> thinkWorkflow(@RequestBody AppThinkWorkflowRequest thinkWorkflowRequest,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(thinkWorkflowRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 同步阻塞执行构思工作流
        String reply = appService.thinkWorkflow(thinkWorkflowRequest.getAppId(),
                thinkWorkflowRequest.getMessage(), loginUser);
        return ResultUtils.success(reply);
    }

    /**
     * 工作流通道 · 生成触发（SSE 进度推送，非 token 级流式）
     * 事件：progress（节点完成即推，10s 心跳补推当前步骤与已耗时）
     *      → done（生成摘要）或 business-error（失败原因）
     * 说明：立即返回 emitter，实际生成在虚拟线程中推进（工作流通道统一同步阻塞执行）
     *
     * @param appId   应用 ID
     * @param message 触发消息（按钮固定文案 / 口令 / 直创应用的初始需求）
     * @param request 请求对象
     */
    @GetMapping(value = "/gen/workflow", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter genWorkflow(@RequestParam Long appId,
                                  @RequestParam String message,
                                  HttpServletRequest request) {
        // 参数校验：异常由全局处理器按 SSE 请求转为 business-error 事件
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 不设异步超时（0L 表示不过期）：生成时长由后端执行器控制，连接由心跳保活
        SseEmitter emitter = new SseEmitter(0L);
        long startTime = System.currentTimeMillis();
        // 当前步骤由节点回调更新，心跳定时读取后推送
        AtomicReference<String> currentStep = new AtomicReference<>("初始化");
        ScheduledFuture<?> heartbeat = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                () -> sendProgress(emitter, currentStep.get(), startTime),
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        // 客户端断开或 emitter 结束时停掉心跳
        emitter.onError(e -> heartbeat.cancel(false));
        emitter.onCompletion(() -> heartbeat.cancel(false));
        CompletableFuture<WorkflowContext> future;
        try {
            // 提交生成：节点完成回调即推一次进度（同步校验/鉴权失败会在此抛出）
            future = appService.genWorkflow(appId, message, loginUser, context -> {
                currentStep.set(context.getCurrentStep());
                sendProgress(emitter, currentStep.get(), startTime);
            });
        } catch (RuntimeException e) {
            // 同步失败：撤掉心跳，异常交由全局处理器写入 business-error 事件
            heartbeat.cancel(false);
            throw e;
        }
        // 终态收尾：成功推 done 摘要，失败推 business-error，随后关闭 emitter
        future.whenComplete((context, throwable) -> {
            heartbeat.cancel(false);
            if (throwable == null) {
                sendEvent(emitter, "done", buildDoneSummary(context, startTime));
            } else {
                log.error("工作流通道生成失败，appId: {}", appId, throwable);
                sendEvent(emitter, "business-error", buildBusinessError(throwable));
            }
            emitter.complete();
        });
        return emitter;
    }

    /**
     * 构思文档存在性预检（生成触发前使用：无构思文档时前端弹窗二次确认）
     *
     * @param appId   应用 ID
     * @param request 请求对象
     * @return 构思文档是否存在
     */
    @GetMapping("/think/exists")
    public BaseResponse<Boolean> existsThink(@RequestParam Long appId, HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        // 获取当前登录用户并校验应用归属，仅创建者可访问
        User loginUser = userService.getLoginUser(request);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        return ResultUtils.success(ThinkFileUtils.existsThink(appId));
    }

    /**
     * 推送进度事件（节点完成即推；10s 心跳走同一入口）
     */
    private void sendProgress(SseEmitter emitter, String step, long startTime) {
        Map<String, Object> data = Map.of(
                "step", StrUtil.blankToDefault(step, "生成中"),
                "elapsedMs", System.currentTimeMillis() - startTime);
        sendEvent(emitter, "progress", JSONUtil.toJsonStr(data));
    }

    /**
     * 构建生成摘要：路由结果 / 产物目录 / 质检结论 / 耗时
     */
    private String buildDoneSummary(WorkflowContext context, long startTime) {
        Map<String, Object> summary = new LinkedHashMap<>();
        CodeGenTypeEnum generationType = context.getGenerationType();
        summary.put("generationType", generationType == null ? null : generationType.getValue());
        summary.put("generatedCodeDir", context.getGeneratedCodeDir());
        summary.put("buildResultDir", context.getBuildResultDir());
        QualityResult qualityResult = context.getQualityResult();
        summary.put("qualityValid", qualityResult == null ? null : qualityResult.getIsValid());
        summary.put("qualityErrors", qualityResult == null ? null : qualityResult.getErrors());
        summary.put("elapsedMs", System.currentTimeMillis() - startTime);
        return JSONUtil.toJsonStr(summary);
    }

    /**
     * 构建业务错误数据（与全局处理器的 SSE 错误格式一致）
     */
    private String buildBusinessError(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        int code = cause instanceof BusinessException businessException
                ? businessException.getCode() : ErrorCode.SYSTEM_ERROR.getCode();
        String message = cause instanceof BusinessException && StrUtil.isNotBlank(cause.getMessage())
                ? cause.getMessage() : "生成失败，请重试";
        return JSONUtil.toJsonStr(Map.of("error", true, "code", code, "message", message));
    }

    /**
     * 发送 SSE 事件
     * 客户端断开后发送会抛 IO 异常，捕获忽略即可——生成仍在锁内跑完、正常落库，仅进度事件丢失
     */
    private void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 事件发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /**
     * 下载应用代码
     *
     * @param appId    应用ID
     * @param request  请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：只有应用创建者可以下载代码
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }
        // 4. 构建应用代码目录路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 5. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        // 6. 生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);
        // 7. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }

    /**
     * 创建应用
     *
     * @param appAddRequest 创建应用请求
     * @param request       请求
     * @return 应用 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        Long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 删除应用（用户只能删除自己的应用）
     *
     * @param deleteRequest 删除请求
     * @param request       请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 更新应用（用户只能更新自己的应用名称）
     *
     * @param appUpdateRequest 更新请求
     * @param request          请求
     * @return 更新结果
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = appUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可更新
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取应用详情
     *
     * @param id      应用 id
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类（包含用户信息）
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页获取当前用户创建的应用列表
     *
     * @param appQueryRequest 查询请求
     * @param request         请求
     * @return 应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页获取精选应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 精选应用列表
     */
    @PostMapping("/good/list/page/vo")
    @Cacheable(//让某个接口的数据进行redis缓存
            value = "good_app_page",
            key = "T(org.aistart.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            condition = "#appQueryRequest.pageNum <= 10"//条件，只有pageNum小于10的时候才进行缓存
    )
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        // 分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 更新结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = appAdminUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页获取应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

}
