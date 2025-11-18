package exception;


import dto.response.API.APIResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j

public class GlobalExceptionHandler {

    //bắt tất cả exception khi chạy trong spring
    @ExceptionHandler(value = Exception.class)
    ResponseEntity<APIResponse> handlingRuntimeException(Exception e){
        log.error("Uncategorized exception: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                APIResponse.builder()
                        .code(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode())
                        .message(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage() + ": " + e.getMessage())
                        .build()
        );
    }

        // Map AccessDeniedException to 403 instead of 500
        @ExceptionHandler(value = AccessDeniedException.class)
        ResponseEntity<APIResponse> handleAccessDenied(AccessDeniedException e){
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(
                APIResponse.builder()
                    .code(ErrorCode.UNAUTHORIZED.getCode())
                    .message(e.getMessage() == null ? ErrorCode.UNAUTHORIZED.getMessage() : e.getMessage())
                    .build()
            );
        }

    //bắt những lỗi logic khi chạy trong spring
    @ExceptionHandler(value = AppException.class)
    ResponseEntity<APIResponse> handlingAppException(AppException e){
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(
                APIResponse.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .build()
        );
    }

    // Xử lý validation errors
    @ExceptionHandler(value=MethodArgumentNotValidException.class)
    ResponseEntity<APIResponse> handlingValidation(MethodArgumentNotValidException e){
        String enumKey = e.getFieldError().getDefaultMessage();

        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        try {
            errorCode = ErrorCode.valueOf(enumKey);
        } catch (IllegalArgumentException exception) {
            // If not a valid enum key, return the validation message directly
            return ResponseEntity.badRequest().body(
                    APIResponse.builder()
                            .code(HttpStatus.BAD_REQUEST.value())
                            .message(enumKey)  // Return actual validation message
                            .build()
            );
        }
        return ResponseEntity.badRequest().body(
                APIResponse.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .build()
        );
    }

}
