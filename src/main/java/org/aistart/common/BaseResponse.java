package org.aistart.common;

import lombok.Data;
import org.aistart.exception.ErrorCode;

import java.io.Serializable;
/*
* 用的响应封装类，主要用于API接口的统一返回格式
* 方便前端操作
* */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}

