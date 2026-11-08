
package com.backend.Sstorage.inventory.alert.domain;

/**
 * Loai canh bao ton kho.
 *
 * Phai khop CHECK constraint:
 * inventory_alerts_alert_type_check
 */
public enum InventoryAlertType {

    /**
     * Ton kho thap hon muc toi thieu.
     */
    LOW_STOCK,

    /**
     * Ton kho vuot muc toi da.
     */
    OVERSTOCK
}
