package com.ghostchu.btn.sparkle.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.ghostchu.btn.sparkle.module.user.UserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {
    private final UserService userService;

    public SaTokenConfigure(UserService userService) {
        this.userService = userService;
    }

    // 注册拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，校验规则为 StpUtil.checkLogin() 登录校验。
        registry.addInterceptor(new SaInterceptor(s -> {
                    long userId = StpUtil.getLoginIdAsLong();
                    if(userId > 0) {
                        if (userService.getUser(userId).isEmpty()) {
                            StpUtil.logout();
                        }
                    }
                    StpUtil.checkLogin();
                }))
                .addPathPatterns("/")
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**")
                .excludePathPatterns("/ping/**")
                .excludePathPatterns("/api/user/me")
                .excludePathPatterns("/api/user/cred");
    }
}