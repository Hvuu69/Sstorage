package com.backend.Sstorage.security.user.api;

import com.backend.Sstorage.common.web.PageRequestFactory;
import com.backend.Sstorage.common.web.PageResponse;
import com.backend.Sstorage.security.user.Role;
import com.backend.Sstorage.security.user.UserStatus;
import com.backend.Sstorage.security.user.api.dto.UserAccountResponse;
import com.backend.Sstorage.security.user.api.dto.UserCreateRequest;
import com.backend.Sstorage.security.user.api.dto.UserStatusRequest;
import com.backend.Sstorage.security.user.api.dto.UserUpdateRequest;
import com.backend.Sstorage.security.user.application.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
    private static final Set<String> ALLOWED_SORTS = Set.of("fullName", "email", "status");
    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UserAccountResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "fullName,asc") String sort) {
        return PageResponse.from(service.list(search, role, status,
                PageRequestFactory.create(page, size, sort, ALLOWED_SORTS)));
    }

    @GetMapping("/{id}")
    public UserAccountResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<UserAccountResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserAccountResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public UserAccountResponse update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserAccountResponse changeStatus(@PathVariable UUID id,
                                           @Valid @RequestBody UserStatusRequest request) {
        return service.changeStatus(id, request);
    }
}
