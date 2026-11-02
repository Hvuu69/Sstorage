package com.backend.Sstorage.identification.api;

import com.backend.Sstorage.identification.api.dto.CreateIdentifierRequest;
import com.backend.Sstorage.identification.api.dto.IdentifierLookupResponse;
import com.backend.Sstorage.identification.api.dto.IdentifierResponse;
import com.backend.Sstorage.identification.api.dto.IdentifierStatusRequest;
import com.backend.Sstorage.identification.api.dto.QrCodeResponse;
import com.backend.Sstorage.identification.application.IdentificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
public class IdentificationController {
    private final IdentificationService service;

    public IdentificationController(IdentificationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/identifiers/lookup")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public IdentifierLookupResponse lookup(
            @RequestParam @Size(max = 255, message = "Mã nhận diện không được vượt quá 255 ký tự.") String code) {
        return service.lookup(code);
    }

    @GetMapping("/api/v1/products/{productId}/identifiers")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public List<IdentifierResponse> list(@PathVariable UUID productId) {
        return service.list(productId);
    }

    @PostMapping("/api/v1/products/{productId}/identifiers")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ResponseEntity<IdentifierResponse> create(@PathVariable UUID productId,
                                                      @Valid @RequestBody CreateIdentifierRequest request) {
        IdentifierResponse response = service.create(productId, request);
        return ResponseEntity.created(URI.create("/api/v1/identifiers/" + response.id())).body(response);
    }

    @PatchMapping("/api/v1/identifiers/{id}/status")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public IdentifierResponse changeStatus(@PathVariable UUID id,
                                            @Valid @RequestBody IdentifierStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping("/api/v1/products/{productId}/qr-code")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public QrCodeResponse qrCode(@PathVariable UUID productId,
                                 @RequestParam(required = false) UUID identifierId) {
        return service.qrCode(productId, identifierId);
    }
}
