# MrSohnLogcat

MrSohnLogcat은 Kotlin Multiplatform(Compose Multiplatform)으로 제작된 데스크톱용 Android Logcat 뷰어입니다. Android Studio의 내장 Logcat보다 가볍고, 강력한 필터링 및 검색 기능을 제공하여 효율적인 디버깅을 돕습니다.

## 주요 기능

*   **실시간 로그 스트리밍**: ADB를 통해 연결된 기기의 로그를 실시간으로 가져옵니다.
*   **스마트 패키지 필터링**: 특정 패키지 명을 입력하면 해당 프로세스의 로그만 집중적으로 수집합니다. 필터링되지 않은 다른 앱의 로그는 메모리에서 즉시 폐기하여 버퍼 효율을 극대화합니다.
*   **강력한 검색 및 강조**:
    *   `Ctrl + F`를 통한 메시지 검색 및 `Enter`/방향키를 이용한 결과 이동.
    *   정규식(Regex) 검색 지원.
    *   검색어 실시간 하이라이팅.
*   **유연한 필터링**:
    *   태그(Tag)별 필터링.
    *   로그 레벨(Verbose, Debug, Info, Warn, Error, Fatal)별 필터링.
    *   제외 필터 (세미콜론 `;`으로 구분하여 여러 개의 키워드나 태그 제외 가능).
*   **커스텀 뷰 설정**:
    *   타임스탬프, PID, TID, 레벨, 태그, 패키지 명의 표시 여부를 자유롭게 설정.
    *   폰트 크기 조절 기능.
    *   다크 모드 / 라이트 모드 / 시스템 테마 지원.
*   **편의 기능**:
    *   로그 일시 정지(Pause) 및 재개(Resume).
    *   로그 클리어 기능.
    *   URL 자동 하이라이팅 및 클릭 가능한 링크 (추후 지원 예정).

## 실행 방법

- **데스크톱 앱 실행**:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - 표준 실행: `./gradlew :desktopApp:run`


   - git push origin v1.0.1
## 요구 사항

- 기기에 ADB가 설치되어 있어야 하며, 앱 실행 시 ADB 경로를 설정해야 합니다.

---

This is a Kotlin Multiplatform project targeting Desktop (JVM).

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  - [commonMain](./shared/src/commonMain/kotlin) contains the main UI and logic.
  - [jvmMain](./shared/src/jvmMain/kotlin) contains JVM-specific ADB implementation.

### Running tests

- Desktop tests: `./gradlew :shared:jvmTest`
