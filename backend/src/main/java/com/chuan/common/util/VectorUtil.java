package com.chuan.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * 向量工具：余弦相似度 + JSON 互转。
 * 向量直接以 JSON 数组存 MySQL，不引入向量库；岗位量在千级以内足够。
 */
public final class VectorUtil {

    private VectorUtil() {
    }

    /** 余弦相似度，维度不一致或零向量返回 0。 */
    public static double cosine(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static String toJson(ObjectMapper mapper, double[] vector) {
        try {
            return mapper.writeValueAsString(vector);
        } catch (Exception e) {
            return null;
        }
    }

    public static double[] fromJson(ObjectMapper mapper, String json) {
        if (!StringUtils.hasText(json)) {
            return new double[0];
        }
        try {
            double[] vector = mapper.readValue(json, double[].class);
            return vector == null ? new double[0] : vector;
        } catch (Exception e) {
            return new double[0];
        }
    }
}