package com.chuan.common.util;

import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;

/**
 * 外部链接安全校验：只允许公网 http(s)，阻断 localhost、私网、链路本地和组播地址，降低 SSRF 风险。
 */
public final class SafeUrlValidator {

    private SafeUrlValidator() {
    }

    public static boolean isSafeHttpUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            String lower = host.toLowerCase();
            if ("localhost".equals(lower) || lower.endsWith(".localhost") || lower.endsWith(".local")) {
                return false;
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    return false;
                }
                byte[] bytes = address.getAddress();
                // IPv6 fc00::/7 唯一本地地址
                if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}