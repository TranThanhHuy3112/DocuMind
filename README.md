# DocuMind

**DocuMind** là ứng dụng Android giúp quản lý tài liệu cá nhân, khai thác nội dung bằng AI và trao đổi trong cộng đồng. Ứng dụng viết bằng Java 17, giao diện XML/Material 3 và dùng Firebase cho xác thực, bài viết cộng đồng và AI Logic.

## Tính năng

| Khu vực | Đã triển khai |
| --- | --- |
| Tài khoản | Đăng ký, đăng nhập bằng email/mật khẩu; xác minh email và đặt lại mật khẩu qua liên kết gửi tự động; đổi tên hiển thị. |
| Thư viện | Chọn và quản lý tối đa 5 tài liệu cho mỗi tài khoản trên thiết bị; tìm kiếm, lọc, sắp xếp và phát hiện file trùng. |
| Công cụ AI | Hỏi đáp theo tài liệu, chép lời audio/video, so sánh hai tài liệu và tạo dàn ý/báo cáo. Mỗi lần gửi nội dung đều do người dùng chủ động bấm chạy. |
| Phân tích | Đọc doanh thu từ CSV hoặc trang tính đầu tiên của XLSX, tính tổng và hiển thị biểu đồ tối đa 12 dòng. |
| Cộng đồng | Bài viết, bình luận và lượt đánh giá hữu ích dạng văn bản được đồng bộ qua Firestore; tác giả có thể xóa bài của mình. |
| Giao diện | Trang chủ, Thư viện, Cộng đồng, Tài khoản; chế độ sáng/tối, 5 màu nhấn và 2 mật độ danh sách. |

## Công nghệ và cấu trúc

- **Android:** Java 17, Android Gradle Plugin, ViewBinding, AndroidX và Material 3. `minSdk 24`, `compileSdk/targetSdk 37`.
- **Dữ liệu trên máy:** `WorkspaceRepository` và `LocalDataStore` quản lý danh sách tài liệu, hoạt động gần đây và cài đặt.
- **Dịch vụ:** Firebase Authentication cho tài khoản; Cloud Firestore cho Cộng đồng; Firebase AI Logic với Gemini Developer API cho công cụ AI.
- **Kiểm soát truy cập:** [`firestore.rules`](firestore.rules) giới hạn thao tác ghi theo tài khoản đã xác minh và quyền sở hữu nội dung.
- **Mã nguồn:** `app/src/main/java` chứa app; `app/src/test` và `app/src/androidTest` chứa kiểm thử; `functions/` là mã thử nghiệm OTP cũ, không được app gọi hoặc triển khai.

## Trạng thái và giới hạn

- **File tài liệu chỉ lưu trên máy** theo lựa chọn hiện tại của dự án; chưa đồng bộ giữa các thiết bị. Cộng đồng chỉ chia sẻ văn bản, không đính kèm file. Cloud Storage và Cloud Functions không được bật trên project Spark này.
- AI nhận PDF, TXT, CSV, DOCX, XLSX, MP4, MOV, MP3, WAV, M4A, tối đa **10 MB mỗi file** và **12 MB mỗi yêu cầu**. DOCX/XLSX được trích nội dung cục bộ; DOC/XLS cũ cần chuyển định dạng. Video dài hoặc file lớn cần cắt trước khi dùng.
- AI dùng hạn mức miễn phí của Gemini Developer API, có thể tạm quá tải hoặc cho kết quả sai. App thông báo trước khi gửi nội dung tài liệu; không nên dùng tài liệu bí mật hoặc tài liệu không được phép gửi.
- Thư viện lưu URI và quyền đọc do Android cấp. Nếu file gốc bị xóa hoặc nhà cung cấp file thu hồi quyền, người dùng cần thêm file lại.
- Trước khi phát hành rộng rãi, cần cấu hình App Check production provider, ký bản release và kiểm thử bản release trên thiết bị thật.

## Chạy dự án

1. Mở thư mục này bằng Android Studio, cài Android SDK 37 và dùng JDK đi kèm Android Studio.
2. Repo có sẵn `app/google-services.json` và `.firebaserc` của Firebase project dùng chung trong nhóm. Authentication, Firestore và AI Logic đã được bật cho project này. Để xem hoặc triển khai cấu hình Firebase, chủ project cần cấp quyền cho tài khoản của từng thành viên.
3. Chạy cấu hình `app` trên máy ảo hoặc thiết bị Android. Nếu muốn dùng Firebase project riêng, thay `app/google-services.json`, cập nhật `.firebaserc`, rồi bật **Authentication → Email/Password**, **Cloud Firestore** và **AI Logic → Gemini Developer API** trên project đó.

Trên Windows có thể build và kiểm thử bằng PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-configuration-cache
# Cần máy ảo hoặc máy thật đang kết nối:
.\gradlew.bat connectedDebugAndroidTest --no-configuration-cache
```

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`. APK trong `app/build/outputs/apk/androidTest/debug/` chỉ dành cho bộ kiểm thử, không phải app để cài cho người dùng.

Khi thay đổi quyền Cộng đồng, kiểm tra [`firestore.rules`](firestore.rules) rồi triển khai vào project mà bạn được cấp quyền quản lý:

```powershell
npx.cmd --yes firebase-tools@latest deploy --project <firebase-project-id> --only firestore:rules
```

## Kiểm thử đã thực hiện

- Build debug, unit test, Android Lint và **30/30 Android instrumented test** đã qua trên bản hiện tại (06/11/2026).
- Đăng ký/xác minh email, đặt lại mật khẩu, hỏi AI từ file TXT và đăng/xóa bài cộng đồng đã được thử với dịch vụ thật trên máy ảo.
- Bộ instrumented test cần máy ảo hoặc thiết bị Android; lần chạy hiện tại dùng AVD `Medium_Phone`.

Báo cáo cục bộ sau khi chạy test: `app/build/reports/androidTests/connected/debug/index.html` và `app/build/reports/lint-results-debug.html`.

## Ghi chú

- Repo nhóm giữ `google-services.json` để mọi người build cùng Firebase project. Không commit khóa ký app, mật khẩu hoặc file môi trường; `.gitignore` đã loại các file này. Trước khi đổi repo sang công khai, kiểm tra giới hạn API key và App Check.
- Mỗi tính năng nên làm trên một nhánh riêng, gửi pull request có mô tả thay đổi và cách đã kiểm thử. Giữ rules cùng thay đổi tính năng Firestore.
- Các giới hạn của gói miễn phí có thể thay đổi; kiểm tra tài liệu [Firebase AI Logic](https://firebase.google.com/docs/ai-logic/get-started) và [Firebase Authentication](https://firebase.google.com/docs/auth/android/manage-users) khi cấu hình project mới.
