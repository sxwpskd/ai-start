# MEMRY.md（AI 助手工作记忆，2026-09-17 建立）

## 1. 项目概览
AI 零代码应用生成平台（ai-start），纯后端（Spring Boot 3.5.7 + Java 21，端口 8100，context-path=/ai）。
技术栈：LangChain4j 1.1.0 + LangGraph4j 1.6.0-rc2、MyBatis Flex、Redis/Caffeine/Redisson、DeepSeek（deepseek-chat / deepseek-reasoner）。
架构基准详见：架构文档/当前对项目架构的整理与理解.txt；目标与决策：架构文档/当前目标.txt；流程：架构文档/流程.txt；任务拆解：架构文档/开发文档.txt。

## 2. 当前目标：开发后端 base 模式
BASE = 构思期（重定义，原 BASE 生成类型废弃）；HTML/MULTI_FILE/VUE_PROJECT = 生成期。
用户在前端按"生成代码"按钮前均为 base 模式（多轮构思对话，流式输出）。
双通道设计：直连通道（现有流式链路 + think 以 @Tool 形式被 AI 调用）与工作流通道（LangGraph4j 同步阻塞，不做 SSE）。RAG 整体搁置后置。

## 3. 核心决策（已拍板，勿重复讨论）
- codeGenType = 阶段 + 策略；BASE→（点生成）→生成期，单向锁定，成功后落库，失败留构思期可重选
- 会话 id = appId；think 文件：tmp/code_output/BASE_{appId}/think_{appId}.txt，AI 写 Markdown，覆盖写
- think 渲染：commonmark-java 服务端渲染为内联 CSS 的 HTML（无 CDN，iframe 离线可用）；触发：直连通道在 WriteThinkTool 执行后，工作流通道在 ThinkNode 完成后；前端复用工具 SSE 事件刷新预览
- 生成触发（直连）：复用 GET /app/chat/gen/code，新增可选 codeGenType 参数（原 POST /app/gen/start 作废）；用户消息（固定文案"请根据以上构思，生成代码"）生成前落库、AI 回复流完成后落库
- 生成触发（工作流）：POST /app/gen/workflow，同步阻塞返回 JSON 摘要（B7 启用）
- 同步阻塞超时：Nginx location 600s + 前端 10min + 生成中防重
- RAG 搁置（倾向 DashScope text-embedding + InMemoryEmbeddingStore），B4/B6/B7 预留接入位

## 4. 代码现状关键事实（base 模式相关）
- AppServiceImpl.createApp：AI 路由结果只 log 未落库（selectedCodeGenType 仅日志）→ B5 删除该调用并默认 BASE
- AiCodeGeneratorServiceFactory.createAiCodeGeneratorService 的 switch 无 BASE 分支（default 抛"不支持"）→ BASE 为半死代码，可安全重定义
- AiCodeGeneratorService.generateBaseCodeStream 存在（Flux，prompt/base-prompt.txt），但 base-prompt.txt 是旧的 JSON 角色分配提示词，需重写为构思提示词
- VUE_PROJECT 是"带工具流式"的参考实现：prototype 多例 reasoningStreamingChatModel + TokenStream + toolManager.getAllTools() + hallucinatedToolNameStrategy 防幻觉；generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String)
- dev.langchain4j 同包覆盖 8 类（TokenStream、AiServiceTokenStream 等），已支持工具调用过程的流式输出
- 统一 JSON 流消息：AiResponseMessage / ToolRequestMessage / ToolExecutedMessage（StreamMessageTypeEnum: ai_response/tool_request/tool_executed）；Facade.processTokenStream 把 TokenStream 转 Flux<String>（JSON）
- StreamHandlerExecutor：VUE_PROJECT→JsonMessageStreamHandler；HTML/MULTI_FILE/BASE→SimpleTextStreamHandler。BASE 改 TokenStream 后需改走 JsonMessageStreamHandler
- 工具体系：BaseTool 抽象类（getToolName/getDisplayName/generateToolExecutedResult）+ ToolManager（Spring 注入 BaseTool[] 自动注册）；FileWriteTool 是模板参考（@Tool + @P + @ToolMemoryId Long appId）
- 注意：VUE_PROJECT 注册的是全部工具；BASE 期应只注册 WriteThinkTool（+未来 RagTool），不能注册文件工具（防构思期写代码）
- StaticResourceController：/static/{deployKey}/**，.txt 返回 octet-stream → 需支持渲染产物 HTML 预览
- 现有文件工具（FileWriteTool 等）目录约定 vue_project_{appId}；BASE 目录约定 BASE_{appId}
- 语料 ./语料/（references/rules/skills/documents，约 160 md，mobilebank 管理端 Vue+Yum UI）——RAG 用，暂搁置

## 5. Base 模式开发任务（按序）
阶段二 · 直连通道（先做）：
- B3【新增】ai/tools/WriteThinkTool（@Tool 写 think_{appId}.txt，@ToolMemoryId 拿 appId）
- B3【新增】ThinkService：txt→HTML 渲染（commonmark-java，pom 需加依赖），产物写 BASE_{appId}/ 目录
- B3【改动】StaticResourceController：支持渲染产物预览访问
- B4【改动】AiCodeGeneratorServiceFactory：BASE 分支 = prototype 流式模型 + 仅 WriteThinkTool + 防幻觉（照 VUE_PROJECT 模式）
- B4【改动】AiCodeGeneratorService：BASE 构思方法改 TokenStream（@MemoryId appId，新 prompt）
- B4【改动】AiCodeGeneratorFacade：BASE 分支改构思对话（不走 Parser/Saver），工具回调后触发渲染
- B4【改动】AppServiceImpl.chatToGenCode：codeGenType 空值兜底 BASE（历史应用兼容）
- B4【改动】CodeParserExecutor/CodeFileSaverExecutor：移除 BASE 死分支
- B5【改动】AppController.chatToGenCode：新增可选 codeGenType 参数（html/multi_file/vue_project）
- B5【改动】AppServiceImpl：参数存在且构思期 → 读 think 拼增强提示词 → 走现有生成分支；codeGenType 成功后落库
- B5【改动】AppServiceImpl.createApp：默认 BASE，删路由调用
- 顺序约束：B4 必须先于/同批于 B5 的默认值变更（勿倒序）

阶段三 · 工作流通道（后做）：
- B6【新增】langgraph4j/node/ThinkNode、langgraph4j/workflow/BaseWorkflow（START→think→END，rag 留插槽）、BaseWorkflowExecutor（虚拟线程同步阻塞）；【改动】WorkflowContext 加 appId/userId/codeGenType
- B7 生成图转正：CodeGenConcurrentWorkflow 接业务、CodeGeneratorNode 去硬编码 0L、RouterNode 落库、GenWorkflowExecutor、AppController 工作流端点
- 【搁置】B1/B2 RAG：恢复时先定 embedding（倾向 DashScope+InMemory）→ RagTool（B4 接入）→ RagNode（B6/B7 接入）

## 6. 关键文件索引
- 工厂：src/main/java/org/aistart/ai/AiCodeGeneratorServiceFactory.java
- AI Service 接口：src/main/java/org/aistart/ai/AiCodeGeneratorService.java
- 门面：src/main/java/org/aistart/core/AIfacade/AiCodeGeneratorFacade.java
- 主业务：src/main/java/org/aistart/service/impl/AppServiceImpl.java（chatToGenCode L158、createApp L74）
- 控制器：src/main/java/org/aistart/controller/AppController.java（chatToGenCode L73）
- 流收尾：src/main/java/org/aistart/core/handler/StreamHandlerExecutor.java
- 工具模板：src/main/java/org/aistart/ai/tools/FileWriteTool.java、ToolManager.java、BaseTool.java
- 静态预览：src/main/java/org/aistart/controller/StaticResourceController.java
- 提示词：src/main/resources/prompt/base-prompt.txt（待重写）、vue-prompt.txt
- 常量：src/main/java/org/aistart/constant/AppConstant.java（CODE_OUTPUT_ROOT_DIR）
- 枚举：src/main/java/org/aistart/model/enums/CodeGenTypeEnum.java（base 值已存在，无 DB 迁移）

## 7. 工作约定
- 修改任何文件前需征得用户同意；严格按架构文档/开发文档.txt 的任务拆解执行，不发散
- 用户偏好：step-by-step，从低难度开始，逐文件处理，避免同时大量修改
