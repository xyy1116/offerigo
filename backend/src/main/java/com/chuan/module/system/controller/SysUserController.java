package com.chuan.module.system.controller;

import com.chuan.common.result.Result;
import com.chuan.module.system.service.SysUserService;
import com.chuan.module.system.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户信息接口。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping("/profile")
    public Result<UserVO> profile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(sysUserService.getProfile(userId));
    }

    /** 保存/删除头像，传空字符串表示恢复默认头像。 */
    @PostMapping("/avatar")
    public Result<Void> saveAvatar(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        sysUserService.saveAvatar(userId, body == null ? null : body.get("avatar"));
        return Result.success();
    }
}