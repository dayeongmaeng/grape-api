# 포도알 채우기 — 서버 설계 초안 (1단계: 로컬 → 서버 동기화)

> 목표: 현재 클라이언트 로컬 상태(React Context, 새로고침 시 초기화)를 그대로 서버로 옮긴다.
> 계정/로그인 + 개인 데이터 동기화까지만 다룬다. 친구/학원 "함께 보기" 공유 기능과 그 전용 테이블·API는 이번 산출물에 포함하지 않는다.

---

## 1. 스캔한 파일과 확인한 핵심 내용

| 파일 | 확인한 핵심 내용 |
|---|---|
| `CLAUDE.md`(`@AGENTS.md` 참조 선언 포함) | 1단계는 백엔드 없는 로컬 앱. `bunches`/`harvests`는 독립된 두 리스트(1:1 상태전환 아님). 완성 시 두 버튼(다시 심기 / 보관함 확인) 의미 차이. `harvests.sourceBunchId`는 원본 삭제돼도 값 유지(고아 참조 허용). |
| `src/store/grape-store.tsx` | 전역 상태의 유일한 소스. 액션 13개(§2 표에 전부 나열) + 시드 데이터 함수(`seedBunches`, `seedHarvests` — 서버 설계와 무관, 로컬 데모용). `applyFilled`가 필드 게이팅 로직(클램프, `fillDates` append 조건, `completedAt` 세팅/해제)을 갖고 있어 서버가 그대로 복제해야 할 부분. **하루에 여러 알을 채워도 `fillDates`에 그날짜가 매번 append됨(중복 허용, 의도된 동작으로 확인됨).** |
| `src/types/grape.ts` | `Bunch`, `Harvest`, `NotificationSettings` 타입 정의. `Harvest`는 `Bunch`의 부분집합이 아니라 의도적 축약 스냅샷(주석에 명시). |
| `src/app/index.tsx` | 로그인 화면. Google/카카오 버튼이 **둘 다 동일하게 `loginContinue`만 호출**하는 스텁 — 실제 OAuth SDK 연동은 아직 없음. `loginAsGuest`는 별도 액션. |
| `src/app/_layout.tsx` | 인증 분기는 `isAuthenticated`(= `session !== 'signedOut'`) 하나의 불리언 게이트. 게스트와 실사용자를 라우팅 단에서 구분하지 않음. |
| `src/app/settings.tsx` | `updateSettings` 호출. 프로필 영역의 이름/이메일("지수"/"jisoo@example.com")은 **스토어에 연결되지 않은 하드코딩 값** — 실제 사용자 프로필 조회 액션이 store에 없음. "회원탈퇴" 버튼은 존재하나 연결된 액션 없음(죽은 UI). |
| `src/app/(tabs)/index.tsx` | 홈. `bunches` 목록 렌더링만, "포도송이 N개"는 필터링 없는 `bunches.length`. |
| `src/app/(tabs)/records.tsx`, `src/lib/stats.ts` | 스트릭/히트맵/주평균 전부 `bunches[].fillDates`를 **클라이언트에서 계산**(순수 함수, 서버 저장 데이터 없음). 서버는 원본 `fillDates` 배열만 그대로 내려주면 됨 — 집계 API 불필요. |
| `src/app/(tabs)/archive.tsx` | `harvests` 목록 렌더링, 클릭 시 `harvest/[id]`로 이동(원본 `bunch/[id]` 아님). |
| `src/app/bunch/[id].tsx` | `setFilled`(그래프 셀 클릭/버튼) + `deleteBunch`(삭제 확인 모달). `addOneGrape`는 여기서도 쓰이지 않고 `setFilled(id, filled+1)`을 직접 호출 — **`addOneGrape`는 스토어에 정의만 되어 있고 어떤 화면에서도 호출되지 않는 미사용 액션**. |
| `src/app/bunch/new.tsx` | `addBunch({name, unitLabel, total, periodDays})` 호출. `detail`은 입력 필드가 아니라 store 내부에서 `unitLabel`로부터 파생됨. |
| `src/app/bunch/complete.tsx` | 두 버튼의 실제 구현 확인: <br>• "같은 송이 다시 심기" → `harvestBunch(id)` 하나만 호출 (내부에서 `addHarvest` + 리셋을 원자적으로 수행) <br>• "보관함에서 확인하기" → `addHarvest(bunch)` + `deleteBunch(bunch.id)` **두 액션을 화면에서 직접 순차 호출**(store에 합쳐진 액션 없음) |
| `src/app/harvest/[id].tsx` | `recallHarvest(harvestId, filled)` 호출 후 반환된 새 `Bunch.id`로 `/bunch/{id}`로 이동. `deleteHarvest` 별도 호출(삭제 모달). |

---

## 2. store 액션 → 서버 API 후보 전체 매핑

| store 액션 | 후보 엔드포인트 | 비고 |
|---|---|---|
| `loginContinue` (Google 버튼) | `POST /api/auth/google` | 클라이언트가 아직 실제 OAuth 미연동(§4 가정) |
| `loginContinue` (카카오 버튼) | `POST /api/auth/kakao` | 위와 동일 스텁 |
| `loginAsGuest` | `POST /api/auth/guest` | |
| `logout` | `POST /api/auth/logout` | |
| `getBunch` | `GET /api/bunches/{id}` | 목록에서 파생되는 selector, 별도 서버 로직 아님 |
| (목록 조회, `bunches` state) | `GET /api/bunches` | 홈/기록 화면이 소비 |
| `addBunch` | `POST /api/bunches` | |
| `setFilled` | `PATCH /api/bunches/{id}/fill` | |
| `addOneGrape` | *(엔드포인트 설계 안 함)* | 어느 화면에서도 호출 안 됨 + `setFilled(id, filled+1)`과 완전히 동일한 효과 → 중복 엔드포인트 불필요 |
| `harvestBunch`(다시 심기 경로) | `POST /api/bunches/{id}/replant` | §3-3 근거 참고 |
| `addHarvest`+`deleteBunch`(보관함 경로) | `POST /api/bunches/{id}/archive` | §3-3 근거 참고 |
| `deleteBunch`(단독 삭제, bunch 상세 화면) | `DELETE /api/bunches/{id}` | |
| `deleteHarvest` | `DELETE /api/harvests/{id}` | |
| (목록 조회, `harvests` state) | `GET /api/harvests`, `GET /api/harvests/{id}` | |
| `recallHarvest` | `POST /api/harvests/{id}/recall` | |
| `updateSettings` | `PATCH /api/settings` | |
| (설정 조회) | `GET /api/settings` | |
| *(store에 없음, 확정)* | `GET /api/users/me` | settings 화면 프로필 실사용 예정 — 클라이언트 연결 작업 필요(§4) |
| *(store에 없음, 확정)* | `DELETE /api/users/me` | settings의 "회원탈퇴" 버튼(현재 죽은 UI)에 연결할 예정 |
| *(store에 없음, 확정)* | `POST /api/auth/refresh` | 액세스 토큰 만료 시 클라이언트가 새로 호출해야 함(§3-1) |

---

## 3. API 명세

공통 사항:
- Base path: `/api`
- 인증: `Authorization: Bearer <accessToken>` (JWT). `/api/auth/**`만 인증 불필요.
- 응답 바디의 필드명/타입은 클라이언트 `Bunch`/`Harvest`/`NotificationSettings`와 1:1 일치(camelCase, Jackson 기본 직렬화로 변환 코드 없이 매칭).
- `bunches`/`harvests` 응답은 해당 사용자(`user_id`) 소유 레코드만 반환.

### 3-1. 인증

**토큰 정책(확정)**: Access + Refresh 토큰 방식을 쓴다.
- **Access 토큰**: JWT, 만료 1시간. 매 API 요청의 `Authorization: Bearer` 헤더로 사용.
- **Refresh 토큰**: 불투명(opaque) 랜덤 문자열, 만료 30일. DB에 해시로 저장(§5 `refresh_tokens` 테이블)해서 서버가 강제로 무효화(revoke)할 수 있게 한다 — 순수 JWT라 서버가 개입할 수 없는 방식은 쓰지 않는다.
- **로테이션**: `POST /api/auth/refresh` 호출마다 기존 refresh 토큰은 즉시 revoke하고 새 refresh 토큰을 발급한다(탈취된 토큰의 재사용 창을 최소화).
- 로그인(`google`/`kakao`/`guest`) 응답은 전부 `accessToken`과 `refreshToken`을 함께 반환한다.

**게스트 계정 병합 정책(확정)**: 게스트로 쓰던 데이터를 소셜 로그인 시 그대로 이어받는다.
- `google`/`kakao` 로그인 요청 시, 클라이언트가 **현재 게스트 세션의 access 토큰**을 `Authorization: Bearer <guestAccessToken>` 헤더로 함께 보낼 수 있다(이 두 엔드포인트는 이 헤더가 없어도 동작하는 optional-auth 엔드포인트).
- 헤더가 있고 유효한 게스트 토큰이면 서버는 아래 두 경우로 나눠 처리한다:
  1. **해당 `provider_user_id`로 가입된 계정이 없음** → 지금 게스트 계정(`users` row)의 `provider`/`provider_user_id`/`email`/`nickname`만 갱신해서 그대로 실계정으로 전환한다. `bunches`/`harvests`는 같은 `user_id`를 그대로 쓰므로 별도 데이터 이관이 필요 없다.
  2. **해당 `provider_user_id`로 이미 다른 계정이 존재함**(다른 기기에서 소셜 로그인으로 먼저 가입한 경우) → 게스트 계정의 `bunches`/`harvests`를 그 기존 계정의 `user_id`로 이관(UPDATE)한 뒤, 게스트 계정 row를 삭제한다. 응답은 기존(대상) 계정 기준 토큰.
- 헤더가 없으면 지금까지의 일반 로그인/가입 흐름과 동일하게 동작한다.
- **클라이언트 후속 작업 필요**: 지금 `loginContinue`는 게스트 토큰을 함께 보내는 로직이 없으므로, 이 병합이 실제로 동작하려면 클라이언트에서 "게스트로 로그인된 상태에서 설정 화면 등에 소셜 계정 연결하기" 진입점과, 로그인 요청 시 현재 게스트 액세스 토큰을 실어 보내는 로직이 추가로 필요하다(이번 서버 설계 범위 밖, 후속 클라이언트 작업으로 별도 정리 권장).

#### `POST /api/auth/google`
- 인증: 불필요(단, 게스트 병합을 원하면 위 정책대로 게스트 access 토큰을 `Authorization` 헤더에 실어 보낼 수 있음)
- 요청: `{ "idToken": string }`
- 응답 200: `{ "accessToken": string, "refreshToken": string, "user": { "id": string, "provider": "GOOGLE", "email": string | null, "nickname": string | null } }`
- 신규 `provider_user_id`면 계정 생성(또는 위 병합 정책에 따라 게스트 계정 전환), 기존이면 로그인.

#### `POST /api/auth/kakao`
- 요청/응답: 위와 동일 shape(리프레시 토큰·게스트 병합 정책 포함), `provider: "KAKAO"`. 요청 바디는 `{ "accessToken": string }`(카카오 SDK 토큰을 서버가 카카오 API로 검증).

#### `POST /api/auth/guest`
- 요청: `{}`
- 응답 200: `{ "accessToken": string, "refreshToken": string, "user": { "id": string, "provider": "GUEST", "email": null, "nickname": null } }`
- 매 호출마다 새 익명 계정을 생성.

#### `POST /api/auth/refresh`
- 인증: 불필요(refresh 토큰 자체가 인증 수단)
- 요청: `{ "refreshToken": string }`
- 응답 200: `{ "accessToken": string, "refreshToken": string }` (기존 refresh 토큰은 즉시 revoke, 새 토큰 발급 — 위 로테이션 정책)
- 401: refresh 토큰이 없거나 만료·revoke된 경우 → 클라이언트는 재로그인 화면으로 보내야 함.

#### `POST /api/auth/logout`
- 인증 필요
- 요청: `{ "refreshToken": string }`
- 응답 204. 전달받은 refresh 토큰을 DB에서 revoke 처리한다(§4에서 "부가 동작 없어도 됨"으로 가정했던 부분을 리프레시 토큰 확정에 맞춰 실제 동작으로 변경). Access 토큰은 스테이트리스라 만료 전까지는 유효하지만, 만료 후 재발급이 막히므로 사실상 로그아웃이 완성된다.

### 3-2. 사용자

#### `GET /api/users/me`
- 응답 200: `{ "id": string, "provider": string, "email": string | null, "nickname": string | null }`
- **확정: settings 화면에서 실사용 예정.** 지금 settings.tsx는 이름/이메일이 하드코딩("지수"/"jisoo@example.com")이라 store에도 연결 selector가 없는데, 이 API를 실제로 붙이려면 클라이언트 쪽에 조회 액션(예: `useGrapeStore()`에 `me` 상태 + `fetchMe()` 추가) 작업이 별도로 필요하다(이번 서버 설계 범위 밖, 후속 클라이언트 작업으로 정리 권장).

#### `DELETE /api/users/me` — 회원탈퇴
- 인증 필요
- 응답 204
- **확정: 즉시 하드 삭제.** `users` row를 삭제하면 `bunches`(`ON DELETE CASCADE`), `harvests`(`ON DELETE CASCADE`), `user_settings`(`ON DELETE CASCADE`), `refresh_tokens`(`ON DELETE CASCADE`)가 전부 함께 삭제된다(§5). 유예기간·소프트 삭제·복구 기능은 없음 — 삭제 확인 모달에서 "되돌릴 수 없음"을 명확히 안내하도록 클라이언트에 요구할 것.
- settings.tsx의 "회원탈퇴" 버튼(현재 연결된 액션이 없는 죽은 UI)에 이 API를 붙이는 작업이 후속 클라이언트 작업으로 필요하다.

### 3-3. Bunch

#### `GET /api/bunches`
- 응답 200: `Bunch[]` (created_at DESC — `addBunch`가 새 항목을 배열 맨 앞에 넣는 클라이언트 동작과 동일한 정렬)

```ts
interface Bunch {
  id: string; name: string; detail: string; unitLabel: string;
  total: number; filled: number; periodDays: number;
  createdAt: string; fillDates: string[];
  completedAt?: string; completions: number;
}
```

#### `GET /api/bunches/{id}`
- 응답 200: `Bunch` / 404

#### `POST /api/bunches`
- 요청: `{ "name": string, "unitLabel": string, "total": number, "periodDays": number }`
- 응답 201: `Bunch`
- 서버 로직(클라이언트 `addBunch`와 동일하게 이식):
  - `id`: 서버 생성(§4)
  - `detail` = `unitLabel`이 비어있지 않으면 `"한 알 = ${unitLabel}"`, 아니면 `""` — **서버가 파생**(클라이언트 입력 필드에 없는 값이라 클라이언트가 보내는 게 아니라 서버가 동일 공식으로 계산)
  - `filled = 0`, `createdAt = now`, `fillDates = []`, `completions = 0`, `completedAt = null`

#### `PATCH /api/bunches/{id}/fill`
- 요청: `{ "filled": number }`
- 응답 200: `Bunch`
- 서버 로직(클라이언트 `applyFilled` 그대로 이식 — 게이팅 로직 추가 금지, 클램프만 유지):
  1. `clamped = max(0, min(total, filled))`
  2. `clamped > 기존 filled`면 `fill_date = today`를 append **(하루에 여러 번 채워도 매번 append, 중복 제거하지 않음 — 클라이언트가 `Set` 없이 그대로 append하고 `records.tsx` 집계도 중복 포함 카운트를 전제로 하기 때문에 서버에서 중복을 걸러내면 클라이언트 집계 결과와 어긋남)**
     - **`fill_date` 타임존 규칙(확정)**: `fill_date`는 서버 OS/배포 리전의 타임존과 무관하게 항상 `Asia/Seoul` 기준 자정을 하루 경계로 계산한다. `ZoneId.of("Asia/Seoul")`처럼 애플리케이션 코드에 명시적으로 고정하고, 서버 JVM/OS의 기본 타임존에 의존하지 않는다. 즉 "오늘 날짜"는 매번 `Instant.now()`를 `Asia/Seoul`로 변환해 `LocalDate`를 뽑아 계산한다(`LocalDate.now()` 등 서버 기본 타임존 API 금지).
  3. `completedAt` 갱신 규칙(분기 명확화):
     - `clamped === total` **이고** 기존 `completedAt`이 `null`이면 → `completedAt = now`로 최초 세팅
     - `clamped === total` **이고** 기존 `completedAt`이 이미 존재하면 → 값 변경 없이 유지
     - `clamped < total`이면 → `completedAt = null`(총량 밑으로 다시 내려가면 완성 해제)

#### `POST /api/bunches/{id}/replant` — "같은 송이 다시 심기"
- 요청: `{}`
- 응답 200: `{ "harvest": Harvest, "bunch": Bunch }`
- 트랜잭션: `harvests`에 스냅샷 insert + 같은 `bunches` row를 `filled=0, completedAt=null, createdAt=now, completions=completions+1`로 갱신(row 자체는 삭제되지 않음).
- 스냅샷 필드(확정, 클라 `addHarvest` `grape-store.tsx:190-200` 기준): `sourceBunchId = bunch.id`, `name = bunch.name`, **`count = bunch.total`**(`bunch.filled` 아님), `harvestedAt = now`. archive도 동일.

#### `POST /api/bunches/{id}/archive` — "보관함에서 확인하기"
- 요청: `{}`
- 응답 200: `{ "harvest": Harvest }`
- 트랜잭션: `harvests`에 스냅샷 insert + 해당 `bunches` row 삭제(연쇄로 `bunch_fill_events`도 삭제, §5).

> **왜 하나의 엔드포인트(모드 파라미터)로 안 합쳤나**: 두 버튼은 "송이가 살아남는가/사라지는가"라는 상호배타적이고 되돌릴 수 없는 결과를 만든다. 하나의 `POST /bunches/{id}/harvest`에 `{mode: "replant"|"archive"}`를 넣는 방식은, 클라이언트 버그나 잘못된 값 하나로 사용자의 진행 중 송이가 조용히 삭제될 수 있는 위험을 만든다. 두 화면 버튼과 1:1 대응하는 의도가 분명한 엔드포인트로 분리하는 편이 오조작 위험이 낮고 각 트랜잭션도 단일 책임으로 단순해진다.

#### `DELETE /api/bunches/{id}`
- 응답 204 (진행 중 송이 상세 화면의 삭제 확인 모달)

### 3-4. Harvest

#### `GET /api/harvests`
- 응답 200: `Harvest[]` (harvested_at DESC)

```ts
interface Harvest {
  id: string; sourceBunchId: string; name: string; count: number; harvestedAt: string;
}
```

#### `GET /api/harvests/{id}`
- 응답 200: `Harvest` / 404

#### `DELETE /api/harvests/{id}`
- 응답 204

#### `POST /api/harvests/{id}/recall`
- 요청: `{ "filled": number }`
- 응답 200: `Bunch` (새로 생성된 활성 송이)
- 트랜잭션: 해당 `harvests` row 삭제 + `bunches`에 새 row insert:
  - `id`: 새로 생성(원본 `sourceBunchId`를 재사용하지 않음 — 클라이언트 주석과 동일 이유: 그 id는 이미 전혀 다른 사이클을 돌고 있을 수 있음)
  - `name = harvest.name`, `total = harvest.count`, `filled = clamp(0, harvest.count, 요청값)`
  - `detail = ""`, `unitLabel = ""`, `periodDays = 0`, `fillDates = []`, `completions = 0` — 하베스트 스냅샷에 없던 필드라 복원 불가(클라이언트 `recallHarvest` 주석과 동일하게 명시적으로 빈 값)

### 3-5. 설정

#### `GET /api/settings`
- 응답 200: `NotificationSettings`

```ts
interface NotificationSettings {
  dailyReminder: boolean; reminderTime: string; fillSound: boolean;
}
```

#### `PATCH /api/settings`
- 요청: `Partial<NotificationSettings>`
- 응답 200: 갱신된 전체 `NotificationSettings`

---

## 4. 확정된 결정 사항

이전 초안에서 가정으로 남겨뒀던 항목 중 아래 4가지는 논의를 거쳐 확정했다:

- **DB 종류**: PostgreSQL로 확정.
- **회원탈퇴 방식**: 즉시 하드 삭제 + CASCADE(§3-2, §5). 유예기간·소프트 삭제 없음.
- **리프레시 토큰**: Access(1시간)+Refresh(30일, DB 저장, 로테이션) 방식으로 확정(§3-1).
- **게스트 계정 병합**: 소셜 로그인 시 게스트 데이터를 이어받는 병합 플로우로 확정(§3-1). 단 이 플로우가 동작하려면 클라이언트에 게스트 액세스 토큰을 소셜 로그인 요청에 실어 보내는 로직이 후속으로 필요함.
- **`GET /api/users/me`**: settings 화면에서 실사용 확정. 클라이언트 연결(조회 액션 추가)은 후속 작업.

## 5. 스캔만으로 판단이 안 서서 여전히 가정한 부분

- **ID 전략**: 클라이언트 `nextId()`는 `"bunch_<timestamp>_<counter>"` 형태의 로컬 임시 id일 뿐 서버 PK로 쓰기 부적합. 서버가 UUID(문자열)를 새로 발급하는 것으로 가정 — 타입은 여전히 `string`이라 클라이언트 타입 정의를 바꿀 필요는 없음.
- **`detail` 필드의 소유권**: `bunch/new.tsx`에 `detail` 입력 UI가 없고 store가 `unitLabel`로부터 파생시킴. 서버가 동일 공식으로 파생시키는 걸로 가정(클라이언트가 `detail`을 요청 바디로 보내지 않음).
- **`addOneGrape`**: store에 정의는 있지만 어떤 화면도 호출하지 않는 죽은 코드로 판단, 별도 엔드포인트를 만들지 않음. 향후 이 액션이 실제로 쓰이게 되어도 `PATCH /bunches/{id}/fill`로 충분히 커버됨.
- **알림 발송/예약 로직**: `reminderTime`은 `"저녁 9:00"` 같은 자유 문자열이며 실제 스케줄링/푸시 로직이 클라이언트에 없음(CLAUDE.md에도 푸시 알림은 이번 단계 스코프 아님으로 명시). 서버도 값만 저장하고 별도 스케줄러/알림 발송은 설계하지 않음.

---

## 6. DB 스키마 (PostgreSQL 기준, Spring Boot/JPA 매핑 고려)

```
users
──────────────────────────────────────────────
id                 UUID          PK
provider           VARCHAR(20)   NOT NULL          -- 'GOOGLE' | 'KAKAO' | 'GUEST'
provider_user_id   VARCHAR(255)  NULL              -- 게스트는 NULL
email              VARCHAR(255)  NULL
nickname           VARCHAR(100)  NULL
created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()

UNIQUE (provider, provider_user_id)   -- provider_user_id가 NULL인 게스트 행끼리는
                                       -- 유니크 제약에 걸리지 않음(Postgres는 NULL을 서로 다른 값으로 취급)
```

```
refresh_tokens
──────────────────────────────────────────────
id                 UUID          PK
user_id            UUID          NOT NULL, FK → users.id ON DELETE CASCADE
token_hash         VARCHAR(255)  NOT NULL          -- 원문 토큰은 저장하지 않고 해시만 저장(SHA-256 등)
expires_at         TIMESTAMPTZ   NOT NULL          -- 발급 시각 + 30일
revoked_at         TIMESTAMPTZ   NULL              -- 로그아웃/로테이션 시 세팅. NULL이면 아직 유효
created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()

INDEX (user_id)
INDEX (token_hash)   -- /api/auth/refresh 조회용
```
- `/api/auth/refresh` 호출 시: `token_hash`로 조회 → `revoked_at IS NULL AND expires_at > now()` 확인 → 통과하면 이 행을 `revoked_at = now()`로 갱신하고 새 행을 insert(로테이션).
- `/api/auth/logout` 호출 시: 전달받은 refresh 토큰의 행을 `revoked_at = now()`로 갱신.
- 회원탈퇴(`DELETE /api/users/me`) 시 `users` row 삭제와 함께 CASCADE로 전부 삭제됨.

```
bunches
──────────────────────────────────────────────
id                 UUID          PK
user_id            UUID          NOT NULL, FK → users.id ON DELETE CASCADE
name               VARCHAR(100)  NOT NULL
detail             VARCHAR(255)  NOT NULL DEFAULT ''
unit_label         VARCHAR(100)  NOT NULL DEFAULT ''
total              INTEGER       NOT NULL
filled             INTEGER       NOT NULL DEFAULT 0
period_days        INTEGER       NOT NULL DEFAULT 0   -- 0 = 기간 없음
created_at         TIMESTAMPTZ   NOT NULL
completed_at       TIMESTAMPTZ   NULL
completions        INTEGER       NOT NULL DEFAULT 0

INDEX (user_id)
```

```
bunch_fill_events                                  -- Bunch.fillDates[] 를 정규화한 append-only 로그
──────────────────────────────────────────────
id                 BIGINT        PK, GENERATED ALWAYS AS IDENTITY
bunch_id           UUID          NOT NULL, FK → bunches.id ON DELETE CASCADE
fill_date          DATE          NOT NULL          -- toDateKey() 형식(YYYY-MM-DD)과 동일
created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()   -- 정렬용, 응답에는 노출 안 함

INDEX (bunch_id, created_at)
```
- 한 행 = 한 번의 "채움 증가" 이벤트. `fillDates` 응답은 `SELECT fill_date FROM bunch_fill_events WHERE bunch_id=? ORDER BY created_at ASC`로 재구성.
- **같은 날짜가 여러 번 들어갈 수 있음 — 확인된 의도된 동작.** 하루에 여러 알을 채우는 것이 허용되고, 클라이언트가 `Set` 없이 매번 append하며, `records.tsx`의 "이번 달 N알" 집계도 중복 포함 카운트를 그대로 쓰기 때문에 서버에서 중복을 제거하면 안 됨(제거 시 클라이언트 집계 결과와 어긋남).
- 송이가 완전히 삭제(`/archive`, `DELETE /bunches/{id}`)될 때만 같이 삭제됨. `/replant`(다시 심기)는 `bunches` row를 삭제하지 않으므로 `fillDates`는 사이클을 넘어 계속 누적(클라이언트 주석 "수확해도 초기화 안 됨"과 동일).

```
harvests
──────────────────────────────────────────────
id                 UUID          PK
user_id            UUID          NOT NULL, FK → users.id ON DELETE CASCADE
source_bunch_id    UUID          NOT NULL          -- FK 제약 없는 일반 컬럼(이유는 아래). bunches.id와 타입 일치(UUID)
name               VARCHAR(100)  NOT NULL
count              INTEGER       NOT NULL
harvested_at       TIMESTAMPTZ   NOT NULL

INDEX (user_id)
```

```
user_settings                                      -- users와 1:1
──────────────────────────────────────────────
user_id            UUID          PK, FK → users.id ON DELETE CASCADE
daily_reminder     BOOLEAN       NOT NULL DEFAULT true
reminder_time      VARCHAR(20)   NOT NULL DEFAULT '저녁 9:00'   -- 자유 문자열, LocalTime 아님(§4)
fill_sound         BOOLEAN       NOT NULL DEFAULT true
```

### `harvests.source_bunch_id`를 하드 FK로 안 만든 이유

요구사항은 "원본 `Bunch`가 삭제돼도 `sourceBunchId` **값 자체는 유지**"다(클라이언트 `Harvest` 타입 주석: "keeps `sourceBunchId` pointing at an id that may no longer resolve"). 두 선택지를 검토:

1. **`ON DELETE SET NULL`(하드 FK)** — 기각. 원본이 삭제되는 순간 컬럼값이 `NULL`로 지워지는데, 이는 "값을 유지"하라는 요구사항과 정반대다. 클라이언트는 명시적으로 "resolve되지 않는 채로 매달린 id"를 계속 들고 있길 원하지, 참조를 잃는 걸 원하지 않는다.
2. **애플리케이션 레벨 관리(FK 제약 없는 일반 컬럼) — 채택**. `source_bunch_id`를 `bunches.id`를 참조하는 순수 값 컬럼으로 두면, 원본이 삭제돼도 이 컬럼은 DB가 손대지 않아 원래 값을 그대로 보존한다. 참조 무결성(원본이 실제로 존재하는지)은 어차피 클라이언트도 검사하지 않고 그냥 고아 참조를 허용하는 설계이므로, DB 레벨에서도 강제할 이유가 없다.

> **타입 일치 참고**: FK 제약은 걸지 않지만 `source_bunch_id`는 `bunches.id`와 동일한 `UUID` 타입으로 맞춘다. 제약이 없더라도 타입이 다르면(예: `VARCHAR`) 값 비교·조인 시마다 형변환이 필요해지고, 원본이 살아있는 동안의 조인 조회(예: 관리자 디버깅 쿼리)에서도 불편해지므로 제약 없음과 타입 통일은 별개로 지킨다.

---

## 7. 참고 — 클라이언트 그대로 옮기지 않은 부분

- `bunches`/`harvests` 리스트는 여전히 완전히 분리된 두 테이블. `completions`(1:N 반복 수확), 축약 스냅샷(`detail`/`unitLabel`/`periodDays`/`fillDates` 없음), 고아 참조 허용 — CLAUDE.md와 클라이언트 타입 주석에 명시된 세 가지 근거를 그대로 스키마에 반영했습니다.
- 스트릭/히트맵/주 평균 계산은 서버에 옮기지 않았습니다. `lib/stats.ts`가 순수 함수로 클라이언트에만 존재하고, 서버는 원본 `fillDates` 배열만 정확히 복제해 내려주면 됩니다(집계 API 불필요).

---

## 8. 클라이언트 후속 작업 필요

- `records.tsx` 등 클라이언트가 자체적으로 날짜를 계산하는 곳이 있다면 `toDateKey`를 Asia/Seoul 기준으로 맞추는 작업 필요 — 서버 `fillDates`는 KST 기준으로 내려옴. (현재 `lib/stats.ts`의 `toDateKey`는 `toISOString().slice(0,10)` = UTC 기준이라 KST 자정~오전 사이 하루가 어긋남. §3-3 서버 타임존 규칙 참고.)
