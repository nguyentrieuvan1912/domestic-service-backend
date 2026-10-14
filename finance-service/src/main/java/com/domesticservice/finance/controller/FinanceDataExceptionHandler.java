package com.domesticservice.finance.controller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackages="com.domesticservice.finance")
public class FinanceDataExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)public ProblemDetail conflict(DataIntegrityViolationException ex){return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Dữ liệu tài chính xung đột hoặc không thỏa điều kiện. Vui lòng kiểm tra lại.");}
}
