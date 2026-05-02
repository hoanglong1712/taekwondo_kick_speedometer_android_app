# Kế hoạch Phát triển Ứng dụng Android Đo Thời Gian Đá Taekwondo

Ứng dụng đo lường thời gian phản xạ và thực hiện đòn đá Taekwondo bằng cách sử dụng Camera để nhận diện khoảnh khắc mục tiêu (vợt) bị đá trúng.

## 1. Tổng quan Công nghệ (Technology Stack)
* **Nền tảng:** Android (Ngôn ngữ Kotlin).
* **Camera API:** **CameraX** (Dễ dàng trích xuất luồng video theo thời gian thực).
* **Thư viện Thị giác máy tính:** **OpenCV cho Android** (Dùng để bóc tách màu sắc, theo dõi đối tượng và phân tích chuyển động).
* **Quản lý luồng (Concurrency):** Coroutines (Xử lý ảnh ngầm không làm đơ giao diện UI).

## 2. Kiến trúc thuật toán cốt lõi (Core Logic)

Để nhận biết "vợt bị đá trúng và rung lên rõ ràng", quy trình xử lý từng khung hình (frame) như sau:

### Bước 1: Nhận diện vợt (Color Tracking & Masking)
* Chuyển đổi khung hình từ không gian màu RGB sang **HSV** (Hue, Saturation, Value) để ít bị ảnh hưởng bởi thay đổi ánh sáng.
* **Lọc dải màu (Thresholding):** Lọc các pixel có màu Đỏ hoặc Xanh (dựa theo lựa chọn của người dùng).
* **Tìm đường bao (Contours):** Xác định vùng màu đỏ/xanh lớn nhất trên màn hình – đó chính là chiếc vợt.

### Bước 2: Xác định trạng thái "Nghỉ" (Calibration/Resting State)
* Trước khi trọng tài bấm Start, hệ thống xác định tọa độ tâm (centroid) và diện tích (area) của chiếc vợt.
* Khóa khu vực theo dõi (Region of Interest - ROI) xung quanh chiếc vợt để giảm thiểu tính toán và loại trừ nhiễu.

### Bước 3: Phát hiện va chạm (Impact Detection)
Sử dụng các phương pháp phân tích chuyển động đột ngột:
1. **Vận tốc dịch chuyển của tâm vợt (Centroid Shift):** Nếu tâm của vùng màu dịch chuyển một khoảng cách lớn hơn một ngưỡng (Threshold) cố định chỉ trong 1-2 khung hình.
2. **Sự thay đổi diện tích đột ngột (Area Deformation):** Khi bị đá, vợt biến dạng hình học trên camera làm thay đổi diện tích vùng màu đột ngột.
3. **Frame Differencing (Trừ ảnh):** Lấy khung hình hiện tại trừ đi khung hình trước đó. Nếu số lượng pixel thay đổi vượt mức cho phép -> Rung lắc mạnh.

## 3. Kịch bản sử dụng (User Flow)

1. **Chuẩn bị:** Trọng tài mở App, chĩa camera về phía cái vợt.
2. **Thiết lập:** Trọng tài chọn màu vợt (Đỏ / Xanh). App hiển thị một khung (Bounding Box) bao quanh cái vợt để báo hiệu nhận diện thành công.
3. **Sẵn sàng:** Trọng tài bấm nút **START**.
   * App phát âm thanh báo hiệu.
   * Đồng hồ bắt đầu chạy (đếm mili-giây). Lưu thời điểm **T0**.
4. **Thực thi:** Võ sĩ tung đòn đá trúng vợt.
5. **Ghi nhận:** Thuật toán bắt được chuyển động giật mạnh.
   * Ghi nhận va chạm thành công. Lưu thời điểm **T1**.
   * Thời gian đá = `T1 - T0`.
   * Đồng hồ dừng lại, hiển thị kết quả.

## 4. Các giai đoạn triển khai (Implementation Phases)

### Phase 1: Xây dựng nền tảng (UI & CameraX)
* Khởi tạo Project Android (Kotlin).
* Tích hợp CameraX (PreviewUseCase để hiển thị lên màn hình và ImageAnalysisUseCase để trích xuất frame).
* Thiết kế UI: Nút Start, Stop, Text hiển thị thời gian, nút chọn màu vợt.

### Phase 2: Tích hợp OpenCV và Nhận diện màu
* Đưa thư viện OpenCV vào project.
* Viết hàm phân tích (`ImageProxy` -> OpenCV `Mat`).
* Code bộ lọc dải màu Đỏ (Red HSV) và Xanh (Blue HSV).
* Vẽ Bounding Box bám theo chiếc vợt.

### Phase 3: Thuật toán phát hiện rung lắc
* Tính toán khoảng cách dịch chuyển của khung hình so với frame trước đó.
* Tạo UI (Slider ẩn) để điều chỉnh độ nhạy (Threshold) của "Cú đá" cho phù hợp với thực tế.

### Phase 4: Hoàn thiện logic đo đếm
* Kết nối nút Start vào bộ đếm giờ (`System.currentTimeMillis`).
* Khi độ dịch chuyển > Threshold -> Dừng bộ đếm, hiển thị kết quả.
* Bổ sung âm thanh báo hiệu (`SoundPool`).

## 5. Thách thức và Giải pháp

* **Nhiễu ánh sáng:** Ánh sáng phòng tập làm màu đỏ/xanh thay đổi.
  * *Giải pháp:* Cho phép người dùng chạm vào cái vợt trên màn hình để lấy mẫu màu thực tế (Color Picker Calibration).
* **Khung hình bị mờ (Motion Blur):** Camera bị nhòe khi vợt chuyển động nhanh.
  * *Giải pháp:* Lợi dụng chính sự tụt giảm giá trị pixel do nhòe để làm tín hiệu nhận biết va chạm.
* **Tối ưu FPS:**
  * *Giải pháp:* Resize ảnh xuống độ phân giải thấp (ví dụ 480x640) trước khi đẩy vào OpenCV để đảm bảo xử lý mượt mà.
