package com.stdntedu.common.api;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int pageSize, long total, long totalPages) {
    public static <T> PageResult<T> of(List<T> items, int page, int pageSize, long total) {
        long pages = pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResult<>(items, page, pageSize, total, pages);
    }
}
