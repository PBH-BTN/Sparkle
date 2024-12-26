package com.ghostchu.btn.sparkle.module.userapp;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.ghostchu.btn.sparkle.exception.TooManyUserApplicationException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.exception.UserNotFoundException;
import com.ghostchu.btn.sparkle.module.user.UserService;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@Controller
@SaCheckLogin
@RequestMapping("/userapp")
public class UserApplicationViewController {

    private final UserApplicationService userApplicationService;
    private final UserService userService;

    public UserApplicationViewController(UserApplicationService userApplicationService, UserService userService) {
        this.userApplicationService = userApplicationService;
        this.userService = userService;
    }

    @GetMapping("/")
    public String userApplicationIndex(Model model) {
        var list = userApplicationService.getUserApplications(
                        userService.getUser(StpUtil.getLoginIdAsLong()).orElseThrow())
                .stream()
                .map(userApplicationService::toDto).toList();
        model.addAttribute("userapps", list);
        return "modules/userapp/index";
    }

    @GetMapping("/{appId}/resetAppSecret")
    public String resetUserApplicationAppSecret(Model model, @PathVariable("appId") String appId) throws UserApplicationNotFoundException {
        var userApp = userApplicationService.getUserApplication(appId).orElseThrow(UserApplicationNotFoundException::new);
        if (!Objects.equals(userApp.getUser().getId(), StpUtil.getLoginIdAsLong())) {
            StpUtil.checkPermission("userapp.reset-other-appsecret");
        }
        model.addAttribute("userapp", userApp);
        return "modules/userapp/created";
    }


    @GetMapping("/{appId}/delete")
    public String deleteUserApplication(@PathVariable("appId") String appId) throws UserApplicationNotFoundException {
        var usrApp = userApplicationService.getUserApplication(appId).orElseThrow(UserApplicationNotFoundException::new);
        if (!Objects.equals(usrApp.getUser().getId(), StpUtil.getLoginIdAsLong())) {
            StpUtil.checkPermission("userapp.delete-other-app");
        }
        userApplicationService.deleteUserApplication(usrApp.getId());
        return "redirect:/userapp/";
    }

    @GetMapping("/create")
    public String createUserApplication() {
        return "modules/userapp/create";
    }

    @PostMapping("/create")
    public String createUserApplication(Model model, @RequestParam String comment) throws UserNotFoundException, TooManyUserApplicationException {
        var user = userService.getUser(StpUtil.getLoginIdAsLong()).orElseThrow();
        var usrApp = userApplicationService.generateUserApplicationForUser(user, comment, TimeUtil.toUTC(System.currentTimeMillis()));
        model.addAttribute("userapp", usrApp);
        return "modules/userapp/created";
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserApplicationCreateRequest {
        private String comment;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserApplicationEditRequest {
        private String comment;
    }
}
