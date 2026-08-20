package vn.omnismart.catalog;

import java.util.UUID;

public interface ProductPublicationGuard {
    boolean hasActivePublishingJobs(UUID storeId, UUID productId);
}
