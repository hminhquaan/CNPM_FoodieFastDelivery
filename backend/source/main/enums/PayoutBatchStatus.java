package enums;

public enum PayoutBatchStatus {
    PENDING,     // Mới tạo, chưa xử lý
    PROCESSING,  // Đang thực hiện thanh toán
    PAID,        // Đã thanh toán thành công
    FAILED       // Thanh toán thất bại
}

