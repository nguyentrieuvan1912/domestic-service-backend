package com.domesticservice.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(ApiExceptionHandler.class)
public class CommonWebConfiguration {
}
