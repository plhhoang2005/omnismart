package vn.omnismart.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import vn.omnismart.catalog.storage.MediaStorage;

class ProductMediaCleanupJobTests {

    @Test
    void cleanupRemovesTrackedTemporaryAndUnregisteredStoredObjectsInBoundedBatch() throws Exception {
        ProductMediaRepository repository = mock(ProductMediaRepository.class);
        MediaStorage storage = mock(MediaStorage.class);
        ProductMedia temporary = new ProductMedia(UUID.randomUUID(), UUID.randomUUID(), "unused-key");
        when(repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        any(), any(), any(Pageable.class)))
                .thenReturn(List.of(temporary));
        when(storage.findObjectsOlderThan(any(Instant.class), anyInt()))
                .thenReturn(List.of(new MediaStorage.StoredObject("orphan-key", Instant.EPOCH)));
        when(repository.existsByObjectKey("orphan-key")).thenReturn(false);

        new ProductMediaCleanupJob(repository, storage, Duration.ofHours(1), 10).cleanup();

        verify(storage).deleteTemporary(temporary.getId());
        verify(repository).delete(temporary);
        verify(storage).deleteTemporaryOlderThan(any(Instant.class), org.mockito.ArgumentMatchers.eq(10));
        verify(storage).deleteObject("orphan-key");
    }
}
