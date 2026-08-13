package com.stdntedu.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageQuery(@Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
    public PageQuery {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
