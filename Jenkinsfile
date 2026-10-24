#!groovy
// jenkins-demo: 이력서 페이지 CI/CD (개발계 / 운영계)
//
//   브랜치     빌드·테스트  이미지 빌드 + Trivy 스캔        push   배포
//   PR         O           O (게이트만, push 안 함)        X      X
//   develop    O           O (이미 있으면 생략)             O      개발계 demo-dev → https://dev.hrc0618.shop  (자동)
//   main       O           재사용 (없으면 빌드·스캔)        O      운영계 demo     → https://demo.hrc0618.shop (승인 후)
//   그 외      O           X                               X      X
//
// 흐름: develop push → 개발계 → PR(develop → main, CI 통과 필수: GitHub 브랜치 보호) → 머지 → 승인 → 운영계
//
// 이미지 태그 = 소스 트리 해시(git rev-parse HEAD^{tree}) 12자리. 커밋이 아니라 "내용" 기준이라
// PR 을 merge commit/squash 로 머지해 커밋 SHA 가 바뀌어도 내용이 같으면 같은 태그 → 개발계에서 빌드·스캔·검증한
// 이미지를 다시 빌드하지 않고 그대로 운영계에 올린다 (build once, deploy many).
//
// 보안 게이트: Kaniko 로 이미지를 tar 로만 만든 뒤 Trivy 로 스캔하고, 수정 버전이 있는 HIGH/CRITICAL 취약점이
// 하나라도 있으면 실패 → 레지스트리에 올라가지 않는다. 위험 수용이 필요하면 저장소 루트 .trivyignore 에 CVE 와 사유를 적는다.
//
// 전제 (iac-gcp-only pjt-an3-dev-vm-4 에 반영됨)
//  - Jenkins 파드 에이전트 KSA jenkins/default ↔ GSA jenkins-sa (Workload Identity, roles/artifactregistry.writer)
//  - jenkins/default 에 demo, demo-dev 네임스페이스 배포 권한 (Role jenkins-deployer)
//  - 두 네임스페이스에 hrc0618-tls (VSO), DNS demo/dev.hrc0618.shop -> ingress-nginx
//  - 운영 배포 승인은 Jenkins 사용자 admin 만 가능 (examiner 는 읽기 전용)

def PROJECT  = 'pjt-an3-dev-vm-4'
def REGISTRY = "asia-northeast3-docker.pkg.dev/${PROJECT}/test"
def APP      = 'jenkins-demo'
def TARGETS  = [
  develop: [env: 'dev',  ns: 'demo-dev', host: 'dev.hrc0618.shop'],
  main   : [env: 'prod', ns: 'demo',     host: 'demo.hrc0618.shop'],
]

// 빌드용 파드: jnlp(cloud 템플릿 "default" 에서 상속) + 아래 컨테이너
def CI_POD = '''
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: default
  containers:
  - name: maven
    image: maven:3.9-eclipse-temurin-21
    command: ["sleep"]
    args: ["infinity"]
    resources:
      requests: { cpu: 500m, memory: 1Gi }
      limits:   { cpu: "2",  memory: 2Gi }
    volumeMounts:
    - name: m2
      mountPath: /root/.m2
  - name: kaniko
    image: gcr.io/kaniko-project/executor:v1.23.2-debug
    command: ["sleep"]
    args: ["infinity"]
    resources:
      requests: { cpu: 500m, memory: 1Gi }
      limits:   { cpu: "2",  memory: 2Gi }
  - name: trivy             # 이미지 취약점 스캔 (보안 게이트). 버전은 trivy-db-update CronJob 과 맞춤
    image: aquasec/trivy:0.58.1
    command: ["sleep"]
    args: ["infinity"]
    resources:
      requests: { cpu: 200m, memory: 512Mi }
      limits:   { cpu: "1",  memory: 1Gi }
    volumeMounts:
    - name: trivy-db
      mountPath: /trivy-cache
      readOnly: true
    - name: trivy-work        # 공유 DB 를 복사해 쓰는 작업 공간 (Trivy 는 스캔만 해도 DB 파일을 쓰기 모드로 연다)
      mountPath: /tmp/trivy-cache
  - name: gcrane            # 같은 태그 존재 확인 + 스캔 통과한 tar 를 push (WI 로 인증)
    image: gcr.io/go-containerregistry/gcrane:debug@sha256:af38137800c3f26df6a80abde9176f88739b81e3e0dc4d80ed5f38be9d3b39dd
    command: ["sleep"]
    args: ["infinity"]
  - name: kubectl
    image: alpine/k8s:1.33.4   # kubectl 포함 (bitnami/kubectl 태그는 더 이상 제공되지 않음)
    command: ["sleep"]
    args: ["infinity"]
  volumes:
  - name: m2
    emptyDir: {}
  - name: trivy-db          # 취약점 DB 공유 캐시 (jenkins/trivy-db, CronJob trivy-db-update 가 6시간마다 갱신)
    persistentVolumeClaim:
      claimName: trivy-db
      readOnly: true
  - name: trivy-work
    emptyDir: {}
'''

// 운영 배포용 파드: 승인 뒤에만 뜬다 (승인 대기 동안 빌드 파드를 붙잡지 않음)
def DEPLOY_POD = '''
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: default
  containers:
  - name: kubectl
    image: alpine/k8s:1.33.4
    command: ["sleep"]
    args: ["infinity"]
'''

// k8s/app.yaml 의 플레이스홀더를 환경별 값으로 치환해 적용하고 롤아웃 완료까지 대기
def deployScript(t) {
  return """
    sed -e 's|\${NAMESPACE}|${t.ns}|g' -e 's|\${HOST}|${t.host}|g' -e 's|\${APP_ENV}|${t.env}|g' \\
        -e 's|\${IMAGE}|${env.IMAGE}|g' -e 's|\${BUILD_INFO}|#${env.BUILD_NUMBER} ${env.GIT_SHA}|g' k8s/app.yaml \\
      | kubectl apply -f -
    kubectl -n ${t.ns} rollout status deployment/jenkins-demo --timeout=180s
    kubectl -n ${t.ns} get deploy,svc,ingress -l app=jenkins-demo
  """
}

// 외부 URL 로 헬스체크 + 이번 빌드가 실제로 서비스되는지 (페이지 하단 Build 번호) 확인
// jnlp(inbound-agent) 컨테이너에 curl 이 있음. 비루트 curl 이미지는 workspace@tmp 를 못 써서 사용 불가
def verifyScript(t) {
  return """
    for i in \$(seq 1 20); do
      if curl -fsS --max-time 10 https://${t.host}/actuator/health | grep -q UP \\
         && curl -fsS --max-time 10 https://${t.host}/ | grep -q '#${env.BUILD_NUMBER} ${env.GIT_SHA}'; then
        echo "OK: https://${t.host} (#${env.BUILD_NUMBER} ${env.GIT_SHA})"; exit 0
      fi
      sleep 5
    done
    echo "verify failed: https://${t.host}"; exit 1
  """
}

pipeline {
  agent none

  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 60, unit: 'MINUTES')   // 운영 승인 대기 포함
    skipDefaultCheckout()
  }

  stages {
    stage('CI') {
      agent {
        kubernetes {
          // jenkins-casc.yaml 의 cloud "kubernetes", 템플릿 "default" 를 상속하고 필요한 컨테이너만 추가
          inheritFrom 'default'
          defaultContainer 'maven'
          yaml CI_POD
        }
      }
      stages {
        stage('Checkout') {
          steps {
            script {
              def scmVars = checkout scm
              // checkout 은 jnlp(uid 1000), 이후 단계는 root 컨테이너라 git 을 다시 부르면 "dubious ownership"
              // -> checkout 이 돌려준 커밋 값 사용
              env.GIT_SHA = scmVars.GIT_COMMIT.take(7)
              // 워크스페이스 소유자(jnlp, uid 1000)로 git 실행
              container('jnlp') {
                env.TREE_SHA = sh(script: 'git rev-parse HEAD^{tree}', returnStdout: true).trim().take(12)
              }
              env.IMAGE   = "${REGISTRY}/${APP}:${env.TREE_SHA}"
              def t = TARGETS[env.BRANCH_NAME]
              echo "branch=${env.BRANCH_NAME} image=${env.IMAGE} target=${t ? t.env + ' (' + t.host + ')' : '없음 (빌드·테스트만)'}"
            }
          }
        }

        stage('Build & Test (Maven)') {
          steps {
            sh 'mvn -B clean package'      // 테스트 포함. 실패하면 이후 단계(이미지·배포)는 실행되지 않음
            sh 'ls -l target/*.jar'
          }
          post {
            always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml' }
          }
        }

        stage('Image (Kaniko → Trivy → Artifact Registry)') {
          when { anyOf { branch 'develop'; branch 'main'; changeRequest() } }
          steps {
            script {
              def publish = ['develop', 'main'].contains(env.BRANCH_NAME)
              def exists = false
              if (publish) {
                container('gcrane') {
                  exists = sh(script: "gcrane digest ${env.IMAGE}", returnStatus: true) == 0
                }
              }
              if (exists) {
                echo "이미지 재사용: ${env.IMAGE} (같은 소스 트리로 이미 빌드·스캔·검증된 이미지)"
                return
              }
              container('kaniko') {
                // push 하지 않고 tar 로만 만든다 (스캔 통과 후에만 레지스트리에 올리기 위해)
                sh """
                  /kaniko/executor \\
                    --context=dir://\$WORKSPACE \\
                    --dockerfile=\$WORKSPACE/Dockerfile \\
                    --destination=${env.IMAGE} \\
                    --no-push --tar-path=\$WORKSPACE/image.tar \\
                    --cache=true --cache-repo=${REGISTRY}/kaniko-cache \\
                    --snapshot-mode=redo
                """
              }
              container('trivy') {
                // DB 는 내려받지 않고 공유 캐시(읽기 전용 PVC)를 노드 로컬 emptyDir 로 복사해 쓴다.
                // (Trivy 는 DB 를 쓰기 모드로 열어 읽기 전용 마운트를 직접 못 씀. 빌드마다 복사본이라 동시 빌드도 충돌 없음)
                def TRIVY = "trivy image --input \$WORKSPACE/image.tar --cache-dir /tmp/trivy-cache --cache-backend memory " +
                            "--skip-db-update --skip-java-db-update --no-progress --severity HIGH,CRITICAL --ignore-unfixed"
                sh """
                  start=\$(date +%s); cp -a /trivy-cache/db /trivy-cache/java-db /tmp/trivy-cache/
                  echo "취약점 DB 복사 \$((\$(date +%s)-start))초: \$(cat /tmp/trivy-cache/db/metadata.json)"
                  ${TRIVY} --format table --exit-code 0 > trivy-report.txt
                  cat trivy-report.txt
                  # 게이트: 수정 버전이 있는 HIGH/CRITICAL 이 있으면 실패
                  ${TRIVY} --format table --exit-code 1 > /dev/null
                  echo "Trivy 게이트 통과: 수정 가능한 HIGH/CRITICAL 취약점 없음"
                """
              }
              if (publish) {
                container('gcrane') {
                  sh "gcrane push \$WORKSPACE/image.tar ${env.IMAGE}"
                }
              }
            }
          }
          post {
            always { archiveArtifacts artifacts: 'trivy-report.txt', allowEmptyArchive: true }
          }
        }

        stage('Deploy dev') {
          when { branch 'develop' }
          steps {
            container('kubectl') { script { sh deployScript(TARGETS.develop) } }
            container('jnlp')    { script { sh verifyScript(TARGETS.develop) } }
          }
        }
      }
    }

    stage('Approve prod') {
      when { branch 'main' }
      steps {
        timeout(time: 30, unit: 'MINUTES') {
          input message: "운영계 https://demo.hrc0618.shop 에 ${env.IMAGE} 를 배포할까요?",
                ok: '운영 배포', submitter: 'admin'
        }
      }
    }

    stage('Deploy prod') {
      when {
        beforeAgent true
        branch 'main'
      }
      agent {
        kubernetes {
          inheritFrom 'default'
          defaultContainer 'kubectl'
          yaml DEPLOY_POD
        }
      }
      steps {
        checkout scm
        script { sh deployScript(TARGETS.main) }
        container('jnlp') { script { sh verifyScript(TARGETS.main) } }
      }
    }
  }

  post {
    success {
      script {
        def t = TARGETS[env.BRANCH_NAME]
        echo t ? "배포 완료: https://${t.host}  (${env.IMAGE})" : "빌드·테스트 통과 (배포 대상 아님)"
      }
    }
    failure {
      script {
        def t = TARGETS[env.BRANCH_NAME]
        if (t) { echo "실패. 서비스는 이전 버전 유지. 수동 되돌리기: kubectl -n ${t.ns} rollout undo deployment/jenkins-demo" }
      }
    }
  }
}
