package vn.omnismart.store;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import vn.omnismart.common.api.ApiException;

@Component
public class StoreOperationGuard {

    private final StoreRepository storeRepository;

    public StoreOperationGuard(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public Store requireOperational(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "Store not found"));
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "STORE_ARCHIVED",
                    "Archived stores cannot be changed");
        }
        if (!store.isOnboardingCompleted()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "STORE_ONBOARDING_REQUIRED",
                    "Confirm the store details before using business operations");
        }
        return store;
    }
}
