package com.backend.Sstorage.inventory.alert.domain;

/**
 * Trang thai xu ly canh bao ton kho.
 *
 * Phai khop CHECK constraint:
 * inventory_alerts_status_check
 */
public enum InventoryAlertStatus {

    /**
     * Canh bao moi duoc tao.
     */
    NEW,

    /**
     * Quan ly da tiep nhan canh bao.
     */
    ACKNOWLEDGED,

    /**
     * Canh bao da duoc xu ly.
     */
    RESOLVED
}