package org.aistart.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.aistart.model.dto.chathistory.ChatHistoryQueryRequest;
import org.aistart.model.entity.ChatHistory;
import org.aistart.model.entity.User;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a>me</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {
/**
 * 添加对话历史
 *
 * @param appId 应用ID
 * @param message 消息内容
 * @param messageType 消息类型
 * @param userId 用户ID
 * @return 是否添加成功
 * */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

/**
 * 删除应用对话历史
 *
 * @param appId 应用ID
 * @return 是否删除成功
 *
 * */
    boolean deleteByAppId(Long appId);

/**
 * 加载对话历史，目的是给用户看
 *
 * @param appId 应用ID
 * @param pageSize 页面大小
 * @param lastCreateTime 最后创建时间
 * @param loginUser 当前登录用户
 * @return 对话历史
 * */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);
/**
 * 加载对话历史到内存，给ai看
 *
 * @param appId 应用ID
 * @param chatMemory 当前缓存
 * @param maxCount 最大加载条数
 * @return 加载条数
 * */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

/**
 * 获取查询包装类,构造查询条件
 *
 * @param chatHistoryQueryRequest 查询条件
 * @return 查询条件
 *
 * */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

}
