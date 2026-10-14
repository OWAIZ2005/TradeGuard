package com.tradeguard.web;

/** Validated page/size parameters shared by every paginated endpoint. */
public record Paging(int page, int size) {

    public static final int MAX_SIZE = 200;

    public static Paging of(int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("BAD_REQUEST", "page must be 0 or greater.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw ApiException.badRequest("BAD_REQUEST", "size must be between 1 and " + MAX_SIZE + ".");
        }
        return new Paging(page, size);
    }

    public long offset() {
        return (long) page * size;
    }
}
