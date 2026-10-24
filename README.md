# jenkins-demo

이력서 페이지를 Jenkins(GKE 파드 에이전트) → Maven → Kaniko → Artifact Registry → GKE 로 배포하는 CI/CD 예제.

| 브랜치 | 동작 | 환경 |
| --- | --- | --- |
| PR (→ main) | 빌드·테스트 → 이미지 빌드 + **Trivy 스캔** (push 안 함). 통과해야 머지 가능 | - |
| `develop` | 빌드·테스트 → 이미지 + Trivy → push → 자동 배포 | 개발계 `demo-dev`, https://dev.hrc0618.shop |
| `main` | 빌드·테스트 → 이미지 **재사용** → **admin 승인** → 배포 | 운영계 `demo`, https://demo.hrc0618.shop |

- 앱: Spring Boot 4 / Java 21. 이력서 본문은 `src/main/resources/templates/resume.html`, `/actuator/health`
- 이미지: `asia-northeast3-docker.pkg.dev/pjt-an3-dev-vm-4/test/jenkins-demo:<소스 트리 해시 12자리>`
- 배포: `k8s/app.yaml` (Deployment/Service/Ingress, 환경별 값은 Jenkinsfile 이 치환)
- 파이프라인: `Jenkinsfile` — Docker 데몬·레지스트리 비밀번호 없이 Workload Identity 로 push

### 운영 반영 절차
1. `develop` 에 push → 개발계 자동 배포 → https://dev.hrc0618.shop 확인
2. GitHub 에서 PR `develop → main` 생성 → Jenkins PR 빌드(테스트 + Trivy)가 통과해야 머지 가능 (`main` 브랜치 보호: 직접 push 금지)
3. 머지 → `main` 빌드가 개발계와 **같은 이미지**를 재사용 → Jenkins 에서 "운영 배포" 승인 → 운영계 반영

이미지 태그가 커밋이 아니라 소스 트리 해시라서, merge commit 이나 squash 로 머지해도 내용이 같으면 다시 빌드하지 않는다.

### 보안 게이트 (Trivy)
- Kaniko 로 이미지를 tar 로만 만든 뒤 Trivy 로 스캔, **수정 버전이 있는 HIGH/CRITICAL** 이 있으면 빌드 실패 → 레지스트리에 올라가지 않음
- 스캔 결과는 빌드 아티팩트 `trivy-report.txt`
- 위험을 수용해야 하면 저장소 루트 `.trivyignore` 에 CVE 와 사유를 기록
- 게이트 도입 시 Spring Boot 3.4.4 에서 CRITICAL 8 / HIGH 27 검출 → Spring Boot 4.1.2 로 올려 0 건

로컬 실행: `mvn spring-boot:run` → http://localhost:8080
