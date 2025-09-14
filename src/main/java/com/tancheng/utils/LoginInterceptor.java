package com.tancheng.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断是否需要拦截（Threadlocal是否有用户）
        if (UserHolder.getUser() ==null){
            response.setStatus(401);
            return false;
        }
        // 有用户，放行
        return true;
    }
}
