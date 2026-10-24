# Jenkins 의 Maven 단계가 만든 target/*.jar 를 담기만 한다 (빌드는 파이프라인에서, 이미지는 Kaniko 로)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
USER 1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
