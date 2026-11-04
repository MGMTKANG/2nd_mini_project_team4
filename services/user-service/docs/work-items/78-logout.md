# 로그아웃 API 복원

- 이슈: https://github.com/mingukang8187/2nd_mini_project_team4/issues/78
- 브랜치: `codex/78-user-logout`
- 구현: 인증된 회원의 Refresh Token 삭제 및 204 응답.
- 자동 검증: Java 17, `bash ./gradlew clean test --no-daemon`, 23개 통과 / 실패 0 / 건너뜀 0 (신규 HTTP 테스트 6개 포함).
- 실제 사용 검증: 실제 Redis 및 배포 화면은 검증 대기.
- 다음 단계: PR에서 CI·코드 검토 후 develop 반영, 배포 후 로그아웃 버튼 및 재발급 거절 확인.
