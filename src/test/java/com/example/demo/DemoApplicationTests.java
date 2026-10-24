package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoApplicationTests {

    @Autowired
    DemoApplication app;

    @Test
    void contextLoads() {
    }

    @Test
    void resumePageRendersWithBuildInfo() {
        String html = app.home();
        assertThat(html).contains("<title>박하람 · 이력서</title>");
        assertThat(html).doesNotContain("{{");   // 치환 안 된 플레이스홀더가 없어야 함
    }
}
