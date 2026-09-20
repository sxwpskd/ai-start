package org.aistart.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RagServiceTest {

    @Resource
    private RagService ragService;

    @Test
    void testSearch() {
        List<RagService.RagFragment> fragments = ragService.search("查询列表页面的实现规范", 5);
        fragments.forEach(f -> System.out.println(f.score() + " | " + f.source()));
        System.out.println(ragService.formatFragments(fragments));
    }
}