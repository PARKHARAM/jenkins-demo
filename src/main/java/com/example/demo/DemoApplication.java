package com.example.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DemoApplication {

    private final String resume;
    private final String coverLetter;
    private final String cicd;

    public DemoApplication() throws IOException {
        // 이력서 본문은 templates/resume.html. 빌드 번호/커밋과 환경(dev|prod)은 k8s/app.yaml 의 env 로 주입
        String env = System.getenv().getOrDefault("APP_ENV", "local");
        String banner = "dev".equals(env)
            ? "<div class=\"env-dev\">개발계 (dev.hrc0618.shop) · develop 브랜치 최신 빌드</div>"
            : "";
        // BUILD_INFO = "#<빌드번호> <커밋>", IMAGE = ".../jenkins-demo:<소스 트리 해시>"
        String buildInfo = System.getenv().getOrDefault("BUILD_INFO", "local");
        String[] parts = buildInfo.split(" ", 2);
        String commit = parts.length > 1
            ? "<a href=\"https://github.com/PARKHARAM/jenkins-demo/commit/" + parts[1] + "\" target=\"_blank\" rel=\"noopener\"><code>" + parts[1] + "</code></a>"
            : "local";
        String image = System.getenv().getOrDefault("IMAGE", "local");
        String envBadge = switch (env) {
            case "dev" -> "<span class=\"badge dev\">개발계 · dev</span>";
            case "prod" -> "<span class=\"badge prod\">운영계 · prod</span>";
            default -> "<span class=\"badge local\">로컬 · local</span>";
        };
        String startedAt = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'KST'"));
        this.resume = renderTemplate("templates/resume.html", banner, envBadge, env, parts[0], commit, image, startedAt, buildInfo);
        this.coverLetter = renderTemplate("templates/cover-letter.html", banner, envBadge, env, parts[0], commit, image, startedAt, buildInfo);
        this.cicd = renderTemplate("templates/cicd.html", banner, envBadge, env, parts[0], commit, image, startedAt, buildInfo);
    }

    private static String renderTemplate(String path, String banner, String envBadge, String env,
            String buildNo, String commit, String image, String startedAt, String buildInfo) throws IOException {
        String html = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        return html
            .replace("{{ENV_BANNER}}", banner)
            .replace("{{ENV_BADGE}}", envBadge)
            .replace("{{APP_ENV}}", env)
            .replace("{{BUILD_NO}}", buildNo)
            .replace("{{COMMIT}}", commit)
            .replace("{{IMAGE_TAG}}", image.substring(image.lastIndexOf(':') + 1))
            .replace("{{STARTED_AT}}", startedAt)
            .replace("{{BUILD_INFO}}", buildInfo);
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
    public String home() {
        return resume;
    }

    @GetMapping(value = "/cover-letter", produces = "text/html;charset=UTF-8")
    public String coverLetter() {
        return coverLetter;
    }

    @GetMapping(value = "/cicd", produces = "text/html;charset=UTF-8")
    public String cicd() {
        return cicd;
    }
}
