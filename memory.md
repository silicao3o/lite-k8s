# 메모리

## 2026-03-11: Docker Monitor vs Kubernetes 비교

### 질문
현재 이 모니터링 서비스와 k8s의 차이점

### 핵심 요약

| 항목 | Docker Monitor | Kubernetes |
|------|----------------|------------|
| **역할** | 모니터링/알림 전용 | 컨테이너 오케스트레이션 |
| **대응 방식** | 수동 (알림만) | 자동 (자가 치유) |
| **복잡도** | 낮음 | 높음 |
| **확장성** | 단일 호스트 | 멀티 노드 클러스터 |

### 주요 차이점

1. **장애 처리**
   - Docker Monitor: 감지 → 이메일 알림 → 관리자 수동 조치
   - Kubernetes: 감지 → 자동 재시작/재배포

2. **Docker Monitor 강점**
   - Exit Code 상세 분석 (11종)
   - 이메일 알림 내장
   - 컨테이너 로그 자동 수집
   - 낮은 학습 곡선

3. **Kubernetes 강점**
   - 자동 재시작/스케일링
   - 로드 밸런싱, 서비스 디스커버리
   - 시크릿/설정 관리
   - 선언적 배포 (YAML)

### 결론
- Docker Monitor = "문제를 알려주는 도구"
- Kubernetes = "문제를 자동으로 해결하는 플랫폼"

### 사용 시나리오
- **Docker Monitor**: 단일 서버, 간단한 모니터링, 수동 대응 가능 환경
- **Kubernetes**: 대규모 프로덕션, 고가용성, 자동 스케일링 필요 환경

---

## 2026-03-11: 프로젝트 방향성 설정

### 핵심 목표
K8s 없이 K8s 수준의 자가치유 시스템 구축 (PRD v2.0 기반)

### 진화 경로
```
현재: 단일 호스트 모니터링 + 이메일 알림
  ↓
목표: 다중 망 통합 + AI 자가치유 (70% 자동 조치율)
```

### 개발 우선순위
1. **Phase 1**: 에이전트/서버 분리 (다중 호스트 지원)
2. **Phase 2**: AI 자가치유 엔진 + Playbook 시스템
3. **Phase 3**: Safety Gate + Slack 승인 워크플로우
4. **Phase 4**: GCP 통합 + 전체 망 커버리지

### 핵심 신규 컴포넌트
- Playbook YAML 기반 조치 규칙
- Safety Gate (위험도 평가)
- SSH/GCP CLI 실행 모듈
- Audit Logger (감사 추적)
- Claude Code 연동 (AI 판단)

### 성공 지표 (PRD 기준)
- MTTD ≤ 1분
- AI 자동 조치율 ≥ 70%
- MTTR ≤ 5분 (현재 30분)
- AI 조치 후 신규 장애 ≤ 2%

---

## 2026-03-11: P0 핵심 기능 완성

### 완료된 작업

| # | 항목 | 구현 내용 |
|---|------|----------|
| 1 | **재연결 백오프** | 지수 백오프 (5초→10초→20초...), 최대 재시도 10회 |
| 2 | **컨테이너 필터링** | 이름/이미지 패턴으로 제외/포함, 정규식 지원 |
| 3 | **알림 중복 방지** | 60초 윈도우 내 동일 이벤트 스킵 |
| 4 | **설정 검증** | 시작 시 필수값 체크, 누락 시 에러 메시지 |

### 새로 추가된 파일

```
src/main/java/.../config/
├── MonitorProperties.java      (확장)
├── ConfigurationValidator.java (신규)

src/main/java/.../service/
├── ContainerFilterService.java      (신규)
├── AlertDeduplicationService.java   (신규)
```

### 새로운 설정 옵션

```yaml
docker.monitor:
  reconnect:
    initial-delay-ms: 5000      # 초기 재연결 대기
    max-delay-ms: 300000        # 최대 대기 (5분)
    multiplier: 2.0             # 백오프 배수
    max-retries: 10             # 최대 재시도

  deduplication:
    enabled: true               # 중복 방지 활성화
    window-seconds: 60          # 중복 판정 시간 창

  filter:
    exclude-names: []           # 제외할 컨테이너 이름 패턴
    exclude-images: []          # 제외할 이미지 패턴
    include-names: []           # 포함할 컨테이너 이름 패턴
    include-images: []          # 포함할 이미지 패턴
```

### 테스트 현황
- 총 테스트: 56개
- 성공: 56개 (100%)

### 다음 단계 (P1)
- [ ] Slack 연동
- [ ] 리소스 모니터링 (CPU/Memory)
- [ ] Health 엔드포인트
- [ ] 이메일 재시도 로직
