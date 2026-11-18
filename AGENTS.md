# Quy tắc Phát triển Dự án PhimBud Mobile (Workspace Rules)

Quy tắc này được áp dụng bắt buộc cho mọi tác vụ phát triển, sửa đổi, kiểm thử và bàn giao trong kho mã nguồn `budmobile`.

---

## 1. Mô phỏng Web Thật & Chống Phát hiện Bất thường (Stealth & Zero-Detection)

Dự án là client đa nền tảng (Kotlin Multiplatform) đóng gói lại dịch vụ `phimbud.com`. Mọi tác vụ mạng phải bảo đảm máy chủ `phimbud.com` không thể phân biệt được ứng dụng với trình duyệt web thông thường:

1. **Chuẩn hóa HTTP Headers**:
   - Mọi request gửi tới `phimbud.com` hoặc các CDN video liên kết phải đi qua `PlatformHttpEngine.httpClient` để tự động đính kèm đầy đủ bộ headers của Google Chrome trên Windows (`BrowserHeaders`):
     - `User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ...`
     - Client Hints: `sec-ch-ua`, `sec-ch-ua-mobile: ?0`, `sec-ch-ua-platform: "Windows"`
     - Fetch Metadata: `Sec-Fetch-Site`, `Sec-Fetch-Mode`, `Sec-Fetch-Dest`
     - Header `Origin: https://phimbud.com` và `Referer: https://phimbud.com/...`
   - Quản lý phiên đăng nhập và Cookie liên tục bằng `PersistentCookieStore` / `PersistentCookieJar`.
2. **Không Gửi Request Quét / Dò Tìm Bất Thường**:
   - Tuyệt đối không gửi hàng loạt request tìm kiếm, crawl dữ liệu dồn dập lên `phimbud.com`.
   - Tìm kiếm và đối chiếu danh mục phim phải tận dụng file index tĩnh tải một lần (`getAllTitlesIndex()` theo giờ UTC) và xử lý 100% trên bộ nhớ RAM / Disk Cache.
3. **Cách ly Dịch vụ Bên Thứ Ba (Zero Header/Cookie Leak)**:
   - Khi tích hợp hoặc tải dữ liệu từ các nguồn mở bên ngoài (như Netflix Tudum, Apple RSS, CDN phụ đề công khai, v.v.), **bắt buộc** sử dụng `publicHttpClient` độc lập (`CookieJar.NO_COOKIES`, không đính kèm cookie, không header `Origin`/`Referer` của `phimbud.com`) để tránh làm rò rỉ phiên đăng nhập hoặc thông tin hệ thống.

---

## 2. Quy trình Hoàn thành Task & Kiểm tra Mã nguồn

Sau mỗi lần thực hiện tính năng mới hoặc sửa lỗi, trước khi bàn giao cho người dùng:

1. **Kiểm tra Kiểm thử & Biên dịch Đa nền tảng**:
   - Chạy lệnh kiểm tra unit test và khả năng biên dịch chéo cho cả Android và iOS khi cần thiết để bảo đảm mã nguồn không lỗi:
     ```powershell
     .\gradlew.bat :composeApp:testDebugUnitTest :composeApp:compileKotlinIosArm64
     ```
2. **Không Tự Động Build Release APK**:
   - Không cần tự động chạy lệnh đóng gói Release APK (`assembleRelease`) trên máy local sau mỗi task.
   - Chỉ thực hiện build APK hoặc export gói cài đặt khi người dùng có yêu cầu cụ thể.

---

## 3. Tiêu chuẩn Thiết kế UI/UX Chuẩn Netflix (Netflix Format)

Toàn bộ giao diện ứng dụng (Android & iOS) phải tuân thủ nghiêm ngặt ngôn ngữ trải nghiệm người dùng đặc trưng của Netflix:

1. **Hệ màu sắc & Phong cách Trực quan**:
   - Nền đen sâu chủ đạo: `NetflixBlack` (`#000000` / `#141414`), thẻ nổi `NetflixDarkGray` (`#1F1F1F`) và `NetflixCard` (`#2A2A2A`).
   - Màu nhấn nhận diện thương hiệu: `NetflixRed` (`#E50914`).
   - Chữ chính: Trắng sáng (`NetflixTextPrimary`), chữ phụ xám mờ (`NetflixTextSecondary`).
2. **Bố cục Màn hình & Trang chủ (`HomeScreen`)**:
   - **Hero Banner**: Chiếm vị trí trang trọng ở đầu trang, hình nền phim lớn tràn viền kèm lớp gradient mờ tối dần xuống chân, thông tin thể loại, nút "Phát" trắng nổi bật và nút "Thông tin".
   - **Thanh Bộ lọc (Pills / Chips)**: Dạng bo tròn hình con nhộng nổi trên nền mờ (Tất cả, Top 10 Việt Nam, Top 10 Thế Giới, Phim Bộ, Phim Lẻ).
   - **Hàng Top 10 Đánh số (#1 – #10)**: Thiết kế số thứ tự khổng lồ in đậm viền trắng/đỏ nổi bật cạnh poster bo góc, kèm huy hiệu đỏ `TOP 10`.
   - **Các hàng danh mục (`MovieRow`)**: Danh sách phim cuộn ngang poster tỉ lệ 2:3 với khoảng cách tiêu chuẩn.
3. **Trình phát Video (`PlayerScreen`)**:
   - Giao diện điều khiển tối giản, tự động ẩn sau 4 giây khi đang phát.
   - Nút tua nhanh / lùi 10 giây (`Replay10`, `Forward10`) và nút Play/Pause lớn ở chính giữa.
   - **Bỏ qua Mở đầu & Tóm tắt (`FloatingSkipOverlay`)**: Nút nổi viền trắng nền đen mờ ở góc dưới bên phải xuất hiện tự động khi đến đoạn Intro/Recap và tự ẩn sau 10 giây nếu không bấm.
   - **Ngăn kéo Chọn Tập phim trong Trình phát (`PlayerEpisodesOverlay`)**: Trượt ra mượt mà từ cạnh màn hình, hỗ trợ chọn Mùa (Season), hiển thị danh sách tập kèm thumbnail, tự cuộn tới tập đang phát có nhãn đỏ "Đang phát".
   - **Tự động chuyển tập khi kết thúc (`isPlaybackEnded`)**: Khi tập hiện tại phát xong, tự động lưu tiến độ và chuyển mượt sang tập tiếp theo mà người dùng không cần chạm màn hình.

---

## 4. CI & tự động phát hành (một workflow duy nhất)

`.github/workflows/build.yml` ("Build & Release") chạy mọi job trong mỗi lần push lên `main`/`master` (không job nào bị bỏ qua):

1. `version` → xác định tag; 2. `compile-android` / `compile-desktop` → kiểm tra biên dịch + báo lỗi dạng annotation; 3. `build-android` → APK release (minify); 4. `build-windows` → `.exe` + `.msi`; 5. `release` → đăng GitHub Release kèm toàn bộ file.

- Tăng `packageVersion` trong `composeApp/build.gradle.kts` → Release chính thức `v<phiên bản>`.
- Cùng phiên bản đã có tag → tự tạo pre-release `v<phiên bản>-build.<số lần chạy>` (vẫn có APK/bộ cài mới).
- Push tag `v*` hoặc chạy tay (`workflow_dispatch`) cũng được. iOS không còn workflow.
