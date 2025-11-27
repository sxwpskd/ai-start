package org.aistart.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import org.aistart.model.dto.user.UserQueryRequest;
import org.aistart.model.entity.User;
import org.aistart.model.vo.LoginUserVO;
import org.aistart.model.vo.UserVO;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author <a>me</a>
 */
public interface UserService extends IService<User> {
    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);
    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);
    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);
    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);
    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);
    /**
     * 获取他人用户信息
     *
     * @param user  用户
     * @return
     */
    UserVO getUserVO(User user);
    /**
     * 获取他人用户信息(分页）
     *
     * @param userList  用户列表
     * @return
     */
    List<UserVO> getUserVOList(List<User> userList);
    /**
     * 查询，后续可以考虑使用ES
     *
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);
    /**
    * 加密
    * */
    String encryptPassword(String password);

}
