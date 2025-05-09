package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 用户信息持有者
 * 基于RequestContextHolder实现用户信息的请求级隔离
 */
public class UserHolder {
    private static final String USER_KEY = "current_user";

    public static void saveUser(UserDTO user){
        RequestContextHolder.currentRequestAttributes()
                .setAttribute(USER_KEY, user, RequestAttributes.SCOPE_REQUEST);
    }

    public static UserDTO getUser(){
        return (UserDTO) RequestContextHolder.currentRequestAttributes()
                .getAttribute(USER_KEY, RequestAttributes.SCOPE_REQUEST);
    }

    public static void removeUser(){
        RequestContextHolder.currentRequestAttributes()
                .removeAttribute(USER_KEY, RequestAttributes.SCOPE_REQUEST);
    }
}
