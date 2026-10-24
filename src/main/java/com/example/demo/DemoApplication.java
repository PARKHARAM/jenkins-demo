package com.example.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DemoApplication {

    private final String resume;

    public DemoApplication() throws IOException {
        // 이력서 본문은 templates/resume.html. 빌드 번호/커밋과 환경(dev|prod)은 k8s/app.yaml 의 env 로 주입
        String env = System.getenv().getOrDefault("APP_ENV", "local");
        String banner = "dev".equals(env)
            ? "<div class=\"env-dev\">개발계 (dev.hrc0618.shop) · develop 브랜치 최신 빌드</div>"
            : "";
        String html = new ClassPathResource("templates/resume.html").getContentAsString(StandardCharsets.UTF_8);
        this.resume = html
            .replace("{{ENV_BANNER}}", banner)
            .replace("{{APP_ENV}}", env)
            .replace("{{BUILD_INFO}}", System.getenv().getOrDefault("BUILD_INFO", "local"));
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
    public String home() {
        return resume;
    }
}
