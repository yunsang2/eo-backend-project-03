package com.example.chat.exception;

import com.example.chat.domain.ApiResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * 비즈니스 로직 예외 (400 Bad Request)
     * 예: 비밀번호 불일치, 가입되지 않은 이메일, 존재하지 않는 닉네임 등, 잘못된 플랜(모델) 요청
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Business Logic Error: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.fail(e.getMessage()));
    }

    /*
     * 상태 부적절 예외 (403 Forbidden)
     * 예: 토큰이 부족하여 AI API를 호출할 수 없을 때
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleIllegalStateException(IllegalStateException e) {
        log.warn("Illegal State Error: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDto.fail(e.getMessage()));
    }

    /*
     * 권한 부족 예외 (403 Forbidden)
     * 예: 다른사용자의 content 읽기 시도, 관리자 페이지 접근 등
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("Access Denied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDto.fail("접근 권한이 없습니다."));
    }

    /*
     * 인증 실패 예외 (401 Unauthorized)
     * 예: 시큐리티 인증 과정 중 아이디/비번 틀림
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("Authentication Failed: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.fail("로그인 정보가 올바르지 않습니다."));
    }

    /*
     * 시스템 전체 예외 (500 Internal Server Error)
     * 예상치 못한 런타임 에러 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Void>> handleGeneralException(Exception e) {
        // 서버 로그에는 상세 에러 남김
        log.error("Unexpected System Error: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.fail("서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    /*
     * DTO 유효성 검사 실패 예외 (400 Bad Request)
     * 예: @Valid 또는 @Validated에서 걸린 경우 (이메일 형식 오류, 빈 값 등)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation Error: {}", errorMessage);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.fail(errorMessage));
    }

    /*
     * 필수 파라미터 누락 예외 (400 Bad Request)
     * 예: @RequestParam으로 받아야 할 값을 프론트에서 안 보냈을 때
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMissingParameterException(MissingServletRequestParameterException e) {
        log.warn("Missing Parameter: {}", e.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.fail("필수 파라미터가 누락되었습니다: " + e.getParameterName()));
    }

    /*
     * 리소스를 찾을 수 없음 예외 (404 Not Found)
     * 예: DB에서 게시글이나 유저를 찾지 못했을 때 (findById 등에서 데이터가 없을 때)
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleNoSuchElementException(NoSuchElementException e) {
        log.warn("Resource Not Found: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponseDto.fail("요청하신 데이터를 찾을 수 없습니다."));
    }

    /*
     * 잘못된 API 주소 요청 (404 Not Found)
     * 예: 프론트엔드에서 아예 없는 URL로 API를 찔렀을 때
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("API URL Not Found: {}", e.getRequestURL());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponseDto.fail("잘못된 API 주소입니다. 요청 URL을 확인해주세요."));
    }

    /*
     * HTTP 메서드 불일치 (405 Method Not Allowed)
     * 예: POST로 보내야 하는 API를 GET으로 불렀을 때
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("Method Not Supported: {}", e.getMethod());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponseDto.fail("지원하지 않는 HTTP 메서드입니다. (요청: " + e.getMethod() + ")"));
    }

    /*
     * 데이터베이스 충돌 예외 (409 Conflict)
     * 예: 이미 존재하는 이메일이나 닉네임으로 가입 시도 (Unique 제약조건 위배)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("Data Integrity Violation: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponseDto.fail("이미 존재하는 데이터이거나 데이터베이스 제약조건을 위반했습니다."));
    }
}