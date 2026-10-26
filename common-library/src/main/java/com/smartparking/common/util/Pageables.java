package com.smartparking.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Pageables {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 500;
    private static final int MAX_PAGE = 10_000;

    private Pageables() {
    }

    public static Pageable sanitize(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_SIZE);
        }
        int page = pageable.getPageNumber();
        if (page < 0 || page > MAX_PAGE) {
            page = 0;
        }
        int size = pageable.getPageSize();
        if (size < 1) {
            size = DEFAULT_SIZE;
        } else if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }
        Sort sort = pageable.getSort();
        return sort.isSorted() ? PageRequest.of(page, size, sort) : PageRequest.of(page, size);
    }
}
