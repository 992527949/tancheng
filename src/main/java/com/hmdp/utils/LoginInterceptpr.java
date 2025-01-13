package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptpr implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1 获取session
        HttpSession session = request.getSession();
        // 2 获取用户信息
        Object user = session.getAttribute("user");
        // 3 判断用户是否存在
        if (user == null) {
            // 4不存在，拦截
            response.setStatus(401);
            return false;
        }
        // 4不存在，拦截
        // 5存在保存信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        // 6放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
