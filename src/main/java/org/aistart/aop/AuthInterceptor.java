package org.aistart.aop;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aistart.annotation.AuthCheck;
import org.aistart.common.ResultUtils;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.model.entity.User;
import org.aistart.model.enums.UserRoleEnum;
import org.aistart.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Aspect
public class AuthInterceptor {
    @Resource
    private UserService userService;


    @Around("@annotation(authCheck)")//拦截带有AuthCheck注解的方法
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRule = authCheck.mustRole();
        //校验用户是否具备业务访问权限,有些业务不需要权限,如果需要，就继续判断
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRule);
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        //获取当前登录用户请求
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        //判断用户是否具备业务访问权限
        if (userRoleEnum == null) {//用户角色为空
            return new BusinessException(ErrorCode.NO_AUTH_ERROR,"用户无权限");
        } else if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {//要求用户角色为管理员，但用户不是
            return new BusinessException(ErrorCode.NO_AUTH_ERROR,"用户权限不足");
        }
        return joinPoint.proceed();
    }
}
