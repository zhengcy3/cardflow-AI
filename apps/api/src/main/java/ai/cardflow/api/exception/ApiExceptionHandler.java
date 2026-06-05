package ai.cardflow.api.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API 异常统一处理器。
 *
 * <p>把常见异常转换为稳定 JSON 错误响应，避免前端收到默认 HTML 错误页。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {
  /**
   * 查询不到资源时返回 404。
   *
   * @return 标准错误响应
   */
  @ExceptionHandler(EmptyResultDataAccessException.class)
  public ResponseEntity<Map<String, Object>> notFound() {
    return error(HttpStatus.NOT_FOUND, "Resource not found");
  }

  /**
   * 请求体验证失败时返回 400，并优先展示第一个字段错误。
   *
   * @param exception Spring Validation 异常
   * @return 标准错误响应
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream()
      .findFirst()
      .map(error -> error.getField() + " " + error.getDefaultMessage())
      .orElse("Validation failed");
    return error(HttpStatus.BAD_REQUEST, message);
  }

  /**
   * 业务状态异常返回 400。
   *
   * @param exception 业务抛出的状态异常
   * @return 标准错误响应
   */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, Object>> illegalState(IllegalStateException exception) {
    return error(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  /**
   * 构造统一错误响应体。
   *
   * @param status HTTP 状态码
   * @param message 错误消息
   * @return 包含 timestamp、status、error 和 message 的响应
   */
  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of(
      "timestamp", Instant.now().toString(),
      "status", status.value(),
      "error", status.getReasonPhrase(),
      "message", message
    ));
  }
}
