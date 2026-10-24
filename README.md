# jenkins-demo

Jenkins(GKE 파드 에이전트) → Maven → Kaniko → Artifact Registry → GKE `demo` 네임스페이스 로 배포되는 테스트 홈페이지.

- 앱: Spring Boot 3 / Java 21, `/` 에 빌드 정보 표시, `/actuator/health`
- 이미지: `asia-northeast3-docker.pkg.dev/pjt-an3-dev-vm-4/test/jenkins-demo:<sha>-<build>`
- 배포: `k8s/app.yaml` (Deployment/Service/Ingress), https://demo.hrc0618.shop
- 파이프라인: `Jenkinsfile` — Docker 데몬·레지스트리 비밀번호 없이 Workload Identity 로 push

로컬 실행: `mvn spring-boot:run` → http://localhost:8080
