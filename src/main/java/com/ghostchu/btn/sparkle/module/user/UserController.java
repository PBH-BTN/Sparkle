package com.ghostchu.btn.sparkle.module.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.exception.UserNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController extends SparkleController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @SaCheckLogin
    @GetMapping("/user/me")
    public UserDto me() {
        return userService.toDto(userService.getUser((Long) StpUtil.getLoginId()).get());
    }

    @SaCheckPermission("user:read.other")
    @GetMapping("/user/{id}")
    public UserDto other(@PathVariable("id") Long id) throws UserNotFoundException {
        var usrOptional = userService.getUser(id);
        if(usrOptional.isEmpty()){
            throw new UserNotFoundException();
        }
        return userService.toDto(usrOptional.get());
    }

    @SaCheckLogin
    @GetMapping("/user/logout")
    public ResponseEntity<Void> logout() {
        StpUtil.logout();
        return ResponseEntity.status(200).build();
    }
}
