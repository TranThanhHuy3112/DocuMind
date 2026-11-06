# Kiểm tra và dọn repo DocuMind — 06/11/2026

Branch làm việc: `refactor/remove-unused-ui-and-deduplicate-helpers`, tách từ `main` tại `edee34d`. Mọi đợt chỉnh sửa bên dưới đều được build và test trước khi lưu commit.

## 1. Code không còn dùng

| Đã xóa | Bằng chứng trước khi xóa | Entry point/test |
| --- | --- | --- |
| `WorkspaceSectionFragment.java` và `fragment_workspace_section.xml` | Tìm toàn repo chỉ thấy tên class trong chính file khai báo; layout chỉ được dùng bởi ViewBinding của class đó. Không có tham chiếu trong manifest, navigation, Java, XML khác hoặc test. | Không có đường gọi từ Activity/Fragment đang dùng; không có test khởi tạo class. |
| `WorkspaceRepository.restoreRemote` (19 dòng) | Tìm toàn repo chỉ thấy một khai báo `private`, không thấy lời gọi. | Không thể gọi qua API công khai; không có test gọi. |
| 24 chuỗi UI cũ trong `strings.xml` (5 thuộc layout trên, 19 thuộc luồng OTP cũ) | Với từng tên, tìm `R.string.<tên>` và `@string/<tên>` trong `main`, `test`, `androidTest`: không có tham chiếu ngoài layout đã xóa. Không có `Resources.getIdentifier` trong app. | Không có đường gọi từ UI còn hoạt động hoặc test. |

Tổng phần này: **2 file và 201 dòng bị xóa**. Các lệnh kiểm chứng chính: `rg -n 'WorkspaceSectionFragment|fragment_workspace_section|restoreRemote' app`, tìm từng tài nguyên bằng `rg -n 'R\.string\.<tên>|@string/<tên>' app/src`, và `rg -n 'getIdentifier|Class\.forName' app/src/main`. Sau khi xóa, tìm lại tên class/layout/helper không còn kết quả.

Giữ lại khi chưa đủ chắc chắn: `functions/index.js` là entry point từ `functions/package.json` dù Firebase hiện chỉ deploy Firestore; có thể còn được deploy/tham chiếu bên ngoài repo. Các thành phần Firebase, ViewBinding, resource được framework gọi gián tiếp qua manifest, XML hoặc mã sinh cũng được giữ. `bg_section_icon` còn được nhiều layout dùng. Không xóa API công khai chỉ vì thiếu lời gọi tĩnh trong repo.

## 2. Mã trùng lặp

| Nhóm đã gộp | Phiên bản giữ lại và lý do | Kết quả dòng |
| --- | --- | ---: |
| Cài theme trước `super.onCreate()` của Login/Register/Verify/Reset | `BaseAuthActivity`: cùng thứ tự gọi và cùng `ThemeManager`; bốn nơi gọi chuyển sang kế thừa. | −8 |
| Đọc `TextInputEditText`, gắn watcher, hiển thị lỗi ở các form Auth | Helper trong `BaseAuthActivity`: cùng giá trị mặc định, listener và trạng thái hiển thị; các Activity gọi helper chung. | −21 |
| Định dạng dung lượng tài liệu | `DocumentSizeFormatter`: giữ ngưỡng, phép chia và chuỗi từ `DocumentAdapter`; adapter và màn so sánh cùng gọi. | +7 |
| Chuẩn hóa email trong kho local và Firebase | `AuthRepository.normalizeEmail`: hai hàm có đúng biểu thức `null → ""`, `trim()`, `toLowerCase(Locale.ROOT)`; Firebase gateway gọi hàm này. | −5 |

**4 nhóm**, tiết kiệm ròng **27 dòng**. Những đoạn gần giống được giữ riêng: `MainActivity` cài theme với `getApplicationContext()` và giữ repository; watcher của ResetPassword cập nhật thêm email/đếm ngược; các `Snackbar` khác thời gian hiển thị; luồng thêm file ở Home/Files khác thời điểm kiểm tra binding và xử lý nhánh. Chưa chứng minh được hành vi hoàn toàn giống nhau nên không gộp.

## 3. Dependencies

Đối chiếu catalog Gradle, khai báo Gradle, import Java/XML, `package.json` và mã Node. Xóa ba mục **catalog không được khai báo/cài**: alias `androidx-core-ktx`, version `coreKtx`, version `lifecycleRuntimeKtx`. **Số dependency đã cài bị xóa: 0; dung lượng cài đặt giảm có thể quy thuộc: 0 byte; code tự viết thay thế: 0 dòng.** APK có dao động kích thước khi rebuild, nhưng không thể quy cho ba mục metadata trên.

Giữ `lifecycle-viewmodel-savedstate` vì `WorkspaceViewModel` dùng `SavedStateHandle`; Guava phục vụ AI Futures; Firebase Auth/Firestore, AndroidX, Material, ML và các package Node còn tham chiếu từ mã hoặc test. `firebase-functions`, `firebase-admin`, `nodemailer` trong Functions cần quyết định sản phẩm/deploy trước khi xóa vì entry point Node còn tồn tại. Không thấy thư viện nhỏ nào có thể thay bằng vài chục dòng mà vẫn bảo toàn hành vi và rủi ro thấp.

## Kiểm tra sau từng đợt

| Đợt | Android build, unit test, lint, emulator |
| --- | --- |
| Xóa class/layout/helper chết | Đạt; 30/30 emulator |
| Xóa 19 chuỗi cũ còn lại | Đạt; 30/30 emulator |
| Gộp theme Auth | Đạt; 30/30 emulator |
| Gộp helper form Auth | Đạt; 30/30 emulator |
| Gộp định dạng dung lượng | Đạt; 30/30 emulator |
| Dọn catalog dependency | Đạt; 30/30 emulator; `assembleRelease` đạt |
| Gộp chuẩn hóa email | Đạt; 30/30 emulator |

Lệnh Android: `gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest --no-configuration-cache` với AVD `Medium_Phone`. Unit test cuối: **6/6**, không lỗi; emulator cuối: **30/30**, không lỗi; lint đạt. Node Functions: `npm.cmd --prefix functions run check` và `npm.cmd --prefix functions test` đạt, **2/2** test. Không có đợt nào phải khôi phục.

## 4. Tác vụ định kỳ — chỉ đọc, chưa thay đổi cài đặt

Task Scheduler Operational log đang **tắt**; do đó số lần chạy và thất bại trong khoảng 06/10–06/11/2026 **không xác minh được** cho mọi task Windows. `LastTaskResult` chỉ là mã của lần gần nhất, không phải thống kê tháng. Metadata không chứng minh ai dùng đầu ra, nên cột sử dụng là **chưa xác minh** cho mọi task; cũng chưa chứng minh task nào thật sự trùng nhau. Các nhãn dưới đây là phân loại sơ bộ để người dùng xem xét, không phải lệnh đã thực hiện.

| Task path / name | Lịch hoặc trigger | Lần gần nhất / mã | Phân loại sơ bộ |
| --- | --- | --- | --- |
| `\` / `AMDRyzenMasterSDKTask` | Không có lịch tiếp theo, đang chạy lúc quét | 06/11 17:14 / 267009 | Giữ |
| `\` / `ETW Host Service Updater v16` | Hằng ngày 23:47 | 05/11 23:47 / 1073807364 | Giữ |
| `\` / `NvDriverUpdateCheckDaily_{B2FE1952-0186-46C3-BAEC-A80AA35AC5B8}` | Hằng ngày 03:00 | 06/11 13:52 / 0 | Cân nhắc giảm tần suất sau khi xác nhận nhu cầu cập nhật driver |
| `\` / `NVIDIA App SelfUpdate_{B2FE1952-0186-46C3-BAEC-A80AA35AC5B8}` | Theo sự kiện | Chưa có lần chạy hợp lệ / 267011 | Giữ |
| `\` / `SamsungMagician` | Đăng nhập; **đã tắt** | Chưa có lần chạy hợp lệ / 267011 | Tắt đi (đã tắt sẵn) |
| `\` / `StartCN` | Đăng nhập | 06/11 17:13 / 1 | Giữ; cần xem mã 1 ở ứng dụng AMD |
| `\` / `StartCNBM` | Một lần 11/07, đã qua | 11/07 / 1 | Cân nhắc tắt sau khi xác nhận không cần chạy lại |
| `\` / `StartDVR` | Đăng nhập | 06/11 17:13 / 1 | Giữ; cần xem mã 1 ở ứng dụng AMD |
| `\` / `User_Feed_Synchronization-{E8241DEF-D1CD-4EB5-9B46-B087CFEBC3EF}` | Hằng ngày 20:17 | 06/11 13:52 / 0 | Cân nhắc tắt nếu không dùng RSS/Internet Explorer |
| `\GoogleUserPEH\` / `RunPlatformExperienceHelper_Daily` | Hằng ngày khoảng 22:16 | 05/11 / 0 | Giữ |
| `\GoogleUserPEH\` / `RunPlatformExperienceHelper_Metrics` | Một lần 10/07, đã qua | 10/07 / 0 | Cân nhắc tắt sau khi xác nhận không cần chạy lại |
| `\SoftLanding\S-1-5-21-2068416847-2290578890-459287644-1002\` / `SoftLandingCreativeManagementTask` | Nhiều trigger thời gian | 06/11 13:52 / 0 | Giữ |

Để tắt **từng task Windows** trong bảng sau khi tự chọn, chạy PowerShell với hai giá trị ở cột đầu: `Disable-ScheduledTask -TaskPath '<task path>' -TaskName '<name>'`. Ví dụ cho task đã hết lịch: `Disable-ScheduledTask -TaskPath '\' -TaskName 'StartCNBM'`. Với task trong thư mục con, dùng chính path đầy đủ có dấu `\` cuối như bảng. Bật lại bằng `Enable-ScheduledTask` với cùng tham số. Chưa chạy các lệnh này.

WSL Ubuntu: `crontab -l` của user `ubuntu` và `root` đều trống. Các job hệ thống mặc định còn thấy bằng tên file: `/etc/cron.d/e2scrub_all`; `/etc/cron.daily/{logrotate,apt-compat,dpkg,man-db,apport}`; `/etc/cron.weekly/man-db`. Tần suất daily/weekly theo thư mục; `e2scrub_all` cần xem nội dung lịch để xác nhận. Không có số lần chạy/thất bại đáng tin cậy từ metadata, không xác minh được người dùng đầu ra. **Giữ** tất cả job mặc định; để tắt riêng cần quản trị lịch ở đúng file dưới `/etc/cron.d` hoặc bỏ script khỏi thư mục cron tương ứng sau khi sao lưu. Không thực hiện. `launchd` không có trên Windows. Repo không có `.github/workflows` và không thấy cron/timer tự chạy trong mã dự án; bộ đếm thời gian UI là theo tương tác người dùng.

## 5. Phiên làm việc, worktree và cache — chỉ metadata

Không mở nội dung file hiện có trong `~/.codex` khi quét mục này. `sessions`: 4 file tháng 09/2026, **16.812.633 byte**; 14 file tháng 11/2026, **38.736.164 byte**. `archived_sessions`: 1 file tháng 09, **49.786 byte**. Các file này vào nhóm **cần xem xét** vì metadata không chứng minh phiên đã ngừng dùng. Tương tự, `.codex/cache` **62.126.600 byte**, `.codex/.tmp` **85.049.294 byte** và `.codex/tmp` **584 byte** đều cần xem xét; một số file được cập nhật hôm nay.

Tại thời điểm quét, `git worktree list --porcelain` chỉ có worktree repo hiện tại; branch có các commit chưa push so với `origin/main`. Vì vậy worktree thuộc nhóm **cần xem xét** và không được xóa. Không có worktree nào đạt điều kiện an toàn.

Nhóm **an toàn để xóa nếu cần dung lượng** (đều tự tạo lại được): `app/build` 165.055.231 byte; `build` 11.421.433 byte; `.gradle` 13.151.620 byte; `functions/node_modules` 206.581.683 byte (`npm ci` khôi phục từ lockfile). **Tổng có thể giải phóng: 396.209.967 byte, khoảng 377,9 MiB.** Đây là tổng kích thước file từ metadata; dung lượng thực trên đĩa có thể khác do kích thước cluster. Theo yêu cầu sau kiểm tra, script dọn dẹp đã được xóa; **không có thư mục hay worktree nào bị xóa**.
