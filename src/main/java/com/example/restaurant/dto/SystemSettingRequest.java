package com.example.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemSettingRequest {
    @NotBlank(message = "Tên nhà hàng không được bỏ trống")
    @Size(max = 120, message = "Tên nhà hàng không được vượt quá 120 ký tự")
    private String restaurantName;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
    private String phone;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 120, message = "Email không được vượt quá 120 ký tự")
    private String email;

    @Size(max = 100, message = "Giờ mở cửa không được vượt quá 100 ký tự")
    private String openingHours;

    @Size(max = 255, message = "Đường dẫn đặt bàn không được vượt quá 255 ký tự")
    private String reservationUrl;

    @Size(max = 255, message = "Đường dẫn thực đơn không được vượt quá 255 ký tự")
    private String menuUrl;
}
