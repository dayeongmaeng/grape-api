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
- 컨테이너(단일): `docker build -t grape-api .` → `docker run -p 8080:8080 --env-file .env grape-api`
- 컨테이너(compose, api+postgres 함께): `.env` 준비 후 `docker compose up -d --build` → `docker compose logs -f api` / 중지 `docker compose down` (DB까지 지우려면 `docker compose down -v`). 상세는 아래 "Docker / 배포 > docker compose".

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

로컬은 `.env`(gitignore) — `application.yml`의 `spring.config.import: optional:file:./.env[.properties]`가 IntelliJ/`bootRun` 실행 시 자동 로드한다(`optional:`이라 `.env` 없는 Docker/운영엔 무영향, `.dockerignore`가 이미지에서 제외). DB 접속값은 `application.yml` 기본값(`localhost:5432`, `grape`/`grape`)으로 충분하므로 `.env`에서는 주석 처리 상태 — `docker run --env-file`용으로만 사용. 배포는 아래 참고.

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

## 게스트 계정

- **게스트 데이터는 서버에 저장된다 — 별도 로컬/임시 저장소가 아니다.** `POST /api/auth/guest`(`AuthController.java:52-55` → `AuthService.guestLogin`, `AuthService.java:64-69`)가 `User.guest(now)`로 **실제 `users` row를 생성**하고(`User.java:45-50`) `UserSettings` 기본값까지 만든 뒤(`createUser`, `AuthService.java:137-141`) access/refresh 토큰을 발급한다. 게스트의 bunch/harvest/settings는 일반 계정과 동일한 테이블·소유권 규칙을 따른다.
- **게스트 구분 = `provider` 컬럼 값.** 별도 `is_guest` 컬럼은 없다. `users.provider VARCHAR(20)`에 `'GUEST'`가 들어가고(`V1__init_schema.sql:5`, `Provider.GUEST` `Provider.java:7`), 게스트는 `provider_user_id = NULL`이다(`V1__init_schema.sql:6`). 판별은 `User.isGuest()`(`provider == Provider.GUEST`, `User.java:73-75`).
- **게스트 전용 엔드포인트**: `POST /api/auth/guest` 하나. 로그인·가입 통합(소셜은 `/api/auth/google|kakao|kakao/web`).
- **게스트 → 정식 회원 이관: 구현되어 있다** (`AuthService.mergeGuest`, `AuthService.java:102-118`). 소셜 로그인 요청의 `Authorization: Bearer <게스트 accessToken>` 헤더로 트리거되며(optional-auth, `resolveGuest` `AuthService.java:120-135` — 토큰이 유효하고 그 user가 `isGuest`일 때만), 두 경로로 갈린다:
  - **Case A** (해당 provider_user_id의 기존 계정 없음): `guest.convertGuestToSocial(...)`로 **게스트 row를 그 자리에서 승격**(`User.java:66-71`). `user_id`가 그대로라 bunch/harvest 이동 불필요.
  - **Case B** (기존 계정 있음): `bunchRepository.reassignOwner(guest, target)` + `harvestRepository.reassignOwner(guest, target)`로 소유권을 옮기고(`BunchRepository.java:19-22`, `HarvestRepository.java:19-22` — `update ... set userId = :target where userId = :source`), 게스트 row는 `userRepository.delete(guest)`로 제거(게스트의 `user_settings`/`refresh_tokens`는 FK CASCADE로 함께 삭제). 응답 토큰은 target 계정 것.
  - 커버 테스트: `AuthIntegrationTest.guestMerge_caseA_*` / `guestMerge_caseB_*` (`AuthIntegrationTest.java:81-`, `149-`).
- **`deleteMe()` / `logout`에 게스트 분기는 없다.** `UserService.deleteMe`(`UserService.java:29-32`)는 provider 무관하게 `users` row 하드 삭제, `AuthService.logout`(`AuthService.java:79-83`)은 provider 무관하게 refresh 토큰만 revoke. 게스트도 정식 계정과 완전히 동일하게 처리된다.

## 회원탈퇴 정책

`DELETE /api/users/me` → `UserController.deleteMe` → `UserService.deleteMe` → `userRepository.deleteById(userId)` 한 줄이 전부다. `@Transactional` 하나로 동기 처리하고 204를 반환한다.

- **처리 방식**: `users` row 즉시 하드 삭제. `bunches`, `bunch_fill_events`, `harvests`, `user_settings`, `refresh_tokens`는 FK `ON DELETE CASCADE`로 함께 삭제된다(`V1__init_schema.sql`). 소프트 삭제·익명화·`status`/`deleted_at` 컬럼·유예기간 없음. kkori-api의 `UserWithdrawalService`(soft delete + 익명화 + `UserWithdrawalEvent`) 방식을 이식하지 않는다.
- **카카오/구글 OAuth 연동 해제(unlink/revoke)는 미구현 상태이며, DB 하드 삭제만 수행한다. 소셜 계정 측 연동은 별도 해제되지 않는다.** 탈퇴 후에도 사용자의 Google/Kakao 계정에는 이 앱의 동의 항목이 남아 있고, 같은 소셜 계정으로 재로그인하면 새 `users` row가 생성된다.
  - OAuth access/refresh token을 저장하는 테이블·엔티티가 없다(kkori-api의 `user_oauth_token` / `UserOAuthToken`에 대응하는 것 없음). `users` 컬럼은 `id, provider, provider_user_id, email, nickname, created_at`뿐. 로그인 시 받은 Google/Kakao 토큰은 신원 확인에만 쓰이고 폐기된다.
  - OAuth 클라이언트는 검증 전용이다: `GoogleTokeninfoVerifier.verify`, `KakaoApiUserClient.fetchUser`, `KakaoAuthApiTokenClient.exchangeCode`. unlink/revoke/disconnect 메서드나 `auth/oauth/disconnect/` 패키지, `KAKAO_ADMIN_KEY` 설정은 없다.
  - `@TransactionalEventListener` / `@Async` / `ApplicationEventPublisher` 등 비동기·이벤트 인프라를 쓰지 않는다(전체 소스에 없음).
- **refresh token**: CASCADE 삭제로 탈퇴 즉시 전부 사라져 재발급이 막힌다. access token(JWT, 1h)은 만료 전까지 유효하며, JWT 필터는 DB를 조회하지 않아 탈퇴 직후에도 통과한다 — 최대 1시간의 접근 창은 허용 범위로 간주한다.

### 향후 OAuth revoke 추가 시 제약

- **하드 삭제 정책은 유지한다.** revoke를 넣더라도 `users` row 삭제를 지연/소프트화하지 않는다.
- revoke는 **DB 삭제 성패와 분리된 best-effort**여야 한다: provider 호출 실패가 탈퇴 트랜잭션을 롤백시키거나 API 응답을 4xx/5xx로 만들면 안 된다(로그만 남기고 204 유지).
- `users` row가 트랜잭션 커밋과 함께 사라지므로 `provider` / `providerUserId`(및 revoke에 필요한 토큰)는 **삭제 전에 캡처**해야 한다. 커밋 후 비동기로 돌린다면 이 값들을 이벤트 페이로드로 넘긴다(kkori-api `UserWithdrawalEvent`와 동일한 이유).
- Google revoke나 Kakao 사용자 토큰 기반 unlink를 하려면 먼저 토큰 저장소(암호화된 access/refresh token 테이블)를 도입해야 한다. Kakao는 Admin Key 기반 unlink면 토큰 저장 없이 `providerUserId`만으로 가능하므로 `KAKAO_ADMIN_KEY` 설정 추가가 선행 조건이다.

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

### docker compose (로컬 / 단일 호스트 통합 실행)

루트 `docker-compose.yml` — `api`(이 리포 빌드) + `postgres` 두 서비스를 한 번에 띄운다. 운영(Lightsail) 배포 경로와는 별개이며, 로컬에서 실제 Postgres에 붙여 돌리거나 단일 VPS에 통째로 올릴 때 쓴다.

- **kkori-api와 완전히 독립.** compose 프로젝트명이 디렉터리명(`grape-api`)이라 네트워크(`grape-api_default`)·볼륨(`grape-api_postgres_data`)·컨테이너가 kkori-api(`kkori-api_*`)와 자동으로 분리된다. postgres 인스턴스·네트워크를 공유하는 설정은 넣지 않는다.
- **서비스**
  - `postgres`: `postgres:16-alpine`, 볼륨 `postgres_data:/var/lib/postgresql/data`, `pg_isready` healthcheck(10s/5s/5회). 호스트 포트 **`127.0.0.1:5433` → 컨테이너 5432** (kkori-api의 5432와 충돌 방지, localhost에서만 접근).
  - `api`: `build: .`, 호스트 포트 **`127.0.0.1:8081` → 컨테이너 8080** (kkori-api의 8080과 충돌 방지). `SPRING_PROFILES_ACTIVE=prod`, `depends_on: postgres(condition: service_healthy)`, `restart: unless-stopped`. DB 접속은 내부 네트워크로 `jdbc:postgresql://postgres:5432/grape` (내부 통신이라 5432 그대로 — 호스트 5433과 무관).
- **필요한 `.env` 키** (`.env.example` 복사 후 채움, 커밋 금지):
  - `POSTGRES_DB`(기본 `grape`) / `POSTGRES_USER` / `POSTGRES_PASSWORD` — postgres 초기화 + api의 `SPRING_DATASOURCE_*`로 자동 매핑(compose가 처리, `SPRING_DATASOURCE_*`를 따로 안 넣어도 됨).
  - `JWT_SECRET` (필수), `GOOGLE_OAUTH_CLIENT_ID`, `KAKAO_REST_API_KEY`.
  - 선택: `GOOGLE_IOS_OAUTH_CLIENT_ID`, `GOOGLE_ANDROID_OAUTH_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `CORS_ALLOWED_ORIGINS`, `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` — 비우면 `application.yml` 기본값.
- **실행**: `docker compose up -d --build` (기동) · `docker compose logs -f api` (로그) · `docker compose ps` (상태) · `docker compose down` (중지, DB 볼륨 유지) · `docker compose down -v` (DB 볼륨까지 삭제).
- **헬스체크**: `curl http://localhost:8081/actuator/health`. Flyway가 api 컨테이너 시작 시 마이그레이션을 적용한다.
- 로컬 `./gradlew bootRun`으로 compose의 postgres에 붙이려면 `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/grape`로 오버라이드(호스트에서는 5433).

## 하지 말아야 할 것 (`server-design-draft.md` 확정 정책 중 실수하기 쉬운 것)

- **회원탈퇴에 소프트 삭제/유예기간을 만들지 말 것.** `DELETE /api/users/me`는 `users` row 즉시 삭제, 나머지는 FK `ON DELETE CASCADE`에 의존. 카카오/구글 연동 해제(unlink/revoke)는 현재 미구현 — 자세한 내용과 향후 추가 제약은 "회원탈퇴 정책" 섹션 참조.
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