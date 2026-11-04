# 참가자 프로필 조회 복원

- 이슈: https://github.com/mingukang8187/2nd_mini_project_team4/issues/79
- 브랜치: `codex/79-profile-detail`
- 구현: 프로필 ID로 공개 프로필 조회, 기존 본인 프로필 조회·수정 유지.
- 자동 검증: Java 17, `bash ./gradlew clean test --no-daemon`, 25개 통과 / 실패 0 / 건너뜀 0 (신규 HTTP 테스트 8개 포함).
- 실제 사용 검증: 실제 DB 및 배포 화면은 검증 대기.
- 다음 단계: PR에서 CI·코드 검토 후 develop 반영, 배포 후 참가자 프로필 창 확인.
