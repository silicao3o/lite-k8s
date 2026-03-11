# Docker AI 모니터링 시스템 PRD v2.0

> **Product Requirements Document**
> CP 제어 서버 기반 · AI Agent 자가치유 · 다중 망(dev/res/GCP) 통합

| 항목 | 내용 |
|------|------|
| 문서 버전 | v2.0 (v1.0 전면 개정) |
| 담당팀 | DAQUV 인프라팀 |
| 상태 | 초안 (Draft) |
| 변경 핵심 | AI 자가치유 엔진 / 다중 망 구조 반영 |
| 분류 | 사내 기밀 |

**v1.0 대비 주요 변경 사항**
- 현재 인프라 구조(CP → dev망/res망/GCP) 반영 및 아키텍처 전면 재설계
- AI Agent 자가치유 엔진 신규 섹션 추가 (섹션 6)
- 망별 에이전트 연결 전략 및 보안 정책 추가 (섹션 5)
- Claude Code 스타일 AI 조치 실행 파이프라인 상세화
- 로드맵 Phase 재편 (AI 자가치유를 Phase 2로 상향)

---

## 목차

1. [배경 및 목적](#1-배경-및-목적)
2. [목표 및 성공 지표](#2-목표-및-성공-지표)
3. [이해관계자 및 사용자](#3-이해관계자-및-사용자)
4. [시스템 아키텍처](#4-시스템-아키텍처)
5. [망별 에이전트 연결 전략](#5-망별-에이전트-연결-전략-신규)
6. [AI 자가치유 엔진](#6-ai-자가치유-엔진-신규-핵심)
7. [기능 요구사항](#7-기능-요구사항)
8. [비기능 요구사항](#8-비기능-요구사항)
9. [기술 스택](#9-기술-스택)
10. [개발 로드맵](#10-개발-로드맵)
11. [제약 사항 및 가정](#11-제약-사항-및-가정)
12. [리스크 및 완화 전략](#12-리스크-및-완화-전략)
13. [미결 사항](#13-미결-사항-open-questions)

---

## 1. 배경 및 목적

### 1.1 현재 인프라 구조

DAQUV의 인프라는 CP(Control Plane) 서버를 허브로, 개발망(dev)·연구망(res)·GCP VM 군을 스포크로 연결하는 허브-스포크 구조다. CP에서 Claude Code가 직접 bash 명령어 및 GCP CLI를 실행해 VM 생성과 각 서버를 관리하며, 각 서버 내에서는 Docker 컨테이너로 애플리케이션과 DB가 서빙되고 있다.

```
                    ┌─────────────────────────────┐
                    │     CP 서버 (Control Plane)  │
                    │  Claude Code / GCP CLI / SSH │
                    └────┬──────────┬──────────┬───┘
              SSH/VPN    │          │  GCP API  │  SSH/VPN
         ┌───────────────▼──┐  ┌───▼──────────┐  ┌▼───────────────┐
         │   dev 망 서버들   │  │  res 망 서버들 │  │  GCP VM 인스턴스 │
         │  [컨테이너 N개]   │  │ [컨테이너 N개] │  │  [컨테이너 N개]  │
         └──────────────────┘  └──────────────┘  └────────────────┘
```

### 1.2 문제 정의

| 구분 | 현재 상황 | 문제점 |
|------|-----------|--------|
| 상태 파악 | CP에서 각 서버 SSH 접속 후 `docker ps` | 서버 수 증가 시 확인 공수 기하급수적 증가 |
| 장애 감지 | 사용자 신고 또는 수동 확인 | MTTD 매우 김, 야간·주말 대응 공백 |
| 장애 조치 | 담당자가 CP 접속 → SSH → docker 명령 수동 실행 | 조치까지 평균 30분 이상 소요 |
| GCP 관리 | GCP CLI 명령 수동 실행 | VM 상태와 컨테이너 상태 간 연계 불가 |
| 망 간 시야 | dev / res / GCP 각각 별도 확인 | 전체 서비스 상태 단일 뷰 없음 |
| AI 활용 | Claude Code가 명령 실행 가능하나 트리거 없음 | 모니터링 데이터와 AI 실행 능력이 단절됨 |

### 1.3 핵심 인사이트 — AI가 조치까지 실행

> **왜 AI 자가치유인가?**
>
> CP 서버에는 이미 Claude Code가 SSH·GCP CLI·bash를 직접 실행할 수 있는 **실행 능력(Execution Capability)** 이 있다.
> 모니터링 시스템이 '무엇이 문제인지'를 감지하면, Claude Code가 '어떻게 고칠지'를 판단하고 직접 실행할 수 있다.
> 이는 K8s의 컨트롤 루프(Reconciliation Loop)와 동등한 자가치유를 **별도 오케스트레이터 없이** 구현하는 방식이다.
> 인간 개입이 필요한 경우는 AI가 정책 범위를 벗어난다고 판단할 때만으로 한정한다.

### 1.4 목적

- dev / res / GCP 전 영역 컨테이너를 단일 대시보드에서 실시간 파악
- 장애 감지 즉시 AI Agent가 표준 조치 절차(Playbook)에 따라 자동 실행
- AI가 처리 불가한 상황만 사람에게 에스컬레이션하는 **Human-in-the-Loop** 구조
- 모든 AI 조치 내역 기록으로 감사 추적(Audit Trail) 확보
- CP 서버의 기존 실행 인프라(Claude Code, GCP CLI)를 최대한 재활용

---

## 2. 목표 및 성공 지표

| Objective | Key Result | 목표값 |
|-----------|-----------|--------|
| 운영 가시성 확보 | 전 망(dev/res/GCP) 컨테이너 단일 뷰 | 커버리지 100% |
| 장애 감지 자동화 | MTTD (평균 장애 감지 시간) | ≤ 1분 |
| AI 자동 조치율 | AI가 자율 처리한 장애 비율 | ≥ 70% |
| MTTR 단축 | 평균 장애 복구 시간 | 수동 30분 → ≤ 5분 |
| 운영 공수 절감 | 수동 docker 확인 작업 감소율 | ≥ 80% |
| AI 조치 안전성 | AI 조치 후 신규 장애 유발 비율 | ≤ 2% |

### 2.1 비기능 목표

- 에이전트 리소스: CPU ≤ 2%, Memory ≤ 128 MB
- 대시보드 로딩: ≤ 3초
- 알림 지연: 감지 후 ≤ 30초
- AI 조치 실행 착수: 알림 후 ≤ 60초
- 시스템 가용성: 모니터링 서버 자체 99% 이상
- 데이터 보존: 메트릭 30일, AI 조치 로그 180일 (감사 목적)

---

## 3. 이해관계자 및 사용자

| 역할 | 이해관계자 | 주요 관심사 |
|------|-----------|-------------|
| Primary User | 인프라 담당자 | 전체 망 상태 파악, AI 조치 내역 확인 및 승인 |
| Primary User | 개발자 (각 서비스 담당) | 자기 서비스 컨테이너 상태, 장애 알림 수신 |
| AI Actor | Claude Code (AI Agent) | 장애 감지 신호 수신 → Playbook 실행 → 결과 보고 |
| Secondary | 팀 리드 / 매니저 | 서비스 안정성 현황, SLA, AI 조치 감사 리포트 |
| Owner | DAQUV 인프라팀 | 시스템 구축·유지보수, AI 정책 설정 |

---

## 4. 시스템 아키텍처

### 4.1 전체 구조

기존 CP 중심 허브-스포크 구조를 그대로 활용한다. CP 서버가 모니터링 중앙 서버 + AI 자가치유 엔진을 동시에 담당하며, 각 망의 서버에는 경량 에이전트만 추가 설치된다.

```
┌──────────────────────────────────────────────────────────────┐
│                     CP 서버 (Control Plane)                   │
│  ┌────────────────┐   ┌──────────────────────────────────┐   │
│  │  모니터링 서버   │   │       AI 자가치유 엔진             │   │
│  │  Spring Boot   │──▶│  Anomaly Detector                │   │
│  │  PostgreSQL    │   │  Playbook Executor (Claude Code) │   │
│  │  Redis         │   │  Safety Gate (승인 / 차단)         │   │
│  └───────┬────────┘   │  Audit Logger                    │   │
│          │            └──────────────┬───────────────────┘   │
│          │ 메트릭 수집                │ SSH / GCP CLI 실행      │
└──────────┼────────────────────────────┼──────────────────────┘
           │ (에이전트 Push)             │ (자동 조치 명령)
  ┌────────▼──────┐   ┌────────▼──────┐   ┌──────────────────┐
  │  dev 망 서버   │   │  res 망 서버   │   │   GCP VM 인스턴스  │
  │   [Agent]     │   │   [Agent]     │   │   [Agent]        │
  └───────────────┘   └───────────────┘   └──────────────────┘
```

### 4.2 컴포넌트 상세

| 컴포넌트 | 위치 | 기술 스택 | 역할 |
|----------|------|-----------|------|
| 모니터링 에이전트 | 각 서버 (dev/res/GCP) | Java Spring Boot (경량 JAR) | Docker Socket 연결, 메트릭 수집 → CP로 Push |
| 중앙 모니터링 서버 | CP 서버 | Spring Boot + PostgreSQL + Redis | 메트릭 집계, 이상 감지, REST/WS API |
| AI 자가치유 엔진 | CP 서버 | Claude Code + Playbook YAML | 장애 신호 수신 → Playbook 선택 → 명령 실행 |
| Safety Gate | CP 서버 | Spring Boot (정책 엔진) | AI 조치 전 위험도 평가, 고위험 조치 인간 승인 요청 |
| Audit Logger | CP 서버 | PostgreSQL (별도 스키마) | 모든 AI 조치 의도·실행 명령·결과 기록 |
| 웹 대시보드 | CP 서버 (서빙) | React + WebSocket | 전체 망 통합 현황, AI 조치 내역 및 승인 UI |
| 알림 채널 | 외부 SaaS | Slack Webhook / SMTP | 장애 감지 및 AI 조치 결과 알림 |

### 4.3 데이터 흐름

- **에이전트 → 중앙 서버**: 15초 주기 메트릭 Push (HTTPS)
- **중앙 서버 → 이상 감지기**: 임계치 위반 이벤트 내부 발행 (Spring ApplicationEvent)
- **이상 감지기 → AI 엔진**: 이상 유형 + 컨텍스트 전달 (내부 큐)
- **AI 엔진 → Safety Gate**: 조치 계획 제출 → 승인/차단 응답 수신
- **AI 엔진 → 대상 서버**: SSH 또는 GCP CLI 명령 실행 (CP의 기존 실행 채널)
- **AI 엔진 → Audit Logger**: 조치 전 계획, 실행 명령, 결과 전체 기록
- **AI 엔진 → Slack**: 조치 시작·완료·실패 알림

---

## 5. 망별 에이전트 연결 전략 [신규]

| 망 구분 | 연결 방식 | 에이전트 설치 방법 | 고려사항 |
|---------|-----------|-------------------|----------|
| dev 망 | CP → SSH 터널 (VPN/내부망) | CP에서 SSH로 JAR 배포 + systemd 등록 | 내부망이므로 상대적으로 낮은 보안 레벨 허용 |
| res 망 | CP → SSH 터널 (격리망) | 동일하나 포트 제한 정책 확인 필요 | 연구 데이터 격리 요구로 별도 인증서 관리 |
| GCP VM | CP → GCP IAP(Identity-Aware Proxy) SSH | GCP CLI로 startup-script 또는 원격 SSH 배포 | GCP SA(Service Account) 키 관리 필수 |

### 5.1 에이전트 보안 원칙

- 에이전트 → CP 통신: TLS 1.2 이상, 사전 발급 토큰 인증
- 에이전트는 Read 전용: Docker Socket 마운트 시 읽기 전용, 재시작 명령은 CP에서 SSH로 직접 실행
- GCP VM 에이전트: Workload Identity 또는 SA 키로 인증, IAP 터널 경유
- 에이전트 토큰 90일 자동 갱신, 만료 7일 전 알림
- 에이전트 heartbeat 60초 미수신 시 해당 서버를 `unreachable` 처리 및 즉시 알림

### 5.2 AI 조치 실행 채널

> **핵심 원칙: 에이전트는 Read-Only, 실행은 CP가 직접**
>
> 컨테이너 재시작·중지·설정 변경 등의 실행 명령은 에이전트를 통하지 않는다.
> CP의 AI 엔진이 SSH(dev/res) 또는 GCP IAP CLI(GCP)를 통해 대상 서버에 직접 명령을 실행한다.
> 이 설계로 에이전트 탈취 시에도 임의 명령 실행이 불가능하며, 모든 실행 경로가 CP를 통과한다.

| 조치 유형 | 실행 채널 | 예시 명령 |
|-----------|-----------|-----------|
| 컨테이너 재시작 | SSH → docker restart | `ssh user@dev-server-01 docker restart quvi-api` |
| 컨테이너 중지/기동 | SSH → docker stop/start | `ssh user@res-server docker stop quvi-db` |
| GCP VM 재시작 | GCP CLI (gcloud) | `gcloud compute instances reset {vm} --zone={zone}` |
| GCP VM 기동/중지 | GCP CLI | `gcloud compute instances start/stop {vm}` |
| 로그 수집 (긴급) | SSH → docker logs | `ssh user@server docker logs --tail 100 {name}` |
| 디스크 정리 | SSH → shell script | `ssh user@server 'docker system prune -f'` |

---

## 6. AI 자가치유 엔진 [신규 핵심]

### 6.1 개념 및 철학

AI 자가치유 엔진은 K8s의 컨트롤 루프를 AI로 구현한 것이다. 모니터링 시스템이 '현재 상태(Actual State)'를 감지하면, AI 엔진이 '원하는 상태(Desired State)'로 수렴하는 조치를 자율적으로 판단하고 실행한다.

**K8s 자가치유 기능과의 대응 관계**

| K8s 기능 | 이 시스템 대응 |
|----------|---------------|
| Liveness Probe | 컨테이너 헬스체크 + AI 재시작 판단 |
| Readiness Probe | 서비스 응답 확인 후 트래픽 차단/복구 조치 |
| ReplicaSet 복구 | AI가 중단된 컨테이너 재기동 |
| Resource 제한 초과 | AI가 원인 로그 분석 후 재시작 또는 스케일 조치 제안 |
| Node 불능 | GCP VM 재시작 또는 마이그레이션 실행 |

### 6.2 AI 조치 실행 파이프라인

```
[1. 감지]        [2. 분석]         [3. 계획]          [4. 검증]
이상 이벤트 ──▶ 컨텍스트 수집 ──▶ Playbook 선택 ──▶ Safety Gate
(모니터링 서버)  (로그, 메트릭)    (AI 판단)                │
                                              ┌──────────┴──────────┐
                                           승인                   차단
                                              │              인간 에스컬레이션
                                        [5. 실행]          (Slack 승인 요청)
                                        SSH / GCP CLI
                                              │
                                    [6. 결과 기록 + 알림]
                                     Audit Logger / Slack
```

### 6.3 Playbook 시스템

Playbook은 '어떤 이상 상황에서 어떤 조치를 어떤 순서로 실행할지'를 YAML로 정의한 규칙 파일이다. AI는 Playbook을 기반으로 조치하되, 상황에 따라 동적으로 판단한다.

| Playbook | 트리거 조건 | AI 실행 단계 | 에스컬레이션 조건 |
|----------|-------------|-------------|-----------------|
| `container-restart` | 컨테이너 exited/stopped | 1.로그 수집 2.재시작 3.헬스체크 4.결과 보고 | 재시작 3회 실패 시 |
| `oom-recovery` | 메모리 OOM으로 종료 | 1.메모리 사용 로그 분석 2.재시작 3.메모리 임계치 알림 | 동일 컨테이너 반복 OOM |
| `cpu-throttle` | CPU 90% 이상 5분 지속 | 1.프로세스 상위 분석 2.재시작 여부 AI 판단 3.조치 | 재시작 후에도 CPU 지속 |
| `disk-full` | 디스크 사용률 90% 초과 | 1.docker system prune 2.로그 정리 3.용량 재확인 | 정리 후에도 90% 이상 |
| `gcp-vm-down` | GCP VM 응답 없음 | 1.GCP 상태 API 확인 2.VM 재시작 명령 3.에이전트 재연결 대기 | VM 재시작 실패 시 |
| `agent-lost` | 에이전트 heartbeat 끊김 | 1.SSH 핑 테스트 2.서버 재부팅 여부 판단 3.알림 | SSH도 불통 시 즉시 |
| `cascade-failure` | 서비스 그룹 다수 동시 장애 | 즉시 인간 에스컬레이션 (자동 조치 보류) | 항상 |

### 6.4 Safety Gate — AI 조치 안전 정책

> **Safety Gate 설계 원칙**
> - 모든 AI 조치는 Safety Gate를 반드시 통과해야 하며, **우회 경로는 없다.**
> - 위험도(Risk Level)는 조치 유형, 영향 범위, 시간대, 서비스 중요도를 종합해 자동 산정한다.
> - 고위험 조치는 Slack으로 담당자에게 승인 요청을 보내고, 5분 내 응답 없으면 보수적 조치(재시작만)로 다운그레이드한다.

| 위험도 | 예시 조치 | Safety Gate 처리 | 응답 요구 |
|--------|-----------|-----------------|-----------|
| Low | 단일 컨테이너 재시작 (비 프로덕션) | 자동 승인 → 즉시 실행 | 불필요 |
| Medium | 프로덕션 컨테이너 재시작 | 자동 승인 + Slack 사후 알림 | 불필요 |
| High | GCP VM 재시작, 다수 컨테이너 일괄 조치 | Slack 사전 승인 요청 | 5분 내 승인 필요 |
| Critical | 데이터 관련 조치, 네트워크 설정 변경 | 자동 실행 금지, 즉시 에스컬레이션 | 수동 조치만 허용 |

### 6.5 Human-in-the-Loop 인터페이스

- **Slack 승인 요청**: 조치 계획 요약 + `[승인]` / `[거부]` / `[보류]` 버튼
- **웹 대시보드**: AI 조치 대기 목록, 실시간 실행 로그 스트리밍
- **긴급 차단**: 대시보드에서 'AI 자동 조치 전체 중단' 원클릭 스위치
- **조치 이력**: 모든 AI 조치의 의도-실행-결과를 타임라인으로 조회
- **재실행 / 롤백 버튼**: 실패한 AI 조치를 수동으로 재시도하거나 이전 상태로 복구

### 6.6 AI 조치 감사 로그 스키마

```sql
CREATE TABLE ai_audit_log (
  id                UUID        PRIMARY KEY,
  triggered_at      TIMESTAMP   NOT NULL,              -- 이상 감지 시각
  anomaly_type      VARCHAR     NOT NULL,              -- container-down, cpu-high 등
  target_server     VARCHAR     NOT NULL,              -- dev::server-01 형식
  target_container  VARCHAR,                           -- 컨테이너 명
  playbook_id       VARCHAR     NOT NULL,              -- 선택된 Playbook
  ai_reasoning      TEXT,                              -- AI 판단 이유 (자연어)
  commands_executed JSONB,                             -- 실행 명령어 배열
  safety_level      VARCHAR     NOT NULL,              -- Low/Medium/High/Critical
  approved_by       VARCHAR,                           -- 자동 승인 또는 사용자 ID
  result            VARCHAR     NOT NULL,              -- SUCCESS/FAILED/ESCALATED/SKIPPED
  result_detail     TEXT,                              -- stdout/stderr 요약
  resolved_at       TIMESTAMP                          -- 문제 해결 확인 시각
);
-- Append-Only: UPDATE/DELETE 권한 부여 금지
```

---

## 7. 기능 요구사항

### 7.1 컨테이너 모니터링

> **P0 — MVP 필수. 이 기능 없이는 출시 불가.**

- 컨테이너 상태: `running` / `stopped` / `paused` / `exited` / `restarting`
- CPU(%), 메모리(사용/한도 MB%), 네트워크 I/O, 블록 I/O
- 컨테이너 시작 시각, 재시작 횟수, 업타임
- 수집 주기: 기본 15초 (5~60초 설정 가능)
- 망별 그룹핑: dev / res / GCP 탭 분리 + 전체 통합 뷰
- WebSocket 실시간 갱신 대시보드

### 7.2 알림 시스템

> **P0 — MVP 필수.**

| 알림 유형 | 트리거 | 기본값 | 채널 |
|-----------|--------|--------|------|
| 컨테이너 다운 | running → exited/stopped | 즉시 | Slack + Email |
| CPU 과부하 | CPU ≥ 임계치 5분 지속 | 80% | Slack |
| 메모리 부족 | Mem ≥ 임계치 5분 지속 | 85% | Slack |
| 재시작 반복 | 5분 내 재시작 3회 이상 | 3회/5분 | Slack + Email |
| 에이전트 연결 끊김 | heartbeat 미수신 | 60초 | Slack + Email |
| AI 조치 시작 | Playbook 실행 착수 | 즉시 | Slack |
| AI 조치 완료/실패 | 실행 결과 확정 | 즉시 | Slack |
| 승인 요청 (High) | Safety Gate High 판정 | 즉시 | Slack (버튼 포함) |
| GCP VM 상태 변경 | VM running/stopped/error | 즉시 | Slack |

### 7.3 AI 자가치유 엔진

> **P0 — 이 시스템의 핵심 차별점. MVP에 포함.**

- Playbook YAML 파일 기반 조치 규칙 정의 및 런타임 로드
- 이상 이벤트 수신 시 적합한 Playbook 자동 선택
- Safety Gate 통과 후 SSH / GCP CLI 명령 실행
- 실행 결과 헬스체크로 조치 성공 여부 자동 검증
- Human-in-the-Loop Slack 승인 인터페이스
- 전체 AI 조치 일시 정지 스위치 (대시보드 + `/pause-ai` Slack 명령)
- Playbook 미등록 이상 유형은 AI 자유 판단 모드로 조치 (설정으로 활성화 가능)

### 7.4 로그 수집 및 조회

> **P1 — 장애 분석 및 AI 판단 근거 확보에 필수.**

- 컨테이너 stdout/stderr 실시간 스트리밍 조회
- 키워드 검색, 시간 범위 필터
- AI 자가치유 엔진이 로그를 컨텍스트로 활용 (분석 파이프라인 연동)
- 로그 보존: 7일 (최대 30일)

### 7.5 GCP VM 통합 관리

> **P1 — GCP 환경 가시성 및 VM 레벨 조치에 필요.**

- GCP VM 인스턴스 목록 및 상태 조회 (`gcloud compute instances list`)
- VM 레벨 CPU / 메모리 / 디스크 사용률 (GCP Monitoring API 또는 에이전트)
- VM 시작/중지/재시작 버튼 (수동 및 AI 자동 조치 공용)
- VM ↔ 컨테이너 연관 뷰: VM 클릭 시 해당 VM의 컨테이너 목록 표시

### 7.6 서비스 그룹 관리

> **P2 — 운영 편의성 기능.**

- 관련 컨테이너를 서비스 단위로 그룹핑 (예: `quvi` = api + db + redis)
- 서비스 전체 상태 요약 (`healthy` / `degraded` / `down`)
- 서비스 단위 Playbook 적용 (연쇄 장애 시 서비스 전체 복구 순서 정의)

### 7.7 메트릭 히스토리 및 리포트

> **P2 — 운영 분석 및 SLA 보고에 활용.**

- 일간/주간 리소스 사용 추이 차트
- AI 조치 통계: 조치 횟수, 성공률, 에스컬레이션 비율, MTTR
- 장애 이벤트 타임라인 요약
- CSV 내보내기

---

## 8. 비기능 요구사항

| 구분 | 요구사항 | 측정 방법 |
|------|----------|-----------|
| 성능 | 에이전트 CPU ≤ 2%, Memory ≤ 128MB | 24시간 평균 |
| 성능 | 대시보드 초기 로딩 ≤ 3초 | Chrome DevTools |
| 성능 | AI 조치 착수까지 ≤ 60초 (감지 기준) | 감지 시각 ~ 첫 명령 실행 시각 |
| 가용성 | 모니터링 서버 자체 99% 이상 | 월간 다운타임 ≤ 432분 |
| 보안 | 대시보드 JWT 인증 | 비인증 접근 차단 |
| 보안 | 에이전트 ↔ CP TLS 1.2 이상 | 인증서 적용 확인 |
| 보안 | AI 조치 명령: CP에서만 실행, 에이전트 실행 불가 | 아키텍처 설계 검증 |
| 감사 | AI 조치 로그 180일 보존, Append-Only | DB 권한 정책 |
| 확장성 | 에이전트 20개 이상 동시 지원 | k6 부하 테스트 |
| 운영성 | 서버: `docker compose up` 1개 명령 기동 | 실행 확인 |
| 운영성 | 에이전트: `java -jar` 단일 JAR 배포 | 배포 확인 |

---

## 9. 기술 스택

| 레이어 | 기술 | 선택 이유 |
|--------|------|-----------|
| 에이전트 | Java 17 + Spring Boot 3.x | 팀 주력 스택, 기존 코드 재활용 |
| Docker 연동 (에이전트) | docker-java SDK | Docker Remote API 클라이언트, 활성 유지보수 |
| 중앙 서버 | Spring Boot 3.x + JPA + Spring Security | 팀 주력, JWT 인증 통합 |
| AI 자가치유 엔진 | Claude Code (claude-code CLI) + Playbook YAML | CP에 기 설치된 실행 환경 그대로 활용 |
| SSH 실행 | JSch 또는 Apache MINA SSHD (Java) | Java에서 SSH 명령 실행, dev/res 망 조치 |
| GCP 실행 | Google Cloud Java SDK / gcloud CLI subprocess | GCP VM 조치, IAP 터널 지원 |
| DB (장기) | PostgreSQL | 기존 인프라, 감사 로그 Append-Only 정책 |
| 캐시/실시간 | Redis | 에이전트 최신 상태 빠른 조회 |
| 대시보드 | React + TailwindCSS + Recharts | 빠른 UI 개발, 차트 풍부 |
| 실시간 통신 | WebSocket (Spring) | AI 조치 로그 실시간 스트리밍 |
| 알림 | Slack Incoming Webhook + Block Kit | 승인 버튼 인터페이스 구현 |
| 배포 | Docker Compose (CP 서버) | 단일 명령 기동 |

---

## 10. 개발 로드맵

| Phase | 기간 | 목표 | 주요 딜리버블 |
|-------|------|------|--------------|
| Phase 1 (Foundation) | Week 1~3 | 기본 모니터링 + 알림 | 에이전트 수집, 중앙 서버, 기본 대시보드, Slack 알림, JWT 인증 |
| Phase 2 (AI 자가치유) | Week 4~6 | AI 자가치유 엔진 MVP | Playbook 시스템, Safety Gate, SSH/GCP CLI 실행, Audit Logger, Slack 승인 |
| Phase 3 (GCP 통합) | Week 7~8 | GCP 완전 통합 | GCP VM 뷰, VM 조치, IAP 터널 연동, 망별 에이전트 관리 |
| Phase 4 (로그/분석) | Week 9~10 | 운영 심화 | 로그 스트리밍, AI 조치 통계, 메트릭 히스토리, SLA 리포트 |
| Phase 5 (고도화) | Week 11+ | 지능형 운영 | AI 자유 판단 모드, 서비스 그룹 Playbook, 에스컬레이션 정책 고도화 |

### 10.1 Phase 2 (AI 자가치유) 상세 태스크

- Playbook YAML 스키마 정의 및 파서 구현
- 이상 이벤트 → Playbook 매핑 로직
- Safety Gate 위험도 산정 알고리즘
- Slack Block Kit 승인 요청 메시지 구현
- SSH 실행 모듈 (dev/res 망, JSch 기반)
- GCP CLI subprocess 실행 모듈
- Audit Logger (Append-Only PostgreSQL 스키마)
- AI 조치 일시 정지 스위치 (대시보드 + Slack `/pause-ai`)
- 기본 Playbook 작성: `container-restart`, `oom-recovery`, `cpu-throttle`, `disk-full`, `gcp-vm-down`

---

## 11. 제약 사항 및 가정

### 11.1 제약 사항

- AI 자가치유 엔진은 CP 서버에만 위치 — 에이전트 서버에서 임의 실행 불가
- Critical 위험도 조치는 자동 실행 금지, 항상 수동 조치
- K8s / ECS 환경의 컨테이너는 이 시스템 범위 외
- HA 구성은 Phase 5 이후 별도 검토
- GCP IAP 터널 사용 시 gcloud CLI가 CP 서버에 설치·인증되어 있어야 함

### 11.2 가정

- CP 서버에서 dev/res 망 서버로 SSH 접근이 가능하고 키 인증이 사전 설정됨
- CP 서버에 GCP CLI 및 Service Account 키가 설치·설정됨 (현재 Claude Code 환경 기준)
- 팀이 Slack을 주 커뮤니케이션 도구로 사용 중
- 기존 PostgreSQL 인스턴스 재활용 가능
- AI 자가치유 엔진의 Claude Code는 CP에서 서브프로세스 또는 API 방식으로 호출

---

## 12. 리스크 및 완화 전략

| 리스크 | 발생 가능성 | 영향도 | 완화 전략 |
|--------|------------|--------|-----------|
| AI 오판단으로 정상 서비스 중단 | 중 | 매우 높음 | Safety Gate 필수 통과, Dry-run 모드로 충분한 검증 후 실 운용 |
| AI 조치 루프 (재시작 반복 유발) | 중 | 높음 | 최대 재시도 횟수 정책 + Circuit Breaker 패턴으로 자동 중단 |
| SSH 키 / GCP SA 키 탈취 | 낮 | 매우 높음 | CP 서버 접근 제어 강화, 키 90일 순환, 최소 권한 원칙 |
| 모니터링 서버 자체 장애 | 중 | 높음 | Watchdog + 외부 uptime 체크, 모니터링 서버 장애 시 Slack 알림 |
| 에이전트 과부하로 운영 서버 영향 | 낮 | 높음 | 에이전트 리소스 제한, k6 부하 테스트 선행 |
| 망 간 네트워크 지연으로 메트릭 지연 | 중 | 중간 | 에이전트별 독립 버퍼, 타임스탬프 기반 메트릭 정합성 |
| AI 자동 조치에 대한 팀 불신 | 중 | 중간 | 충분한 Dry-run 기간 운용 + 상세 Audit Trail 공유 |

---

## 13. 미결 사항 (Open Questions)

| # | 질문 | 결정 시점 | 담당 |
|---|------|-----------|------|
| OQ-1 | AI 자가치유 엔진: Claude Code CLI 호출 vs Anthropic API 직접 호출 방식 결정 | Phase 2 착수 전 | 인프라팀 |
| OQ-2 | Playbook 미등록 이상에 대한 AI 자유 판단 모드 활성화 범위 결정 | Phase 2 완료 후 | 팀 리드 |
| OQ-3 | res 망의 격리 정책상 에이전트 Push 허용 여부 확인 (방화벽 규칙) | Phase 1 착수 전 | 인프라팀 |
| OQ-4 | GCP IAP vs VPN 터널 방식 최종 결정 (현재 사용 방식 확인) | Phase 3 착수 전 | 인프라팀 |
| OQ-5 | Audit Log의 법적/감사 목적 보존 기간 결정 (현재 180일 기준) | Phase 2 완료 후 | 팀 리드 |
| OQ-6 | AI 자가치유 시스템 자체를 외부 공개 솔루션으로 전환할 계획 여부 | Phase 5 이후 | 경영진 |

---

*— 문서 끝  |  Docker AI 모니터링 시스템 PRD v2.0 —*
