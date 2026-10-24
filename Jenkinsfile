#!groovy
// jenkins-demo: Maven 빌드 -> Kaniko 로 Artifact Registry push -> GKE demo 네임스페이스 배포
//
// 전제 (iac-gcp-only pjt-an3-dev-vm-4 에 반영됨)
//  - Jenkins 파드 에이전트 KSA jenkins/default ↔ GSA jenkins-sa (Workload Identity, roles/artifactregistry.writer)
//  - jenkins/default 에 demo 네임스페이스 Deployment/Service/Ingress 권한 (Role jenkins-deployer)
//  - demo 네임스페이스에 hrc0618-tls (VSO), DNS demo.hrc0618.shop -> ingress-nginx
// Docker 소켓/credentials('dockerID') 는 필요 없음: Kaniko + WI 가 대신한다.

def PROJECT  = 'pjt-an3-dev-vm-4'
def REGISTRY = "asia-northeast3-docker.pkg.dev/${PROJECT}/test"
def APP      = 'jenkins-demo'
def NS       = 'demo'
def URL      = 'https://demo.hrc0618.shop'

pipeline {
  agent {
    kubernetes {
      // jenkins-casc.yaml 의 cloud "kubernetes", 템플릿 "default"(label jenkins-jenkins-agent) 를 상속하고
      // 이 잡에 필요한 컨테이너만 추가한다
      inheritFrom 'default'
      defaultContainer 'maven'
      yaml """
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
  - name: kubectl
    image: alpine/k8s:1.33.4   # kubectl 포함 (bitnami/kubectl 태그는 더 이상 제공되지 않음)
    command: ["sleep"]
    args: ["infinity"]
  volumes:
  - name: m2
    emptyDir: {}
"""
    }
  }

  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 30, unit: 'MINUTES')
  }

  environment {
    IMAGE_REPO = "${REGISTRY}/${APP}"
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script {
          // checkout 은 jnlp(uid 1000), 이 단계는 maven 컨테이너(root) 라 git 을 직접 부르면 "dubious ownership" 에 걸림
          // -> Jenkins 가 checkout scm 후 넣어 주는 GIT_COMMIT 사용
          env.GIT_SHA   = env.GIT_COMMIT.take(7)
          env.IMAGE_TAG = "${env.GIT_SHA}-${env.BUILD_NUMBER}"
          env.IMAGE     = "${env.IMAGE_REPO}:${env.IMAGE_TAG}"
          echo "image=${env.IMAGE}"
        }
      }
    }

    stage('Build & Test (Maven)') {
      steps {
        sh 'mvn -B -q -version'
        sh 'mvn -B clean package'      // 테스트 포함. 건너뛰려면 -DskipTests
        sh 'ls -l target/*.jar'
      }
      post {
        always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml' }
      }
    }

    stage('Image (Kaniko -> Artifact Registry)') {
      steps {
        container('kaniko') {
          // WI 로 인증: Kaniko 가 GKE 메타데이터 서버에서 jenkins-sa 토큰을 받아 *.pkg.dev 에 push
          sh """
            /kaniko/executor \\
              --context=dir://\$WORKSPACE \\
              --dockerfile=\$WORKSPACE/Dockerfile \\
              --destination=${env.IMAGE} \\
              --destination=${env.IMAGE_REPO}:latest \\
              --cache=true --cache-repo=${REGISTRY}/kaniko-cache \\
              --snapshot-mode=redo
          """
        }
      }
    }

    stage('Deploy (GKE demo)') {
      steps {
        container('kubectl') {
          sh """
            sed -e 's|\${IMAGE}|${env.IMAGE}|g' -e 's|\${BUILD_INFO}|#${env.BUILD_NUMBER} ${env.GIT_SHA}|g' k8s/app.yaml \
              | kubectl apply -n ${NS} -f -
            kubectl -n ${NS} rollout status deployment/${APP} --timeout=180s
            kubectl -n ${NS} get deploy,svc,ingress -l app=${APP}
          """
        }
      }
    }

    stage('Verify') {
      steps {
        // jnlp(inbound-agent) 컨테이너에 curl 이 있음. 비루트 curl 이미지는 workspace@tmp 를 못 써서 사용 불가
        container('jnlp') {
        sh """
          for i in \$(seq 1 20); do
            if curl -fsS --max-time 10 ${URL}/actuator/health | grep -q UP; then echo "OK: ${URL}"; exit 0; fi
            sleep 5
          done
          echo "health check failed"; exit 1
        """
        }
      }
    }
  }

  post {
    success { echo "배포 완료: ${URL}  (${env.IMAGE})" }
    failure { echo "실패. 이전 버전으로: kubectl -n ${NS} rollout undo deployment/${APP}" }
  }
}
