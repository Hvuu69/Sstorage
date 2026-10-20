package com.backend.Sstorage.common.web;

import com.backend.Sstorage.common.error.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class PageRequestFactory {
    private PageRequestFactory() {
    }

    public static PageRequest create(int page, int size, String sort, Set<String> allowedSorts) {
        if (page < 0) {
            throw new InvalidRequestException("INVALID_PAGE", "page phải lớn hơn hoặc bằng 0.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("INVALID_PAGE_SIZE", "size phải nằm trong khoảng 1 đến 100.");
        }

        String[] parts = (sort == null || sort.isBlank() ? "name,asc" : sort.trim()).split(",", -1);
        if (parts.length != 2 || !allowedSorts.contains(parts[0])) {
            throw new InvalidRequestException("INVALID_SORT", "Trường sắp xếp không hợp lệ.");
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("INVALID_SORT", "Chiều sắp xếp phải là asc hoặc desc.");
        }
        return PageRequest.of(page, size, Sort.by(direction, parts[0]));
    }
}
