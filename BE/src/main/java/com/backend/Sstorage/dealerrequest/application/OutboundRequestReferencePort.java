package com.backend.Sstorage.dealerrequest.application;

import java.util.Set;
import java.util.UUID;

public interface OutboundRequestReferencePort {
    Set<UUID> activeOutboundRequestIds();
}
