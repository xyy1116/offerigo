package com.chuan.module.jobmatch.service;

import com.chuan.module.jobmatch.vo.ResumeTemplateVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResumeTemplateCatalogTest {

    @Test
    void builtinsAreDistinctAndFallbackWorks() {
        ResumeTemplateCrawler catalog = new ResumeTemplateCrawler();
        List<ResumeTemplateVO> list = catalog.listTemplates();

        assertEquals(4, list.size());
        assertEquals(4, list.stream().map(ResumeTemplateVO::getName).distinct().count());
        assertEquals(4, list.stream().map(ResumeTemplateVO::getLayout).distinct().count());
        assertEquals("builtin-1", catalog.getTemplate("missing-template").getId());
    }
}