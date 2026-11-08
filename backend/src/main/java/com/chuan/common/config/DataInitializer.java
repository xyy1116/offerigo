package com.chuan.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuan.module.system.entity.SysUser;
import com.chuan.module.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器：首次启动时若不存在 admin 用户则自动创建默认管理员。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DataInitializer implements CommandLineRunner {

    private final SysUserService sysUserService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        long count = sysUserService.count(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (count == 0) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNickname("系统管理员");
            admin.setStatus(1);
            sysUserService.save(admin);
            log.info("已初始化默认管理员账号: admin / admin123");
        }
    }
}