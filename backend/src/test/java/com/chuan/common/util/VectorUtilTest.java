package com.chuan.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 向量相似度与 JSON 互转的最小校验。 */
class VectorUtilTest {

    @Test
    void cosineHandlesNormalAndEdgeCases() {
        assertEquals(1.0, VectorUtil.cosine(new double[]{1, 2}, new double[]{2, 4}), 1e-9);
        assertEquals(0.0, VectorUtil.cosine(new double[]{1, 0}, new double[]{0, 1}), 1e-9);
        assertEquals(0.0, VectorUtil.cosine(new double[]{1, 0}, new double[]{1}));
        assertEquals(0.0, VectorUtil.cosine(new double[0], new double[0]));
        assertEquals(0.0, VectorUtil.cosine(null, new double[]{1}));
    }

    @Test
    void vectorJsonRoundTrip() {
        ObjectMapper mapper = new ObjectMapper();
        double[] vector = {0.1, -2.0, 3.25};
        String json = VectorUtil.toJson(mapper, vector);
        assertArrayEquals(vector, VectorUtil.fromJson(mapper, json), 1e-9);
        assertArrayEquals(new double[0], VectorUtil.fromJson(mapper, "not-json"));
    }
}