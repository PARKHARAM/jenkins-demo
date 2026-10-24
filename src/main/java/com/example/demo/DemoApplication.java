package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
    public String home() {
        String build = System.getenv().getOrDefault("BUILD_INFO", "local");
        return """
            <!doctype html><html lang="ko"><head><meta charset="utf-8"><title>jenkins-demo</title>
            <style>body{font-family:sans-serif;margin:3rem;color:#223}code{background:#eef;padding:.2em .4em}</style></head>
            <body><h1>jenkins-demo</h1>
            <p>Jenkins → Kaniko → Artifact Registry → GKE(<code>demo</code>) 파이프라인으로 배포된 테스트 홈페이지입니다.</p>
            <p>Build: <code>%s</code></p>
            <p><a href="/actuator/health">health</a></p></body></html>
            """.formatted(build);
    }
}
