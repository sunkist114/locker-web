package com.cse.locker.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 프론트(student.html/admin.html)는 실패 시 response.text()를 그대로 alert로 띄우는 구조라
 * 예외를 "짧은 텍스트"로 내려주면 UI 수정 없이도 사용자에게 원인을 보여줄 수 있다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException e) {
        // 중복 신청, 상태 불일치 등은 클라이언트 입력/상태 문제이므로 400으로 통일
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
