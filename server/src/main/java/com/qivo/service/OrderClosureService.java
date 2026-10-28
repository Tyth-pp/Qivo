package com.qivo.service;

public interface OrderClosureService {
    /** userId 为空仅用于内部超时扫描；expiredOnly 决定是否必须已到期。 */
    boolean close(Long orderId, Long userId, boolean expiredOnly);
}
