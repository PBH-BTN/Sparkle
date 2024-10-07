package com.ghostchu.btn.sparkle.module.userapp;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.exception.TooManyUserApplicationException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.exception.UserNotFoundException;
import com.ghostchu.btn.sparkle.module.audit.AuditService;
import com.ghostchu.btn.sparkle.module.user.UserService;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import com.ghostchu.btn.sparkle.wrapper.StdResp;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

@RestController
@SaCheckLogin
@RequestMapping("/api")
public class UserApplicationController extends SparkleController {
    private final UserApplicationService userApplicationService;
    private final UserService userService;
    private final ObjectMapper jacksonObjectMapper;
    private final AuditService auditService;
    private final HttpServletRequest req;

    public UserApplicationController(UserApplicationService userApplicationService, UserService userService,
                                     ObjectMapper jacksonObjectMapper, AuditService auditService, HttpServletRequest req) {
        this.userApplicationService = userApplicationService;
        this.userService = userService;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.auditService = auditService;
        this.req = req;
    }

    @GetMapping("/userapp")
    public StdResp<List<UserApplicationDto>> getUserApplications() {
        return new StdResp<>(true, null,
                userApplicationService.getUserApplications(
                                userService.getUser(StpUtil.getLoginIdAsLong()).orElseThrow())
                        .stream()
                        .map(userApplicationService::toDto).toList());
    }

    @GetMapping("/userapp/{appId}")
    public StdResp<UserApplicationDto> getUserApplication(@PathVariable("appId") String appId) throws UserApplicationNotFoundException {
        var usrApp = userApplicationService.getUserApplication(appId).orElseThrow(UserApplicationNotFoundException::new);
        if (!Objects.equals(usrApp.getUser().getId(), StpUtil.getLoginIdAsLong())) {
            StpUtil.checkPermission("userapp.read-other-app");
        }
        return new StdResp<>(true, null, userApplicationService.toDto(usrApp));
    }

    @PostMapping("/userapp/{appId}/resetAppSecret")
    public StdResp<UserApplicationVerboseDto> resetUserApplicationAppSecret(@PathVariable("appId") String appId) throws UserApplicationNotFoundException {
        var usrApp = userApplicationService.getUserApplication(appId).orElseThrow(UserApplicationNotFoundException::new);
        if (!Objects.equals(usrApp.getUser().getId(), StpUtil.getLoginIdAsLong())) {
            StpUtil.checkPermission("userapp.reset-other-appsecret");
        }
        var audit = new LinkedHashMap<String, Object>();
        audit.put("appId", appId);
        audit.put("userAppOwner", usrApp.getUser().getId());
        audit.put("userId", StpUtil.getLoginIdAsLong());
        auditService.log(req, "USERAPP_RESET_SECRET", true, audit);
        return new StdResp<>(true, null, userApplicationService.toVerboseDto(userApplicationService.resetUserApplicationSecret(usrApp.getId())));
    }

    @PatchMapping("/userapp/{appId}")
    public StdResp<UserApplicationDto> editUserApplication(@PathVariable("appId") String appId, @RequestBody UserApplicationEditRequest req) throws UserApplicationNotFoundException {
        var usrApp = userApplicationService.getUserApplication(appId).orElseThrow(UserApplicationNotFoundException::new);
        if (!Objects.equals(usrApp.getUser().getId(), StpUtil.getLoginIdAsLong())) {
            StpUtil.checkPermission("userapp.edit-other-app");
        }
        var audit = new LinkedHashMap<String, Object>();
        audit.put("appId", appId);
        audit.put("userAppOwner", usrApp.getUser().getId());
        audit.put("userId", StpUtil.getLoginIdAsLong());
        audit.put("userAppOld", usrApp.getComment());
        audit.put("userAppNew", req.getComment());
        auditService.log(this.req, "USERAPP_EDIT", true, audit);
        return new StdResp<>(true, null, userApplicationService.toDto(userApplicationService.editUserApplicationComment(usrApp.getId(), req.getComment())));
    }

    @DeleteMapping("/userapp/{appId}")
    public StdResp<Void> deleteUserApplication(@PathVariable("appId") String appId) throws UserApplicationNotFoundException {
        var usrApp = userApplicationService.getUserApplication(appId).orElseThrow(UserApplicationNotFoundException::new);
        if (!Objects.equals(usrApp.getUser().getId(), StpUtil.getLoginIdAsLong())) {
            StpUtil.checkPermission("userapp.delete-other-app");
        }
        var audit = new LinkedHashMap<String, Object>();
        audit.put("appId", appId);
        audit.put("userAppId", usrApp.getAppId());
        audit.put("userAppOwner", usrApp.getUser().getId());
        audit.put("userId", StpUtil.getLoginIdAsLong());
        auditService.log(this.req, "USERAPP_DELETE", true, audit);
        userApplicationService.deleteUserApplication(usrApp.getId());
        return new StdResp<>(true, "用户应用程序删除成功", null);
    }

    @PutMapping("/userapp")
    public StdResp<UserApplicationVerboseDto> createUserApplication(@RequestBody UserApplicationCreateRequest req) throws UserNotFoundException, TooManyUserApplicationException {
        var user = userService.getUser(StpUtil.getLoginIdAsLong()).orElseThrow();
        var usrApp = userApplicationService.generateUserApplicationForUser(user, req.getComment(), TimeUtil.toUTC(System.currentTimeMillis()));
        var audit = new LinkedHashMap<String, Object>();
        audit.put("appId", usrApp.getAppId());
        audit.put("userAppId", usrApp.getAppId());
        audit.put("userAppOwner", usrApp.getUser().getId());
        audit.put("userId", StpUtil.getLoginIdAsLong());
        auditService.log(this.req, "USERAPP_CREATE", true, audit);
        return new StdResp<>(true, null, userApplicationService.toVerboseDto(usrApp));
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
