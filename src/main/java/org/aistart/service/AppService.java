package org.aistart.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.aistart.model.dto.app.AppQueryRequest;
import org.aistart.model.entity.App;
import org.aistart.model.entity.User;
import org.aistart.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a>me</a>
 */
public interface AppService extends IService<App> {
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
 *
 * */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 应用部署
     *
     * */
    String deployApp(Long appId, User loginUser);

    void generateAppScreenshotAsync(Long appId, String appUrl);
}
