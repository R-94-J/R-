package cn.edu.campus.kb;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

class ApiException extends RuntimeException {
    final HttpStatus status;
    ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "记录不存在或已删除"); }
}

@RestControllerAdvice
class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException ex) { return ResponseEntity.status(ex.status).body(Map.of("message", ex.getMessage())); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getDefaultMessage()).orElse("请求参数不正确");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge() { return ResponseEntity.status(413).body(Map.of("message", "文件不能超过 10 MB")); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate() { return ResponseEntity.status(409).body(Map.of("message", "资料重复或记录已变更，请刷新后重试")); }

    @ExceptionHandler({HttpMessageNotReadableException.class, org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed(Exception ex) { return ResponseEntity.badRequest().body(Map.of("message", "请求格式不正确或缺少必填参数")); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ex) {
        log.error("Request failed", ex);
        return ResponseEntity.internalServerError().body(Map.of("message", "服务处理失败，请稍后重试"));
    }
}
