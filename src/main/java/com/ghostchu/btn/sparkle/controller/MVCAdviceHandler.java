package com.ghostchu.btn.sparkle.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ghostchu.btn.sparkle.module.user.UserDto;
import com.ghostchu.btn.sparkle.module.user.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class MVCAdviceHandler {
    private final UserService userService;

    public MVCAdviceHandler(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute("user")
    public UserDto addUserToModel() {
        return userService.toDto(userService.getUser((StpUtil.getLoginIdAsLong())).get());
    }
}
