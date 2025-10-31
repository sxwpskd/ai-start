package org.aistart.model.dto;

import lombok.Data;

import java.io.Serializable;
/*
用户注册请求
* Serializable使得可以序列化，保存硬盘
* */
@Data
public class UserRegisterRequest implements Serializable {

    // 序列化版本UID，用于在序列化和反序列化过程中验证版本一致性
    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 账号
     * 用于存储用户的账号信息
     */
    private String userAccount;

    /**
     * 密码
     * 用于存储用户的密码信息
     */
    private String userPassword;

    /**
     * 确认密码
     * 用于用户注册时二次确认密码，确保两次输入的密码一致
     */
    private String checkPassword;
}
