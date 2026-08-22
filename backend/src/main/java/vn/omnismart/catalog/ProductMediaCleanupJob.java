package vn.omnismart.catalog;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.omnismart.catalog.storage.MediaStorage;

@Component
public class ProductMediaCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductMediaCleanupJob.class);

    private final ProductMediaRepository mediaRepository;
    private final MediaStorage storage;
    private final Duration retention;
    private final int batchSize;

    public ProductMediaCleanupJob(
            ProductMediaRepository mediaRepository,
            MediaStorage storage,
            @Value("${omnismart.product-media.temporary-retention:PT24H}") Duration retention,
            @Value("${omnismart.product-media.cleanup-batch-size:100}") int batchSize) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Temporary media retention must be positive");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Media cleanup batch size must be between 1 and 1000");
        }
        this.mediaRepository = mediaRepository;
        this.storage = storage;
        this.retention = retention;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${omnismart.product-media.cleanup-interval:PT1H}",
            initialDelayString = "${omnismart.product-media.cleanup-initial-delay:PT1H}")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retention);
        cleanupTrackedTemporaryMedia(cutoff);
        cleanupDanglingTemporaryFiles(cutoff);
        cleanupOrphanedObjects(cutoff);
    }

    private void cleanupTrackedTemporaryMedia(Instant cutoff) {
        List<ProductMedia> stale = mediaRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                ProductMediaStatus.TEMPORARY,
                OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC),
                PageRequest.of(0, batchSize));
        for (ProductMedia media : stale) {
            try {
                storage.deleteTemporary(media.getId());
                mediaRepository.delete(media);
            } catch (IOException exception) {
                LOGGER.warn("Could not clean temporary media {}", media.getId(), exception);
            }
        }
    }

    private void cleanupDanglingTemporaryFiles(Instant cutoff) {
        try {
            storage.deleteTemporaryOlderThan(cutoff, batchSize);
        } catch (IOException exception) {
            LOGGER.warn("Could not scan dangling temporary media", exception);
        }
    }

    private void cleanupOrphanedObjects(Instant cutoff) {
        try {
            for (MediaStorage.StoredObject object : storage.findObjectsOlderThan(cutoff, batchSize)) {
                if (!mediaRepository.existsByObjectKey(object.objectKey())) {
                    storage.deleteObject(object.objectKey());
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not scan orphaned product media", exception);
        }
    }
}
