# Báo cáo triển khai backend cơ bản OmniSmart

> Tài liệu bàn giao kỹ thuật cho nhánh `BackendBasic`.
> Phạm vi: xác thực và tenant, Store API, Membership/RBAC, invitation, catalog sản phẩm, upload ảnh và nền tảng vận hành backend.
> Không bao gồm: sinh nội dung bằng AI, Google Sheets, kết nối kênh bán hàng và giao diện frontend hoàn chỉnh.

## 1. Mục tiêu của phần đã triển khai

Phần backend cơ bản được xây dựng để giải quyết các nhu cầu vận hành thật của một cửa hàng nhỏ trước khi bổ sung AI:

- Người dùng đăng nhập an toàn bằng Google và làm việc bằng session phía server.
- Mỗi cửa hàng là một tenant độc lập; dữ liệu cửa hàng này không được lộ sang cửa hàng khác.
- Owner có thể quản lý cửa hàng, thành viên và lời mời nhưng không thể vô tình làm cửa hàng mất Owner cuối cùng.
- Người được mời phải tự quyết định chấp nhận hoặc từ chối quyền truy cập.
- Sản phẩm có dữ liệu thương mại chính xác, kiểm soát xung đột cập nhật và không bị xóa cứng.
- Ảnh sản phẩm được kiểm tra nội dung thật, giới hạn dung lượng/số lượng và không tin tên file từ người dùng.
- Các thao tác quan trọng có audit log phục vụ truy vết.
- Hệ thống trả lỗi API nhất quán và có thể chạy, kiểm thử với PostgreSQL thật.

Hai nguyên tắc xuyên suốt:

1. **Poka-yoke:** dùng cấu trúc code, database constraint và test để ngăn lỗi ngay từ đầu, thay vì phụ thuộc vào việc lập trình viên phải nhớ kiểm tra.
2. **Human in the Loop:** các quyết định nhạy cảm như xác nhận cửa hàng, thay đổi quyền, archive và dữ liệu thương mại phải có hành động xác nhận của con người.

## 2. Công nghệ và cấu trúc đang sử dụng

Backend tiếp tục sử dụng đúng nền tảng đã có của dự án:

- Java và Spring Boot.
- Spring MVC cho REST API.
- Spring Security và OAuth2/OIDC cho Google Login.
- Spring Data JPA/Hibernate cho tầng persistence.
- PostgreSQL làm database chạy thật.
- Flyway quản lý migration tuần tự.
- Bean Validation kiểm tra request đầu vào.
- Maven Wrapper quản lý build và test.
- JUnit, MockMvc, AssertJ và Spring Security Test cho test tự động.
- Testcontainers để kiểm tra schema trên PostgreSQL thật.
- Docker Compose để chạy PostgreSQL local.
- OpenAPI 3.1 mô tả contract backend cơ bản.

Code được chia theo miền nghiệp vụ thay vì gom toàn bộ controller/service/repository vào một thư mục chung:

```text
vn.omnismart
├── auth          # OIDC, rate limit, không lưu Google token
├── identity      # AppUser, provision user và /me
├── store         # Store lifecycle và tenant authorization
├── membership    # Thành viên, role và invitation
├── catalog       # Product, media và storage adapter
├── audit         # Nhật ký thao tác quan trọng
├── common.api    # Error response và correlation ID
└── system        # System status/health
```

## 3. Giai đoạn 1 — Xác thực Google và tenant

### 3.1. Google SSO và session phía server

Luồng đăng nhập đã được hoàn thiện theo hướng:

```text
Frontend
   → /oauth2/authorization/google
   → Google xác thực
   → /login/oauth2/code/google
   → kiểm tra OIDC claims
   → tạo/cập nhật AppUser
   → tạo session JSESSIONID phía server
   → chuyển về frontend
```

Những việc đã thực hiện:

- Tích hợp Google OpenID Connect với Spring Security.
- Chỉ chấp nhận tài khoản có claim `email_verified=true`.
- Lần đăng nhập đầu tiên tạo một `app_user`, một store mặc định và membership `OWNER`.
- Lần đăng nhập sau tìm lại user bằng `(provider, provider_subject)` và chỉ cập nhật thông tin hồ sơ cần thiết; không tạo store hoặc membership trùng.
- Database có unique constraint cho email và danh tính provider.
- Google access token không được trả về frontend.
- `DiscardingOAuth2AuthorizedClientRepository` không lưu provider access/refresh token khi hệ thống chưa cần gọi Google API thay mặt người dùng.
- Logout yêu cầu CSRF, hủy session server và xóa cookie `JSESSIONID`.
- Cookie production dùng `HttpOnly`, `Secure` và `SameSite`.
- Local profile cho phép `Secure=false` để chạy HTTP trên máy phát triển.
- Login và callback có rate limit theo client để hạn chế spam và abuse.
- API chưa đăng nhập trả JSON `401 AUTHENTICATION_REQUIRED`, không trả trang HTML login.
- API bị từ chối quyền trả error body có `code`, `message`, `traceId` và `path`.

Poka-yoke đã áp dụng:

- Không tin email chưa xác minh từ Google.
- Unique constraint bảo vệ cả khi có hai request đăng nhập đồng thời.
- Token Google bị loại bỏ theo thiết kế, tránh vô tình lưu bí mật không cần thiết.
- Rate limit được đặt trong security filter, không phụ thuộc từng controller nhớ gọi.
- Logout bắt buộc CSRF để website khác không thể ép người dùng đăng xuất.

Human in the Loop đã áp dụng:

- Store do lần đăng nhập đầu tiên tạo có `onboarding_completed=false`.
- Người dùng phải xác nhận lại tên cửa hàng bằng Store API.
- Đăng nhập Google không tự động cấp quyền Google Sheets và không tự kết nối dữ liệu ngoài.

Giới hạn hiện tại:

- Test tự động sử dụng OIDC principal giả lập nên không cần Google Client ID.
- Đăng nhập Google end-to-end trên trình duyệt chỉ kiểm thử được khi nhóm cung cấp OAuth Client ID/Secret và khai báo đúng redirect URI.

### 3.2. Tenant isolation

Mỗi tài nguyên nghiệp vụ đều được gắn `store_id`. Quyền không được xác định bằng `store_id` do request body tự gửi lên, mà dựa trên:

1. Google subject trong principal đã xác thực.
2. `app_user` tương ứng trong database.
3. Bản ghi `store_member` giữa user và store trong URL.

`StoreAuthorizationService` là điểm dùng chung để:

- Yêu cầu người dùng hợp lệ.
- Yêu cầu membership trong store.
- Yêu cầu role Owner.
- Hỗ trợ kiểm tra bằng Spring Method Security.

Repository tài nguyên tenant dùng cả resource ID và store ID, ví dụ:

```java
findByIdAndStoreId(productId, storeId)
```

Không sử dụng `findById(productId)` cho việc đọc/sửa/xóa sản phẩm từ API. Nếu Product thuộc Store B nhưng URL đang ở Store A, backend trả `404` thay vì xác nhận rằng resource của Store B tồn tại.

Ở tầng database, migration `V6` bổ sung foreign key kép:

```text
product_media(product_id, store_id)
    → product(id, store_id)
```

Do đó kể cả code có lỗi, database vẫn từ chối gắn ảnh của Store A vào sản phẩm Store B.

### 3.3. Store API và vòng đời store

Các endpoint đã có:

```text
POST  /api/v1/stores
GET   /api/v1/stores
GET   /api/v1/stores/{storeId}
PATCH /api/v1/stores/{storeId}
```

Hành vi chính:

- Tạo store mới và tự gán người tạo làm Owner.
- Sinh slug phía server và xử lý trùng slug.
- Danh sách chỉ trả các store mà user có membership.
- Member được xem thông tin store; chỉ Owner được thay đổi.
- Store sử dụng `ACTIVE/ARCHIVED`; không có API hard-delete.
- Xác nhận onboarding hoặc đổi tên được thực hiện bằng `PATCH`.
- Archive yêu cầu gửi đúng tên store hiện tại trong `confirmationName`.
- Store đã archive giữ lại dữ liệu và lịch sử, có thể được Owner kích hoạt lại.
- Tạo, cập nhật, hoàn tất onboarding, archive và reactivate đều được ghi audit.

Ảnh hưởng migration đối với dữ liệu cũ:

- Migration `V2` thêm lifecycle store và đặt `onboarding_completed=false` cho store đã tồn tại.
- Dữ liệu không bị xóa, nhưng Owner phải xác nhận store trước khi thực hiện các thao tác nghiệp vụ mới.

## 4. Giai đoạn 2 — Membership và RBAC

### 4.1. Quản lý thành viên

Các endpoint quản lý thành viên:

```text
GET    /api/v1/stores/{storeId}/members
PATCH  /api/v1/stores/{storeId}/members/{userId}
DELETE /api/v1/stores/{storeId}/members/{userId}
```

Chức năng:

- Owner xem danh sách thành viên của store.
- Owner đổi role giữa `OWNER` và `STAFF`.
- Owner thu hồi membership.
- Role được biểu diễn bằng Java enum và database check constraint.
- Mọi thay đổi role và thu hồi thành viên đều có audit log theo `store_id`.

Các bài toán thực tế đã xử lý:

- Không thể hạ quyền hoặc xóa Owner cuối cùng.
- Khi thay đổi Owner, service khóa các membership của store bằng pessimistic write lock trước khi đếm Owner.
- Hai request đồng thời không thể cùng nhìn thấy “còn hai Owner” rồi cùng xóa, khiến store không còn Owner.
- Thao tác thay đổi role hoặc thu hồi yêu cầu nhập đúng tên store để giảm nhấn nhầm.
- Store archived không cho tiếp tục thay đổi membership.
- Người không thuộc store hoặc không phải Owner không được biết dữ liệu quản trị store.

### 4.2. Invitation

Các endpoint lời mời:

```text
POST   /api/v1/stores/{storeId}/invitations
GET    /api/v1/stores/{storeId}/invitations
DELETE /api/v1/stores/{storeId}/invitations/{invitationId}

GET  /api/v1/invitations
POST /api/v1/invitations/{invitationId}/accept
POST /api/v1/invitations/{invitationId}/decline
```

Vòng đời invitation:

```text
PENDING → ACCEPTED
        → DECLINED
        → EXPIRED
        → REVOKED
```

Đảm bảo an toàn:

- Chỉ Owner tạo, xem và thu hồi lời mời của store.
- Email được chuẩn hóa lowercase.
- Không mời trùng người đã là thành viên.
- Mỗi store chỉ có một lời mời `PENDING` cho cùng email.
- Invitation mặc định hết hạn sau 72 giờ và chỉ sử dụng một lần.
- Token thô chỉ được trả khi vừa tạo để nhóm có thể kiểm thử link mời.
- Database chỉ lưu SHA-256 hash của token, không lưu token thô.
- Token và email được che trong `toString`, tránh lọt vào log.
- Người đăng nhập chỉ thấy invitation gửi đúng email đã xác minh của mình.
- Người nhận phải chủ động gọi accept hoặc decline; hệ thống không tự thêm membership.
- Không nâng quyền dựa trên domain email.
- Thu hồi lời mời yêu cầu nhập lại đúng email được mời.
- Tạo, accept, decline, expire và revoke đều được audit.

Phần chưa làm có chủ đích:

- Chưa tích hợp gửi email thật. Backend trả token để test; sau này có thể nối email provider mà không thay đổi lifecycle cốt lõi.

## 5. Giai đoạn 3 — Catalog sản phẩm

### 5.1. Schema sản phẩm

Migration `V4` tạo `product` với các trường:

```text
id, store_id, sku, name, description,
price, currency, inventory_quantity,
status, version, created_at, updated_at
```

Các constraint và index quan trọng:

- Unique `(store_id, sku)`: hai store có thể cùng SKU, một store không thể trùng SKU.
- SKU được `trim` và chuẩn hóa uppercase.
- Giá dùng `NUMERIC(19,2)`/`BigDecimal`, không dùng floating point.
- `price >= 0`.
- `inventory_quantity >= 0`.
- Currency hiện chỉ cho phép `VND` và `USD` bằng enum/constraint.
- Status chỉ gồm `ACTIVE` và `ARCHIVED`.
- Index theo store, `(store_id, status)` và `(store_id, sku)`.
- Trường `version` dùng cho optimistic locking.

Giá, tồn kho, SKU và currency là dữ liệu thương mại do người dùng nhập/xác nhận. Phần backend này không có đường gọi AI để tự thay đổi các trường đó.

### 5.2. Product CRUD

Các endpoint:

```text
POST   /api/v1/stores/{storeId}/products
GET    /api/v1/stores/{storeId}/products
GET    /api/v1/stores/{storeId}/products/{productId}
PATCH  /api/v1/stores/{storeId}/products/{productId}
DELETE /api/v1/stores/{storeId}/products/{productId}
```

Danh sách hỗ trợ:

- Phân trang từ trang `0`.
- Kích thước trang từ `1` đến `100`, mặc định `20`.
- Tìm kiếm theo chuỗi.
- Lọc theo trạng thái sản phẩm.
- Mọi truy vấn đều nằm trong store của URL.

Cập nhật chống ghi đè:

1. Client đọc sản phẩm và nhận `version` hiện tại.
2. Client gửi `version` đó khi PATCH.
3. Nếu người khác đã sửa trước, version trong database thay đổi.
4. Backend trả `409 PRODUCT_VERSION_CONFLICT`.
5. Người dùng tải lại dữ liệu và quyết định cập nhật lại, thay vì mất thay đổi của người khác.

Archive sản phẩm:

- `DELETE` mang nghĩa archive, không xóa cứng.
- Request phải có version hiện tại và nhập lại chính xác SKU trong `confirmationSku`.
- Nếu có publication job đang hoạt động, backend trả conflict thay vì archive âm thầm.
- Tạo, cập nhật và archive đều được audit.

### 5.3. Upload và quản lý ảnh

Các endpoint:

```text
POST   /api/v1/stores/{storeId}/products/{productId}/media
GET    /api/v1/stores/{storeId}/products/{productId}/media
PATCH  /api/v1/stores/{storeId}/products/{productId}/media/{mediaId}/primary
DELETE /api/v1/stores/{storeId}/products/{productId}/media/{mediaId}
GET    /api/v1/stores/{storeId}/products/{productId}/media/{mediaId}/content
```

Quy trình upload:

```text
Nhận multipart file
  → kiểm tra giới hạn request
  → tạo object key ngẫu nhiên phía server
  → lưu trạng thái TEMPORARY
  → đọc magic bytes/cấu trúc và kích thước ảnh
  → chỉ chấp nhận JPEG, PNG hoặc WebP
  → kiểm tra quota ảnh của sản phẩm
  → gắn vào đúng product/store
  → chuyển sang ATTACHED
  → ghi audit
```

Biện pháp phòng lỗi:

- Không tin extension hoặc MIME type do trình duyệt gửi.
- Kiểm tra signature và cấu trúc nội dung file.
- Giới hạn mặc định 5 MB mỗi ảnh, 8 ảnh mỗi sản phẩm.
- Giới hạn multipart ở tầng servlet để chặn file quá lớn trước khi service xử lý.
- Server tự sinh object key; không overwrite theo tên file người dùng.
- Storage nằm sau `ProductMediaStorage` adapter, nên có thể đổi local filesystem sang S3/MinIO sau này mà không đưa logic storage vào ProductService.
- Chỉ user có membership đúng store mới được xem nội dung ảnh.
- Người dùng chủ động chọn ảnh đại diện qua endpoint `primary`; hệ thống không tự chọn bằng AI.
- Xóa ảnh sai giải phóng quota và ghi audit.
- Scheduled cleanup xóa object tạm quá hạn theo batch giới hạn, tránh job quét/xóa không kiểm soát.

## 6. Nền tảng API và vận hành được bổ sung

### 6.1. Chuẩn hóa lỗi API

Các lỗi backend sử dụng cấu trúc ổn định, ví dụ:

```json
{
  "code": "PRODUCT_VERSION_CONFLICT",
  "message": "Product was changed by another request",
  "fieldErrors": [],
  "traceId": "...",
  "path": "/api/v1/stores/.../products/..."
}
```

Lợi ích:

- Frontend xử lý theo `code`, không phải phân tích chuỗi message.
- Validation error chỉ rõ field sai.
- `traceId` giúp nối lỗi người dùng báo với log server.
- Request/response nhạy cảm đã override `toString` để che email, token, giá và tồn kho khi phù hợp.

### 6.2. Profiles và fail-fast configuration

Đã tách cấu hình:

```text
application.yml
application-local.yml
application-staging.yml
application-production.yml
```

- Local cho phép cookie không Secure để chạy bằng HTTP.
- Staging/production bắt buộc cookie an toàn.
- Biến môi trường bắt buộc được tham chiếu trực tiếp để ứng dụng fail fast nếu triển khai thiếu cấu hình.
- Storage root, invitation TTL, rate limit, upload limits và cleanup schedule đều cấu hình được bằng environment variable.

### 6.3. OpenAPI và tài liệu quyết định kiến trúc

Đã thêm:

- `docs/openapi/backend-basic.yaml`: contract cho 19 nhóm đường dẫn backend không-AI.
- ADR 0003: tenant isolation và store lifecycle.
- ADR 0004: invitation, RBAC và bảo vệ Owner cuối cùng.
- ADR 0005: product catalog, media và quyết định lưu trữ.
- README, CHANGELOG, SECURITY và THIRD_PARTY_NOTICES được cập nhật theo thay đổi thực tế.

## 7. Database migrations

Không chỉnh sửa migration nền `V1`, tránh làm sai checksum Flyway trên database đã tồn tại.

| Migration | Nội dung |
| --- | --- |
| `V1__identity_baseline.sql` | User, store, membership và unique identity ban đầu |
| `V2__store_lifecycle.sql` | Trạng thái store, onboarding, archive và timestamp membership |
| `V3__membership_invitations_and_audit.sql` | Invitation lifecycle và tenant-scoped audit log |
| `V4__product_catalog.sql` | Product, product media, constraint và index |
| `V5__basic_backend_lifecycle.sql` | Mở rộng invitation/audit cho revoke và lifecycle mới |
| `V6__enforce_product_media_tenant.sql` | Composite foreign key bảo vệ media khác tenant |

Flyway chạy migration theo thứ tự khi ứng dụng khởi động. Hibernate dùng `ddl-auto=validate`, vì vậy entity Java không tự ý sửa schema; ứng dụng dừng sớm nếu schema và code không khớp.

## 8. Kiểm thử đã thực hiện

### 8.1. Bộ test mặc định

Lệnh:

```powershell
Set-Location backend
.\mvnw.cmd --quiet verify
```

Kết quả gần nhất:

```text
58 tests
0 failures
0 errors
0 skipped
```

Các nhóm test bao gồm:

- Google OIDC provisioning và email verification.
- Đăng nhập lại không tạo user/store trùng.
- Không lưu OAuth authorized client/token.
- OAuth rate limiter và filter.
- API authentication, authorization, CSRF và logout session.
- Store CRUD, onboarding, archive và cross-store access.
- Invitation accept/decline/expire/revoke và token redaction.
- Membership RBAC, Owner cuối cùng và thao tác đồng thời.
- Product CRUD, validation, tenant isolation và optimistic locking.
- Publication guard khi archive.
- Kiểm tra nội dung ảnh, quota, primary image, deletion và orphan cleanup.
- OpenAPI YAML và operation ID.
- System status.

### 8.2. PostgreSQL integration test

Lệnh:

```powershell
.\mvnw.cmd --quiet verify -Ppostgres-it
```

Profile này dùng Testcontainers khởi tạo PostgreSQL độc lập và kiểm tra:

- Toàn bộ Flyway migrations chạy được trên PostgreSQL thật.
- Unique SKU chỉ áp dụng trong phạm vi một store.
- Database chặn product media tham chiếu product của store khác.

Kết quả kiểm thử gần nhất: `3/3` integration test đạt.

### 8.3. Trạng thái local đã xác nhận

- PostgreSQL container chạy healthy.
- Backend chạy trên port `8081` và `/actuator/health` trả `UP`.
- Frontend development server chạy trên port `5173`.
- Google login thật chưa được xác nhận vì chưa có Client ID của dự án nhóm; việc này không ngăn các test backend dùng OIDC giả lập.

## 9. Cách chạy local để tiếp tục kiểm thử

Từ thư mục gốc project:

```powershell
docker compose --env-file .env -f infra/compose.yaml up -d postgres
docker compose --env-file .env -f infra/compose.yaml ps
```

Đảm bảo `.env` có cấu hình tương đương:

```env
APP_ENV=local
DATABASE_URL=jdbc:postgresql://localhost:5433/omnismart
DATABASE_USERNAME=omnismart
DATABASE_PASSWORD=omnismart
SERVER_PORT=8081
FRONTEND_URL=http://localhost:5173
SESSION_COOKIE_SECURE=false
```

Sau đó chạy backend:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Kiểm tra:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8081/api/v1/system/status
```

Không commit `.env`, Google Client Secret, invitation token hoặc dữ liệu upload local.

## 10. Ảnh hưởng đối với phần code ban đầu của nhóm

Những phần được giữ nguyên hoặc mở rộng an toàn:

- Migration `V1` không bị sửa.
- Google SSO, `AppUser`, Store và StoreMember được mở rộng thay vì thay kiến trúc nền.
- Logic `StoreAuthorization` được thay bằng `StoreAuthorizationService` đầy đủ hơn nhưng vẫn giữ bean name `storeAuthorization` cho method security.
- `/api/v1/me` giữ các field cũ và chỉ bổ sung `status`, `onboardingCompleted` trong membership.
- Các file quản trị repository như CODEOWNERS, Dependabot, security/release workflow và PR template không bị thay đổi bởi phần nghiệp vụ.
- Frontend hiện tại không phụ thuộc các API catalog/membership mới nên không bị ép thay đổi ngay.

Thay đổi tương thích cần thông báo cho nhóm:

1. Hai endpoint demo cũ `/membership` và `/owner-access` đã được thay bằng Store API thật.
2. Truy cập store không thuộc quyền đổi từ `403` sang `404` để không lộ resource.
3. Store cũ sau `V2` cần Owner xác nhận onboarding trước khi dùng catalog/invitation.
4. CI backend chạy thêm profile PostgreSQL Testcontainers nên thời gian build tăng và runner cần Docker.
5. `GEMINI_API_KEY` không còn trong `.env.example` vì backend cơ bản chưa dùng AI; khi merge nhánh AI cần thống nhất và bổ sung lại placeholder nếu cần.

## 11. Trạng thái Git và lưu ý trước khi tạo Pull Request

Tại thời điểm lập báo cáo có nhiều file mới chưa được Git theo dõi. Cần commit đồng bộ source, migration, test và tài liệu. Không nên chỉ dùng `git add -u`, vì lệnh đó sẽ ghi nhận file bị xóa nhưng bỏ qua file mới.

Thư mục `.vs/` là dữ liệu IDE local và không nên đưa vào Pull Request. Trước khi commit nên:

1. Bổ sung `.vs/` vào `.gitignore`.
2. Xem `git status --short`.
3. Stage có chọn lọc source, migrations, tests và docs.
4. Kiểm tra `git diff --cached --check`.
5. Chạy lại `mvnw verify`.
6. Chạy profile `postgres-it` nếu Docker sẵn sàng.
7. Xác nhận không có `.env`, secret, token, file upload hoặc thư mục IDE trong staged files.

## 12. Phần chưa thuộc backend cơ bản đã chủ động để lại

Các phần sau chưa được triển khai trong nhánh này và không nên ghi nhận là đã hoàn thành:

- Google Sheets/CSV import.
- AI sinh nội dung và prompt management.
- Workflow Draft → Review → Approved.
- Kết nối Facebook, TikTok hoặc sàn thương mại điện tử.
- Publication scheduler/job thực tế.
- Gửi email invitation thật.
- Object storage production như S3/MinIO.
- Backup, monitoring và triển khai production hoàn chỉnh.
- Giao diện quản lý store, thành viên, sản phẩm và xác nhận Human in the Loop.

Backend hiện tại là nền móng đủ để các phần trên phát triển tiếp mà vẫn giữ tenant isolation, audit và quyền con người làm ranh giới an toàn.

## 13. Kết luận

Nhánh `BackendBasic` đã biến phần scaffold ban đầu thành một backend nghiệp vụ có thể kiểm thử và giải quyết các tình huống thực tế: đăng nhập an toàn, nhiều cửa hàng, phân quyền Owner/Staff, mời thành viên có xác nhận, quản lý sản phẩm chống ghi đè, ảnh được kiểm tra và dữ liệu tenant được bảo vệ ở cả service lẫn database.

Điểm quan trọng không chỉ là có đủ endpoint CRUD, mà là các lỗi thường gặp đã được ngăn bằng constraint, transaction locking, version, xác nhận của người dùng, archive thay hard-delete, audit log và test tự động. Đây là nền tảng phù hợp để nhóm tiếp tục làm frontend và backend AI sau này mà không để AI hoặc client vượt qua quyền của con người hay làm sai dữ liệu thương mại.
