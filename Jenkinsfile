#!groovy
// jenkins-demo: 이력서 페이지 CI/CD (개발계 / 운영계)
//
//   브랜치     빌드·테스트  이미지                 배포
//   PR, 기타   O           X                      X
//   develop    O           빌드 (이미 있으면 생략)  개발계 demo-dev → https://dev.hrc0618.shop  (자동)
//   main       O           재사용 (없으면 빌드)     운영계 demo     → https://demo.hrc0618.shop (승인 후)
//
// 이미지 태그 = 커밋 SHA 7자리. develop 을 main 으로 fast-forward 하면 같은 커밋이므로
// 개발계에서 검증한 이미지를 다시 빌드하지 않고 그대로 운영계에 올린다 (build once, deploy many).
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
  - name: gcrane            # 레지스트리에 같은 태그가 이미 있는지 확인 (WI 로 인증)
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
              env.IMAGE   = "${REGISTRY}/${APP}:${env.GIT_SHA}"
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

        stage('Image (Kaniko -> Artifact Registry)') {
          when { anyOf { branch 'develop'; branch 'main' } }
          steps {
            script {
              def exists = false
              container('gcrane') {
                exists = sh(script: "gcrane digest ${env.IMAGE}", returnStatus: true) == 0
              }
              if (exists) {
                echo "이미지 재사용: ${env.IMAGE} (같은 커밋을 이미 빌드했음 — 개발계에서 검증된 이미지)"
              } else {
                container('kaniko') {
                  // WI 로 인증: Kaniko 가 GKE 메타데이터 서버에서 jenkins-sa 토큰을 받아 *.pkg.dev 에 push
                  sh """
                    /kaniko/executor \\
                      --context=dir://\$WORKSPACE \\
                      --dockerfile=\$WORKSPACE/Dockerfile \\
                      --destination=${env.IMAGE} \\
                      --cache=true --cache-repo=${REGISTRY}/kaniko-cache \\
                      --snapshot-mode=redo
                  """
                }
              }
            }
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
