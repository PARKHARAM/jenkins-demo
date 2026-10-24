# Jenkins 의 Maven 단계가 만든 target/*.jar 를 담기만 한다 (빌드는 파이프라인에서, 이미지는 Kaniko 로)
FROM eclipse-temurin:21-jre-alpine
# 베이스 이미지 OS 패키지 보안 패치 (Trivy 게이트: 수정 가능한 HIGH/CRITICAL 이 있으면 push 되지 않음)
RUN apk upgrade --no-cache
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
USER 1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
