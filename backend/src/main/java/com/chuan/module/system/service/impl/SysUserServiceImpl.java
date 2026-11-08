package com.chuan.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuan.common.exception.BusinessException;
import com.chuan.common.result.ResultCode;
import com.chuan.common.util.JwtUtil;
import com.chuan.module.system.dto.ForgotQuestionDTO;
import com.chuan.module.system.dto.ForgotResetDTO;
import com.chuan.module.system.dto.LoginDTO;
import com.chuan.module.system.dto.RegisterDTO;
import com.chuan.module.system.entity.SysUser;
import com.chuan.module.system.mapper.SysUserMapper;
import com.chuan.module.system.service.CaptchaService;
import com.chuan.module.system.service.SysUserService;
import com.chuan.module.system.vo.LoginVO;
import com.chuan.module.system.vo.SecurityQuestionVO;
import com.chuan.module.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 系统用户服务实现。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /** 头像 data URI：只允许位图，避免 SVG 等可执行内容 */
    private static final Pattern AVATAR_URI =
            Pattern.compile("^data:image/(?:jpeg|jpg|png|webp);base64,[A-Za-z0-9+/=]+$");
    /** 头像压缩到 256px 后约几十 KB，1MB 足够且能挡住异常大请求 */
    private static final int MAX_AVATAR_CHARS = 1_000_000;

    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;

    @Override
    public LoginVO login(LoginDTO dto) {
        captchaService.verify(dto.getCaptchaId(), dto.getCaptchaCode());
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(),
                Boolean.TRUE.equals(dto.getRememberMe()));
        return LoginVO.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
    }

    @Override
    public void register(RegisterDTO dto) {
        captchaService.verify(dto.getCaptchaId(), dto.getCaptchaCode());
        long count = count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        if (StringUtils.hasText(dto.getEmail())) {
            long emailCount = count(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, dto.getEmail()));
            if (emailCount > 0) {
                throw new BusinessException("该邮箱已被注册");
            }
        }
        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setSecurityAnswer(passwordEncoder.encode(dto.getSecurityAnswer()));
        user.setStatus(1);
        save(user);
    }

    @Override
    public UserVO getProfile(Long userId) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void saveAvatar(Long userId, String avatar) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!StringUtils.hasText(avatar)) {
            // updateById 会忽略 null 字段，删除头像必须显式置空
            update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, userId)
                    .set(SysUser::getAvatar, null));
            return;
        }
        String trimmed = avatar.trim();
        if (trimmed.length() > MAX_AVATAR_CHARS || !AVATAR_URI.matcher(trimmed).matches()) {
            throw new BusinessException("头像格式不支持或文件过大");
        }
        user.setAvatar(trimmed);
        updateById(user);
    }

    @Override
    public SecurityQuestionVO getSecurityQuestion(ForgotQuestionDTO dto) {
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        if (!StringUtils.hasText(user.getSecurityQuestion())) {
            throw new BusinessException("该账号未设置密保问题，无法找回密码");
        }
        SecurityQuestionVO vo = new SecurityQuestionVO();
        vo.setUsername(user.getUsername());
        vo.setSecurityQuestion(user.getSecurityQuestion());
        return vo;
    }

    @Override
    public void resetPassword(ForgotResetDTO dto) {
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        if (!StringUtils.hasText(user.getSecurityAnswer())
                || !passwordEncoder.matches(dto.getSecurityAnswer(), user.getSecurityAnswer())) {
            throw new BusinessException("密保答案错误");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(user);
    }
}