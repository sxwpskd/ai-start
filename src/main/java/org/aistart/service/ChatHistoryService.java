package org.aistart.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
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
 *
 * 删除应用对话历史
 *
 * @param appId 应用ID
 * @return 是否删除成功
 *
 * */
    boolean deleteByAppId(Long appId);

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);
}
