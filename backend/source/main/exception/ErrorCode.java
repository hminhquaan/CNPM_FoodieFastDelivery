package exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@AllArgsConstructor
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE,makeFinal = true)
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized exception", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(1012, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    USERNAME_INVALID(1003, "Username at must be at least 5 characters", HttpStatus.BAD_REQUEST),
    INVALID_ROLE(1023, "Invalid role", HttpStatus.BAD_REQUEST),
    ADDREESS_NOT_EXISTED(1024, "Address not existed", HttpStatus.NOT_FOUND),
    PASSWORD_INVALID(1004, "Password at must be at least 6 characters", HttpStatus.BAD_REQUEST),
    PHONENUMBER_INVALID(1005, "Phone number invalid", HttpStatus.BAD_REQUEST),
    INVALID_KEY(1006, "Invalid message key", HttpStatus.BAD_REQUEST),
    NOT_NULL(1007, "Please fill in all fields", HttpStatus.BAD_REQUEST),
    QUANTITY_INVALID(1008, "Quantity must be at least 1", HttpStatus.BAD_REQUEST),
    PRICE_INVALID(1009, "Price must be Positive", HttpStatus.BAD_REQUEST),
    USER_EXISTED(1001, "User existed", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1011, "User existed", HttpStatus.NOT_FOUND),
    PERMISSION_EXISTED(1013, "Permission existed", HttpStatus.BAD_REQUEST),
    PERMISSION_NOT_EXISTED(1014, "Permission not existed", HttpStatus.BAD_REQUEST),
    ROLE_EXISTED(1015, "Role existed", HttpStatus.BAD_REQUEST),
    ROLE_NOT_EXISTED(1016, "Role not existed", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1017, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_TOKEN(1006, "Invalid token", HttpStatus.UNAUTHORIZED),
    CART_NOT_EXISTED(1018, "Cart not existed", HttpStatus.NOT_FOUND),
    PRODUCT_ID_REQUIRED(1019, "productId must be filled", HttpStatus.BAD_REQUEST),
    NOT_FOUND_ITEM(1021, " Not found", HttpStatus.NOT_FOUND),
    NOT_FOUND_ORDER(1022, " Not found", HttpStatus.NOT_FOUND),
    EMPTY_CART(1020, "you haven't product in your  cart", HttpStatus.NOT_FOUND),
    STORE_NOT_EXISTED(1026, "Store not existed", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_ADDRESS_ACCESS(1025, "Address does not belong to the specified user", HttpStatus.FORBIDDEN),
    PRODUCT_NOT_EXISTED(1027, "Product not existed", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_EXISTED(1028, "Category not existed", HttpStatus.NOT_FOUND),
    CATEGORY_SLUG_EXISTED(1030, "Category slug already existed", HttpStatus.BAD_REQUEST),
    PARENT_CATEGORY_NOT_EXISTED(1029, "Parent Category not existed", HttpStatus.NOT_FOUND),
    INVALID_DOB(1010, "Invalid Dob", HttpStatus.BAD_REQUEST),
    DRONE_NOT_FOUND(1031, "Drone not found", HttpStatus.NOT_FOUND),
    DRONE_ALREADY_EXISTS(1032, "Drone already exists", HttpStatus.BAD_REQUEST),
    NO_AVAILABLE_DRONE(1033, "No available drone found for this delivery", HttpStatus.NOT_FOUND),
    DELIVERY_NOT_FOUND(1034, "Delivery not found", HttpStatus.NOT_FOUND),
    DELIVERY_ALREADY_EXISTS(1035, "Delivery already exists for this order", HttpStatus.BAD_REQUEST),
    DELIVERY_ALREADY_ASSIGNED(1036, "Delivery is already assigned to a drone", HttpStatus.BAD_REQUEST),
    DRONE_NOT_AVAILABLE(1037, "Drone is not available", HttpStatus.BAD_REQUEST),
    ORDER_NOT_PAID(1038, "Order has not been paid yet", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION(1039, "Invalid delivery status transition", HttpStatus.BAD_REQUEST),
    ORDER_NOT_EXISTED(1040, "Order not existed", HttpStatus.NOT_FOUND);


    int code;
    String message;
    HttpStatusCode statusCode;
}