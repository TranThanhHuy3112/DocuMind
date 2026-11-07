# DocuMind

DocuMind là ứng dụng Android để quản lý tài liệu trên điện thoại và làm việc với nội dung của chúng. Bạn có thể tìm lại file, hỏi AI về tài liệu, xem nhanh số liệu doanh thu và trao đổi với mọi người trong Cộng đồng.

## Tính năng

- **Thư viện tài liệu:** chọn tối đa 5 file cho mỗi tài khoản trên thiết bị; tìm kiếm, lọc, sắp xếp và nhận biết file trùng.
- **Hỏi AI:** đặt câu hỏi dựa trên nội dung tài liệu đã chọn.
- **Công cụ nội dung:** chép lời từ audio/video, so sánh hai tài liệu, tạo dàn ý và báo cáo.
- **Phân tích doanh thu:** đọc CSV hoặc trang tính đầu tiên của XLSX, tính tổng và hiển thị biểu đồ.
- **Cộng đồng:** đăng bài, bình luận, đánh giá hữu ích và xóa bài của chính mình. Nội dung văn bản được đồng bộ giữa các thiết bị.
- **Tài khoản:** đăng ký, đăng nhập bằng email và mật khẩu; xác minh email và đặt lại mật khẩu bằng liên kết gửi qua email.
- **Giao diện:** chế độ sáng/tối, màu nhấn và mật độ hiển thị danh sách có thể tùy chỉnh.

## Hiện tại

Danh sách tài liệu được lưu trên thiết bị, còn file gốc vẫn nằm ở nơi bạn đã chọn; chưa có đồng bộ giữa các máy. Nếu file gốc bị xóa, bạn cần thêm lại. Cộng đồng chỉ hỗ trợ bài viết dạng văn bản, chưa đính kèm file. Khi dùng công cụ AI, ứng dụng chỉ gửi nội dung sau khi bạn chủ động chọn và bắt đầu thao tác.

## Chạy dự án

1. Mở repo bằng Android Studio, cài Android SDK 37 và dùng JDK đi kèm Android Studio.
2. Chờ Gradle đồng bộ rồi chạy cấu hình `app` trên máy ảo hoặc điện thoại Android.

Repo có sẵn cấu hình Firebase dùng chung của nhóm trong `app/google-services.json`. Muốn quản lý Authentication, Firestore hoặc AI Logic trên Firebase Console, thành viên cần được chủ project cấp quyền.

APK debug sau khi build nằm ở `app/build/outputs/apk/debug/app-debug.apk`.
