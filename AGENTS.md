# AGENTS.md

## 프로젝트 개요

포도알 채우기(Grape) 모바일 앱의 백엔드 API 서버다. 지금 React(Expo) 클라이언트는 로컬 상태(React Context, 새로고침 시 초기화)로만 동작하는데, 이를 계정 로그인 + 개인 데이터 서버 동기화로 옮기는 1단계 작업이다. 클라이언트의 store 액션·타입 정의를 서버 API/DB 스키마로 **그대로 이식**하는 것이 기본 원칙이며, 클라이언트는 별도 리포에 있다.

### 무엇이 이미 확정됐나 (상세는 `server-design-draft.md`, 여기 다시 옮기지 말 것 — 중복)

- 전체 REST 엔드포인트 목록과 각 요청/응답 바디 shape → `server-design-draft.md` §2, §3
- 전체 DB 스키마 (`users`, `refresh_tokens`, `bunches`, `bunch_fill_events`, `harvests`, `user_settings`) → §6
- 인증 정책: Access(JWT, 1h) + Refresh(opaque 랜덤, 30d, DB에 해시 저장, 매 refresh마다 로테이션) → §3-1
- 회원탈퇴: 즉시 하드 삭제 + FK CASCADE, 소프트 삭제·유예기간 없음 → §3-2
- 게스트 → 소셜 계정 병합 플로우 (`/auth/google`·`/auth/kakao`가 optional-auth) → §3-1
- OAuth 토큰 검증은 kkori-api와 동일한 방식 — Google `idToken`을 `tokeninfo`로 검증하되 `aud`를 web·iOS·Android 클라이언트 ID 전부와 대조하고 `email_verified`를 확인, Kakao는 `code`→access token 교환 후 `/v2/user/me` 호출 → 아래 "OAuth 토큰 검증"
- 응답 JSON 필드는 클라이언트 `Bunch`/`Harvest`/`NotificationSettings` 타입과 camelCase로 1:1 일치
- 2단계 "함께 보기"/공유 기능은 이번 범위 밖

## 기술 스택

- 프레임워크: Spring Boot 4.1.1 — 확정 (이유: OSS 지원이 살아있는 최신 라인 [4.1은 2027-07-31 종료]. 3.5는 2026-06-30, 4.0은 2026-12-31 종료 예정)
- 언어/런타임: Java 21 — 확정 (이유: LTS이고 Spring Boot 4.1 지원 범위 Java 17~26 안에 있음)
- 빌드 도구: Gradle 8.14+ / 9.x + Groovy DSL — 확정 (이유: Spring Initializr 기본값, 증분 빌드. Spring Boot 4.1 최소 Gradle 8.14 — wrapper가 이 버전 이상을 고정)
- 영속성: Spring Data JPA (Hibernate)
- DB 마이그레이션: Flyway (Boot 4.1이 관리하는 12.x, `spring-boot-starter-flyway`로 추가) — 확정 (이유: 스키마가 이미 확정돼 있어 `ddl-auto` 대신 버전 관리된 마이그레이션. Boot 4에서 Flyway 오토컨피그가 스타터로 분리됨)
- DB: PostgreSQL
- 인증: Spring Security 7 + jjwt(`io.jsonwebtoken`), 직렬화 모듈은 `jjwt-gson` — 확정 (이유: 커스텀 JWT 필터 + opaque refresh 로테이션 직접 구현. jjwt가 아직 Jackson 3 미지원이라, Jackson 3이 기본인 Boot 4에서는 `jjwt-jackson`(deprecated Jackson 2를 끌어옴) 대신 `jjwt-gson` 사용)
- 테스트: JUnit 5 + Spring Boot Test + Testcontainers 2.x(PostgreSQL) + AssertJ — 확정 (이유: H2/Postgres 방언 차이 회피). Boot 4는 Testcontainers 2.0 사용 — 모듈 좌표·패키지가 재배치되고 JUnit 4 지원이 빠졌으니 2.0 기준 예제를 참고할 것
- 보일러플레이트: Lombok
- 컨테이너화: Docker (멀티스테이지)
- 배포 대상: AWS Lightsail

## 빌드 / 실행

프로젝트 스캐폴딩은 아직 없다. 최초 1회 생성 (루트에서 실행):

```
curl -sG https://start.spring.io/starter.tgz \
  -d dependencies=web,data-jpa,postgresql,flyway,security,validation,actuator,lombok,testcontainers \
  -d type=gradle-project -d language=java -d javaVersion=21 -d bootVersion=4.1.1 \
  -d groupId=com.grape -d artifactId=grape-api -d packageName=com.grape.api -d name=grape-api \
  | tar -xz
```

jjwt(`jjwt-api` + `jjwt-impl` + `jjwt-gson`)는 start.spring.io에 없으므로 생성 후 `build.gradle`에 직접 추가한다.

이후:

- 로컬 실행: 먼저 PostgreSQL 기동(로컬 설치 또는 임시 `docker run -e POSTGRES_DB=grape -p 5432:5432 postgres:16`) 후 `./gradlew bootRun`
- 테스트: `./gradlew test` (Testcontainers가 Postgres 컨테이너 자동 기동 — Docker 필요)
- 실행 jar: `./gradlew bootJar` → `build/libs/grape-api.jar`
- 컨테이너: `docker build -t grape-api .` → `docker run -p 8080:8080 --env-file .env grape-api`

## 패키지 구조

도메인별 패키지(package-by-feature)로 나눈다 — auth/user/bunch/harvest/settings가 서로 거의 독립적이라 기능 단위로 모으는 편이 탐색·변경 범위가 작다.

```
com.grape.api
├─ GrapeApiApplication.java
├─ auth       JWT 발급·검증, OAuth 검증 클라이언트(Google tokeninfo 다중 aud / Kakao code 교환+user/me), refresh 로테이션
├─ user       GET/DELETE /api/users/me
├─ bunch      bunches + bunch_fill_events, fill/replant/archive 로직
├─ harvest
├─ settings
└─ common     전역 예외 처리, SecurityConfig, 인증 필터, 에러 응답 타입, 공통 설정
```

각 도메인 패키지 내부: `XxxController`, `XxxService`, `XxxRepository`, `entity/`, `dto/`.

## 코드 컨벤션

- JPA 엔티티는 테이블 도메인명 그대로: `User`, `RefreshToken`, `Bunch`, `BunchFillEvent`, `Harvest`, `UserSettings`. DB 컬럼은 snake_case, 필드는 camelCase.
- 요청 DTO는 `XxxRequest`, 응답 DTO는 `XxxResponse` (Java `record`). 엔티티를 컨트롤러 밖으로 직접 반환하지 말 것.
- 응답 DTO 필드명은 클라이언트 TS 타입과 정확히 일치(camelCase, Jackson 기본 직렬화). **성공 응답에 공통 envelope를 씌우지 않는다** — 바디가 곧 `Bunch`/`Harvest`/`NotificationSettings`.
- ID는 서버가 UUID로 발급(컬럼 `UUID`, JSON은 문자열). 타임스탬프는 `Instant`(TIMESTAMPTZ) → ISO-8601, `fill_date`는 `LocalDate` → `YYYY-MM-DD`.
- 소유권: bunch/harvest/settings 조회·변경은 항상 인증된 `userId`로 필터한다. 리포지토리 메서드가 `userId`를 받도록 하고 `findById`만으로 반환하지 말 것. 없거나 남의 것이면 404(403 아님).
- 예외: `common`의 `ApiException`(HTTP status + code 보유) + `@RestControllerAdvice` 전역 핸들러. 에러 응답 포맷은 `{ "code": string, "message": string }` 하나로 통일.
- 입력 검증은 `@Valid` + Bean Validation 애노테이션.

## 환경변수 / 설정

`application.yml` + 환경변수 오버라이드. 실제로 필요한 키(값은 리포에 커밋하지 않음):

- `SPRING_DATASOURCE_URL` — `jdbc:postgresql://host:5432/grape`
- `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` — DB 접속 계정
- `JWT_SECRET` — access 토큰 HMAC 서명 키 (32바이트 이상)
- `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` — 선택, 기본 `1h` / `30d`
- `GOOGLE_OAUTH_CLIENT_ID` — 구글 `idToken`의 `aud` 검증용 "Web application" 클라이언트 ID
- `GOOGLE_IOS_OAUTH_CLIENT_ID` / `GOOGLE_ANDROID_OAUTH_CLIENT_ID` — 선택. 네이티브 로그인은 auth-code+PKCE로 토큰을 교환해 `aud`가 네이티브 클라이언트 ID다. `GoogleTokeninfoVerifier`가 이 세 값 중 하나와 `aud`를 대조한다(kkori-api `GoogleOAuthVerifier`와 동일). 해당 플랫폼을 안 쓰면 비워 둠
- `KAKAO_REST_API_KEY` — `POST /api/auth/kakao/web`에서 인가코드→access token 교환 시 `client_id`(`KakaoAuthApiTokenClient`). 클라이언트(grape)는 웹·네이티브 모두 이 code 경로를 쓴다. 사용자 access 토큰을 직접 받는 `POST /api/auth/kakao`(이 경우 키 불필요)도 유지되지만 현재 클라이언트는 호출하지 않는다
- `KAKAO_CLIENT_SECRET` — 선택. 카카오 개발자 콘솔에서 "보안 > Client Secret"을 켠 경우에만, 위 토큰 교환 요청에 함께 실린다. 그 외에는 비워 둠
- `CORS_ALLOWED_ORIGINS` — 선택. `/api/**`를 호출할 수 있는 브라우저 origin, 쉼표 구분. 기본값 `http://localhost:8081,http://127.0.0.1:8081,https://grape.kkori.co.kr` (dev: Expo 웹 dev 서버 / prod: 운영 도메인). 운영에선 이 값으로 좁힐 것
- `SERVER_PORT` — 선택, 기본 8080

로컬은 `.env`(gitignore) — `application.yml`의 `spring.config.import: optional:file:./.env[.properties]`가 IntelliJ/`bootRun` 실행 시 자동 로드한다(`optional:`이라 `.env` 없는 Docker/운영엔 무영향, `.dockerignore`가 이미지에서 제외). DB 접속값은 `application.yml` 기본값(`localhost:5433`, `grape`/`grape`)으로 충분하므로 `.env`에서는 주석 처리 상태 — `docker run --env-file`용으로만 사용. 배포는 아래 참고.

### CORS (`SecurityConfig`)

- `common/config/SecurityConfig`가 `/api/**`에 대해 CORS를 켠다. Spring Security 7 방식: `CorsConfigurationSource` 빈 등록 + `http.cors(cors -> cors.configurationSource(...))`. **`Customizer.withDefaults()`는 이 조합에서 빈을 못 찾아 필터가 안 붙었다 — 반드시 `configurationSource(...)`로 명시 주입.**
- 허용 메서드 `GET/POST/PATCH/DELETE`, 허용 헤더 `Authorization`·`Content-Type`, `allowCredentials=false` (JWT를 `Authorization` 헤더로 보내고 쿠키를 안 쓰므로 `Access-Control-Allow-Credentials` 불필요), preflight 캐시 1시간.
- **네이티브(iOS 시뮬/Android 에뮬)는 브라우저가 아니라 CORS 대상이 아님** — origin 목록은 Expo 웹에만 관계. Expo 웹 dev 서버는 Metro 기본 포트 8081(점유 시 8082+로 증가). `localhost`/`127.0.0.1`은 별개 origin이라 둘 다 기본값에 포함. 다른 포트/LAN IP로 뜨면 `CORS_ALLOWED_ORIGINS`로 추가.

### OAuth 토큰 검증 (`auth/oauth/`)

kkori-api와 동일하게 동작한다. 엔드포인트 구조(`/api/auth/google`, `/api/auth/kakao`, `/api/auth/kakao/web`)는 grape 고유로 유지하되, 토큰 검증 로직만 kkori-api `GoogleOAuthVerifier` / `KakaoOAuthVerifier`에 맞췄다.

- **Google** (`GoogleTokeninfoVerifier`): `https://oauth2.googleapis.com/tokeninfo?id_token=…` 호출 후 `aud` ∈ {`client-id`, `ios-client-id`, `android-client-id`}(설정된 것만), `iss` ∈ Google, `email_verified == "true"`, `sub` 존재를 검증. **`aud`를 단일 값이 아니라 목록과 대조하는 게 핵심** — 클라이언트의 네이티브 Google 로그인은 auth-code+PKCE로 토큰을 교환하므로 `aud`가 웹이 아닌 네이티브 클라이언트 ID다. 단일 `aud`만 허용하면 iOS/Android 로그인이 전부 실패한다.
- **Kakao** (`KakaoAuthApiTokenClient` + `KakaoApiUserClient`): `code`가 오면 `https://kauth.kakao.com/oauth/token`(`grant_type=authorization_code`, `client_id`=REST API 키, `redirect_uri`, `code`, 설정 시 `client_secret`)으로 access token을 교환한 뒤 `https://kapi.kakao.com/v2/user/me`. `redirect_uri`는 authorize 요청에 쓴 값과 바이트 단위로 같아야 한다(클라이언트가 그대로 넘김).
- OAuth용 `RestClient`는 5s connect / 5s read 타임아웃(`AppConfig.restClientBuilder`, kkori-api `RestClientConfig`와 동일). Google/Kakao가 응답하지 않을 때 요청 스레드가 묶이지 않게.
- 검증 실패는 전부 `ApiException(INVALID_GOOGLE_TOKEN | INVALID_KAKAO_TOKEN)` → 401.

## Docker / 배포

- **Dockerfile**: 멀티스테이지. 1단계는 `eclipse-temurin:21-jdk`에서 프로젝트 `./gradlew bootJar`(wrapper가 Gradle 8.14+/9.x 고정 — Boot 4.1 요구), 2단계는 `eclipse-temurin:21-jre`에 jar만 복사. `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`. (Java 버전은 21 유지 — Boot 4.1 지원 범위 안)
- 앱은 컨테이너 내부 **8080 고정**. Lightsail 컨테이너 서비스에서 public port → 8080 매핑.
- 헬스체크: `/actuator/health` (actuator는 health만 노출).
- **환경변수 주입**: Lightsail 컨테이너 서비스의 environment(plain) 항목으로 위 키 전달. 별도 시크릿 매니저 연동은 아직 없음.
- **Flyway**가 앱 시작 시 마이그레이션을 적용하므로, 배포 = 새 이미지 push + 컨테이너 교체로 스키마도 함께 반영된다.
- **DB 호스팅(확정)**: Lightsail 인스턴스(VPS)에 Docker로 Postgres 직접 운영(컨테이너 서비스 아님 — 컨테이너 서비스는 영속 디스크를 지원하지 않아 DB 운영 불가). API 서버는 그대로 컨테이너 서비스 유지.
- **DB 백업(확정)**: `pg_dump` 기반 정기 백업(cron) + S3 업로드. 인스턴스 자동 스냅샷은 보조 수단으로 병행 가능.
- **도메인(확정)**: `grape.kkori.co.kr`. 컨테이너 서비스의 Custom domains 기능으로 연결.
- **TLS(확정)**: 별도 인증서 발급/갱신 불필요 — Lightsail이 컨테이너 서비스용 인증서를 자체 발급(ACM 기반). 콘솔에서 인증서 생성 → DNS 검증(CNAME) → 컨테이너 서비스에 연결.
- **CI/CD**: 여전히 추후 결정. 당분간 `aws lightsail push-container-image` + 콘솔 수동 배포.

## 하지 말아야 할 것 (`server-design-draft.md` 확정 정책 중 실수하기 쉬운 것)

- **회원탈퇴에 소프트 삭제/유예기간을 만들지 말 것.** `DELETE /api/users/me`는 `users` row 즉시 삭제, 나머지는 FK `ON DELETE CASCADE`에 의존.
- **`harvests.source_bunch_id`에 FK 제약을 걸지 말 것.** JPA에서 `@ManyToOne` 매핑하지 말고 순수 `UUID` 컬럼으로 둔다. 원본 bunch가 삭제돼도 값은 유지(고아 참조 허용). 타입만 `bunches.id`와 동일하게 UUID.
- **`bunch_fill_events`에서 같은 날짜를 중복 제거하지 말 것.** `applyFilled` 이식 시 `Set`/`distinct` 금지 — `clamped > 기존 filled`면 매번 `fill_date=today` append. `fillDates` 응답은 `created_at ASC` 정렬.
- **fill 로직에 클램프 외 게이팅을 추가하지 말 것.** `clamped = max(0, min(total, filled))`만. `completedAt`은 (`clamped == total` && 기존 null)일 때만 최초 세팅, 이미 있으면 유지, `clamped < total`이면 null.
- **`/bunches/{id}/replant`는 `bunches` row를 삭제하지 않는다.** `filled=0, completedAt=null, createdAt=now, completions+1` 갱신만 + `harvests` 스냅샷 insert. fill_events도 유지(사이클 넘어 누적). row 삭제는 `/archive`와 `DELETE /bunches/{id}`만.
- **`/harvests/{id}/recall`은 새 UUID를 발급한다.** `source_bunch_id`를 새 bunch의 id로 재사용하지 말 것.
- **`addOneGrape`용 엔드포인트를 만들지 말 것.** `PATCH /bunches/{id}/fill`로 커버됨.
- **집계/통계 API(스트릭·히트맵·주평균)를 만들지 말 것.** 서버는 원본 `fillDates` 배열만 그대로 내려준다.
- **알림 스케줄러·푸시 발송 로직을 만들지 말 것.** `reminder_time`은 자유 문자열(`LocalTime` 아님), 값만 저장.
- **refresh 토큰을 순수 stateless JWT로 만들지 말 것.** DB에 해시 저장하고 매 `POST /api/auth/refresh`마다 기존 토큰 revoke + 새 토큰 발급(로테이션). `logout`도 전달받은 refresh 토큰을 revoke.
- **`POST /api/auth/google`·`/kakao`는 optional-auth.** `Authorization` 헤더에 유효한 게스트 access 토큰이 실려오면 게스트 계정 병합 처리(§3-1), 없으면 일반 로그인/가입.
- **Google `idToken`의 `aud`를 단일 클라이언트 ID와만 비교하지 말 것.** web·iOS·Android 세 클라이언트 ID 목록과 대조해야 한다(`AppProperties.Oauth.Google.allowedAudiences`). 네이티브 로그인 토큰의 `aud`는 웹 클라이언트 ID가 아니다 — 단일 비교로 되돌리면 iOS/Android Google 로그인이 깨진다.
- **2단계 공유("함께 보기") 관련 테이블·엔티티·API를 미리 넣지 말 것.**