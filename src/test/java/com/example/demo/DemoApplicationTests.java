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
        assertThat(html).contains("<h1>박하람</h1>");             // 이력서 문구가 바뀌어도 깨지지 않게 이름만 확인
        assertThat(html).contains("<h2>학력</h2>", "<h2>자격</h2>", "<h2>병역 사항</h2>");
        assertThat(html).contains("href=\"/cover-letter\"", "href=\"/cicd\"");
        assertThat(html).doesNotContain("현재 Deployment");
        assertThat(html).contains("Build <code>local</code>");   // 배포 검증 단계가 찾는 빌드 정보 자리
        assertThat(html).doesNotContain("{{");   // 치환 안 된 플레이스홀더가 없어야 함
    }

    @Test
    void coverLetterRendersFullContent() {
        String html = app.coverLetter();
        assertThat(html).contains("<h1>자기소개서</h1>");
        assertThat(html).contains("업무 자동화와 플랫폼 개선", "CI/CD와 Kubernetes 실무 경험", "입사 후 포부");
        assertThat(html).doesNotContain("{{");
    }

    @Test
    void cicdPageRendersPipelineAndDeployment() {
        String html = app.cicd();
        assertThat(html).contains("<h1>Jenkins CI/CD 이력서 웹 애플리케이션</h1>");
        assertThat(html).contains("배포 파이프라인", "현재 Deployment", "Trivy 통과");
        assertThat(html).doesNotContain("{{");
    }
}
