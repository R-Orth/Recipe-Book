package com.chefit.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public record ErrorResponse(String error, List<String> details) {
    public ErrorResponse(String error) { this(error, null); }
}
