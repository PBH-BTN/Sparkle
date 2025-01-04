package com.ghostchu.btn.sparkle.module.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.ghostchu.btn.sparkle.module.userscore.UserScoreService;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@SaCheckLogin
@RequestMapping("/user")
public class UserViewController {
    private final UserService userService;
    private final UserScoreService userScoreService;

    public UserViewController(UserService userService, UserScoreService userScoreService) {
        this.userService = userService;
        this.userScoreService = userScoreService;
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        var dto = userService.toDto(userService.getUser((StpUtil.getLoginIdAsLong())).get());
        model.addAttribute("user", dto);
        var userScore = userScoreService.getUserScoreBytes(userService.getUser((StpUtil.getLoginIdAsLong())).get());
        model.addAttribute("userScoreBytes.display", FileUtils.byteCountToDisplaySize(userScore));
        model.addAttribute("userScoreBytes.raw", userScore);
        return "user/profile";
    }
}
