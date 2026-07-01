package com.chefit.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.List;

// Uniform error body. `details` is populated for 400 validation responses; null/absent
// for 401 generic responses so they stay indistinguishable across failure causes.
@JsonInclude(Include.NON_NULL)
public record ErrorResponse(String error, List<String> details) {
    public ErrorResponse(String error) { this(error, null); }
}
