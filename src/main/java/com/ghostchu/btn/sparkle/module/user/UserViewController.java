package com.ghostchu.btn.sparkle.module.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@SaCheckLogin
@RequestMapping("/user")
public class UserViewController {
    private final UserService userService;

    public UserViewController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        var dto = userService.toDto(userService.getUser((StpUtil.getLoginIdAsLong())).get());
        model.addAttribute("user", dto);
        return "user/profile";
    }
}
