package com.stdntedu.common.api;

import java.util.List;
import java.util.function.Function;

public final class PageMapper {
    private PageMapper() { }

    public static <S, T> PageResult<T> map(PageResult<S> source, Function<S, T> mapper) {
        return new PageResult<>(source.items().stream().map(mapper).toList(), source.page(), source.pageSize(),
                source.total(), source.totalPages());
    }
}
