package com.backend.Sstorage.security.user.application;

import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.InvalidRequestException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;
import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.Role;
import com.backend.Sstorage.security.user.UserRepository;
import com.backend.Sstorage.security.user.UserRole;
import com.backend.Sstorage.security.user.UserRoleRepository;
import com.backend.Sstorage.security.user.UserStatus;
import com.backend.Sstorage.security.user.api.dto.UserAccountResponse;
import com.backend.Sstorage.security.user.api.dto.UserCreateRequest;
import com.backend.Sstorage.security.user.api.dto.UserStatusRequest;
import com.backend.Sstorage.security.user.api.dto.UserUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAdminService {
    private final UserRepository userRepository;
    private final UserRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public UserAdminService(UserRepository userRepository, UserRoleRepository roleRepository,
                            PasswordEncoder passwordEncoder, AuditPort auditPort,
                            CurrentActorProvider actorProvider) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public Page<UserAccountResponse> list(String search, Role role, UserStatus status, Pageable pageable) {
        Specification<AppUser> specification = (root, query, builder) -> builder.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("fullName")), pattern),
                    builder.like(builder.lower(root.get("email")), pattern)));
        }
        if (role != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("role").get("code"), role));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("status"), status));
        }
        return userRepository.findAll(specification, pageable).map(UserAccountResponse::from);
    }

    @Transactional(readOnly = true)
    public UserAccountResponse get(UUID id) {
        return UserAccountResponse.from(findUser(id));
    }

    @Transactional
    public UserAccountResponse create(UserCreateRequest request) {
        String email = normalizeEmail(request.email());
        ensureUniqueEmail(email, null);
        UserRole role = findRole(request.role());
        UUID dealerId = dealerIdFor(request.role(), request.dealerId());
        AppUser user = userRepository.saveAndFlush(new AppUser(role, dealerId,
                request.fullName().trim(), email, passwordEncoder.encode(request.password()), UserStatus.ACTIVE));
        audit("USER_CREATED", user, null, snapshot(user));
        return UserAccountResponse.from(user);
    }

    @Transactional
    public UserAccountResponse update(UUID id, UserUpdateRequest request) {
        AppUser user = findUser(id);
        Map<String, Object> oldValues = snapshot(user);
        String email = normalizeEmail(request.email());
        if (!email.equalsIgnoreCase(user.getUsername())) ensureUniqueEmail(email, id);

        if (isCurrentActor(user) && !email.equalsIgnoreCase(user.getUsername())) {
            throw new BusinessConflictException("USER_CANNOT_CHANGE_OWN_EMAIL",
                    "Bạn không thể tự đổi email đăng nhập của tài khoản đang sử dụng.");
        }
        if (isCurrentActor(user) && user.getRole().getCode() == Role.ADMIN && request.role() != Role.ADMIN) {
            throw new BusinessConflictException("USER_CANNOT_DEMOTE_SELF",
                    "Bạn không thể tự gỡ quyền quản trị của tài khoản đang sử dụng.");
        }

        UserRole role = findRole(request.role());
        UUID dealerId = dealerIdFor(request.role(), request.dealerId());
        ensureActiveAdminRemains(user, request.role(), user.getStatus());
        user.updateAccount(role, dealerId, request.fullName().trim(), email);

        String newPassword = request.newPassword();
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 8) {
                throw new InvalidRequestException("PASSWORD_TOO_SHORT", "Mật khẩu mới phải có ít nhất 8 ký tự.");
            }
            user.changePassword(passwordEncoder.encode(newPassword));
        }
        userRepository.flush();
        audit("USER_UPDATED", user, oldValues, snapshot(user));
        if (newPassword != null && !newPassword.isBlank()) {
            audit("USER_PASSWORD_RESET", user, null, Map.of("passwordChanged", true));
        }
        return UserAccountResponse.from(user);
    }

    @Transactional
    public UserAccountResponse changeStatus(UUID id, UserStatusRequest request) {
        AppUser user = findUser(id);
        UserStatus nextStatus = request.status();
        if (user.getStatus() == nextStatus) return UserAccountResponse.from(user);
        if (isCurrentActor(user)) {
            throw new BusinessConflictException("USER_CANNOT_CHANGE_OWN_STATUS",
                    "Bạn không thể khóa hoặc vô hiệu hóa tài khoản đang sử dụng.");
        }
        ensureActiveAdminRemains(user, user.getRole().getCode(), nextStatus);

        Map<String, Object> oldValues = snapshot(user);
        user.changeStatus(nextStatus);
        userRepository.flush();
        audit("USER_STATUS_CHANGED", user, oldValues, snapshot(user));
        return UserAccountResponse.from(user);
    }

    private AppUser findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy tài khoản " + id + "."));
    }

    private UserRole findRole(Role code) {
        return roleRepository.findByCode(code).orElseThrow(() ->
                new InvalidRequestException("ROLE_NOT_CONFIGURED", "Vai trò chưa được cấu hình trong hệ thống."));
    }

    private UUID dealerIdFor(Role role, UUID dealerId) {
        if (role == Role.DEALER && dealerId == null) {
            throw new InvalidRequestException("DEALER_ID_REQUIRED", "Tài khoản đại lý phải được liên kết với mã đại lý.");
        }
        return role == Role.DEALER ? dealerId : null;
    }

    private void ensureUniqueEmail(String email, UUID currentId) {
        boolean exists = currentId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, currentId);
        if (exists) {
            throw new BusinessConflictException("USER_EMAIL_EXISTS", "Email đã được sử dụng bởi tài khoản khác.");
        }
    }

    private void ensureActiveAdminRemains(AppUser user, Role nextRole, UserStatus nextStatus) {
        boolean activeAdmin = user.getRole().getCode() == Role.ADMIN && user.getStatus() == UserStatus.ACTIVE;
        boolean remainsActiveAdmin = nextRole == Role.ADMIN && nextStatus == UserStatus.ACTIVE;
        if (activeAdmin && !remainsActiveAdmin
                && userRepository.countByRole_CodeAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BusinessConflictException("LAST_ACTIVE_ADMIN", "Không thể thay đổi tài khoản Admin đang hoạt động cuối cùng.");
        }
    }

    private boolean isCurrentActor(AppUser user) {
        return user.getUsername().equalsIgnoreCase(actorProvider.currentActor());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> snapshot(AppUser user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", user.getFullName());
        values.put("email", user.getUsername());
        values.put("role", user.getRole().getCode());
        values.put("status", user.getStatus());
        values.put("dealerId", user.getDealerId());
        return values;
    }

    private void audit(String action, AppUser user, Map<String, Object> oldValues,
                       Map<String, Object> newValues) {
        auditPort.record(new AuditEvent(action, "USER_ACCOUNT", user.getId(),
                actorProvider.currentActor(), oldValues, newValues));
    }
}
