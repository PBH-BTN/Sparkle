package com.ghostchu.btn.sparkle.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.ghostchu.btn.sparkle.module.user.UserDto;
import com.ghostchu.btn.sparkle.module.user.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.OffsetDateTime;

@ControllerAdvice
public class MVCAdviceHandler {
    private final UserService userService;

    public MVCAdviceHandler(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute("user")
    public UserDto addUserToModel() {
        try {
            var optional = userService.getUser((StpUtil.getLoginIdAsLong()));
            // 同时更新最后访问时间
            if (optional.isPresent()) {
                var user = optional.get();
                // If over 15 minutes
                if (user.getLastSeenAt().plusMinutes(15).isBefore(OffsetDateTime.now())) {
                    user.setLastSeenAt(OffsetDateTime.now());
                    userService.saveUser(user);
                }
            }
            return optional.map(userService::toDto).orElse(null);
        } catch (NotLoginException e) {
            return null;
        }
    }
}
