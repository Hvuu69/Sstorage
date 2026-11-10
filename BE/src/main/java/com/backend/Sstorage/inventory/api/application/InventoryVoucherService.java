package com.backend.Sstorage.inventory.api.application;

import com.backend.Sstorage.product.domain.InventoryBalance;
import com.backend.Sstorage.inventory.api.domain.*;
import com.backend.Sstorage.inventory.api.dto.VoucherLineRequest;
import com.backend.Sstorage.inventory.api.infrastructure.InventoryVoucherRepository;
import com.backend.Sstorage.product.infrastructure.InventoryBalanceRepository;
import com.backend.Sstorage.product.infrastructure.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class InventoryVoucherService {

    private final InventoryVoucherRepository voucherRepository;
    private final ProductRepository productRepository;
    private final InventoryBalanceRepository balanceRepository;

    public InventoryVoucherService(InventoryVoucherRepository voucherRepository,
                                   ProductRepository productRepository,
                                   InventoryBalanceRepository balanceRepository) {
        this.voucherRepository = voucherRepository;
        this.productRepository = productRepository;
        this.balanceRepository = balanceRepository;
    }

    /**
     * Lấy danh sách phiếu theo loại (INBOUND / OUTBOUND) có phân trang.
     */
    public Page<InventoryVoucher> listVouchers(VoucherType type, Pageable pageable) {
        return voucherRepository.findByType(type, pageable);
    }

    @Transactional
    public InventoryVoucher createVoucher(VoucherType type, String partnerName, String note, List<VoucherLineRequest> linesDto) {
        String prefix = (type == VoucherType.INBOUND) ? "PN-" : "PX-";
        String code = prefix + System.currentTimeMillis();

        InventoryVoucher voucher = new InventoryVoucher(UUID.randomUUID(), code, type, partnerName, note);

        for (VoucherLineRequest dto : linesDto) {
            var product = productRepository.findById(dto.productId())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));
            voucher.addLine(new VoucherLine(UUID.randomUUID(), product, dto.quantity()));
        }
        return voucherRepository.save(voucher);
    }

    @Transactional
    public void completeVoucher(UUID voucherId) {
        InventoryVoucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu"));

        voucher.complete();

        for (VoucherLine line : voucher.getLines()) {
            InventoryBalance balance = balanceRepository.findById(line.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tồn kho"));

            if (voucher.getType() == VoucherType.INBOUND) {
                balance.add(line.getQuantity());
            } else {
                if (balance.getQuantityOnHand() < line.getQuantity()) {
                    throw new RuntimeException("Không đủ tồn kho để xuất");
                }
                balance.subtract(line.getQuantity());
            }
            balanceRepository.save(balance);
        }
        voucherRepository.save(voucher);
    }

    /**
     * Hủy phiếu — chỉ cho phép hủy khi phiếu chưa hoàn tất.
     * Tồn kho KHÔNG bị thay đổi vì phiếu chưa complete.
     */
    @Transactional
    public void cancelVoucher(UUID voucherId) {
        InventoryVoucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu"));

        if (voucher.isCompleted()) {
            throw new RuntimeException("Không thể hủy phiếu đã hoàn tất");
        }

        voucher.cancel();
        voucherRepository.save(voucher);
    }
}