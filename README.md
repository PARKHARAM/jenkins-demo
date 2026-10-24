# jenkins-demo

이력서 페이지를 Jenkins(GKE 파드 에이전트) → Maven → Kaniko → Artifact Registry → GKE 로 배포하는 CI/CD 예제.

| 브랜치 | 동작 | 환경 |
| --- | --- | --- |
| PR, 기타 | 빌드·테스트만 | - |
| `develop` | 빌드·테스트 → 이미지 → 자동 배포 | 개발계 `demo-dev`, https://dev.hrc0618.shop |
| `main` | 빌드·테스트 → 이미지 재사용 → **admin 승인** → 배포 | 운영계 `demo`, https://demo.hrc0618.shop |

- 앱: Spring Boot 3 / Java 21. 이력서 본문은 `src/main/resources/templates/resume.html`, `/actuator/health`
- 이미지: `asia-northeast3-docker.pkg.dev/pjt-an3-dev-vm-4/test/jenkins-demo:<커밋 SHA 7자리>`
- 배포: `k8s/app.yaml` (Deployment/Service/Ingress, 환경별 값은 Jenkinsfile 이 치환)
- 파이프라인: `Jenkinsfile` — Docker 데몬·레지스트리 비밀번호 없이 Workload Identity 로 push

운영 반영은 `develop` 을 `main` 으로 fast-forward 한다 (`git push origin develop:main`).
커밋이 같으므로 개발계에서 검증한 이미지를 다시 빌드하지 않고 그대로 운영계에 올린다.

로컬 실행: `mvn spring-boot:run` → http://localhost:8080
