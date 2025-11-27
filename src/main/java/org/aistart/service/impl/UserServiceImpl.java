package org.aistart.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.model.dto.user.UserQueryRequest;
import org.aistart.model.entity.User;
import org.aistart.mapper.UserMapper;
import org.aistart.model.enums.UserRoleEnum;
import org.aistart.model.vo.LoginUserVO;
import org.aistart.model.vo.UserVO;
import org.aistart.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.aistart.constant.UserConstant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户 服务层实现。
 *
 * @author <a>me</a>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //1.校验参数
        if(StrUtil.hasBlank(userAccount,userPassword,checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if(checkPassword.length()<8 || userPassword.length()<8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码长度不能小于8位");
        }
        if(userAccount.length()<4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号不能小于4位");
            }
        if(!userPassword.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次密码不一致");
        }

        //2.查询用户是否存在
        long count = this.mapper.selectCountByQuery(
                new QueryWrapper().eq("userAccount", userAccount));
        if(count>0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户已存在");
        }

        //3.加密密码，无良可以不加密，这样就能在数据库直接看
        String encryptPassword = this.encryptPassword(userPassword);

        //4.注册用户
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName(userAccount);
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if(!saveResult){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"注册失败");
        }

        return user.getId();
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);

        return loginUserVO;

    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1.校验参数
        if(StrUtil.hasBlank(userAccount,userPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if(userAccount.length()<4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号不能小于4位");
        }
        if(userPassword.length()<8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码长度不能小于8位");
        }
        //2.加密并检验密码
        String encryptPassword = this.encryptPassword(userPassword);
        //3.查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if(user == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在或密码错误");
        }
        //4.记录用户
        /*存入session，它是k-v，这里整个user存入了v*/
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,user);

       return getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User user = (User) userObj;
        if(user == null || user.getId() == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"未登录");
        }
        //从数据库获取用户信息
        long userId = user.getId();
        user = this.getById(userId);
        if(user == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"未登录");
        }
        return user;

    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User user = (User) userObj;
        if(user == null || user.getId() == null){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"用户未登录");
        }
        //解除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO UserVO = new UserVO();
        BeanUtil.copyProperties(user, UserVO);

        return UserVO;
    }
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollectionUtil.isEmpty(userList)) {
            return null;
        }

        return userList.stream()
                .map(user -> getUserVO(user))
                .collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }



    @Override
    public String encryptPassword(String password){
        //盐值
        final String SALT = "yazhi";
        return DigestUtils.md5DigestAsHex(
                (SALT+password+SALT).getBytes(StandardCharsets.UTF_8));
    }
}
