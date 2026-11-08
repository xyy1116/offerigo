package com.chuan.module.system.controller;

import com.chuan.common.result.Result;
import com.chuan.module.system.dto.ForgotQuestionDTO;
import com.chuan.module.system.dto.ForgotResetDTO;
import com.chuan.module.system.dto.LoginDTO;
import com.chuan.module.system.dto.RegisterDTO;
import com.chuan.module.system.service.CaptchaService;
import com.chuan.module.system.service.SysUserService;
import com.chuan.module.system.vo.CaptchaVO;
import com.chuan.module.system.vo.LoginVO;
import com.chuan.module.system.vo.SecurityQuestionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.success(captchaService.generate());
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(sysUserService.login(dto));
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        sysUserService.register(dto);
        return Result.success();
    }

    @PostMapping("/forgot/question")
    public Result<SecurityQuestionVO> forgotQuestion(@Valid @RequestBody ForgotQuestionDTO dto) {
        return Result.success(sysUserService.getSecurityQuestion(dto));
    }

    @PostMapping("/forgot/reset")
    public Result<Void> forgotReset(@Valid @RequestBody ForgotResetDTO dto) {
        sysUserService.resetPassword(dto);
        return Result.success();
    }
}