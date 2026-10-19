package com.domesticservice.catalog.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = "com.domesticservice.catalog")
public class CatalogDataExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Tên hoặc mã đã được sử dụng, hoặc dữ liệu không thỏa điều kiện. Vui lòng kiểm tra lại.");
    }
}
