package com.chuan.module.system.service;

import com.chuan.common.exception.BusinessException;
import com.chuan.module.system.vo.CaptchaVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图形验证码服务：生成与校验（内存存储，5 分钟有效，一次性使用）。
 */
@Slf4j
@Service
public class CaptchaService {

    private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final long TTL_MILLIS = 5 * 60 * 1000L;

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** 生成验证码并返回图片 + 标识。 */
    public CaptchaVO generate() {
        String code = randomCode();
        String id = UUID.randomUUID().toString().replace("-", "");
        store.put(id, new Entry(code, System.currentTimeMillis() + TTL_MILLIS));
        // 清理过期项，避免内存无限增长
        store.entrySet().removeIf(e -> e.getValue().expireAt < System.currentTimeMillis());
        return CaptchaVO.builder()
                .captchaId(id)
                .image("data:image/png;base64," + Base64.getEncoder().encodeToString(render(code)))
                .build();
    }

    /** 校验验证码（忽略大小写），成功后立即失效。 */
    public void verify(String captchaId, String code) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(code)) {
            throw new BusinessException("请输入图形验证码");
        }
        Entry entry = store.remove(captchaId);
        if (entry == null) {
            throw new BusinessException("验证码已失效，请刷新后重试");
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            throw new BusinessException("验证码已过期，请刷新后重试");
        }
        if (!entry.code.equalsIgnoreCase(code.trim())) {
            throw new BusinessException("验证码错误");
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private byte[] render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线
        for (int i = 0; i < 5; i++) {
            g.setColor(randomColor(160, 220));
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // 字符
        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(randomColor(40, 140));
            int x = 16 + i * 24 + random.nextInt(4);
            int y = 28 + random.nextInt(4);
            double angle = ThreadLocalRandom.current().nextDouble(-0.25, 0.25);
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-angle, x, y);
        }

        // 噪点
        for (int i = 0; i < 60; i++) {
            g.setColor(randomColor(150, 240));
            g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 1, 1);
        }

        g.dispose();
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", os);
            return os.toByteArray();
        } catch (Exception e) {
            log.error("生成验证码图片失败", e);
            throw new BusinessException("验证码生成失败");
        }
    }

    private Color randomColor(int min, int max) {
        return new Color(random.nextInt(min, max), random.nextInt(min, max), random.nextInt(min, max));
    }

    private record Entry(String code, long expireAt) {
    }
}