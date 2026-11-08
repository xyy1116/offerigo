package com.chuan.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chuan.module.system.dto.ForgotQuestionDTO;
import com.chuan.module.system.dto.ForgotResetDTO;
import com.chuan.module.system.dto.LoginDTO;
import com.chuan.module.system.dto.RegisterDTO;
import com.chuan.module.system.entity.SysUser;
import com.chuan.module.system.vo.LoginVO;
import com.chuan.module.system.vo.SecurityQuestionVO;
import com.chuan.module.system.vo.UserVO;

/**
 * 系统用户服务接口。
 */
public interface SysUserService extends IService<SysUser> {

    LoginVO login(LoginDTO loginDTO);

    void register(RegisterDTO registerDTO);

    UserVO getProfile(Long userId);

    /** 保存/删除头像，传空表示恢复默认头像 */
    void saveAvatar(Long userId, String avatar);

    SecurityQuestionVO getSecurityQuestion(ForgotQuestionDTO dto);

    void resetPassword(ForgotResetDTO dto);
}