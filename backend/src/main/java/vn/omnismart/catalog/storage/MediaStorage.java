package vn.omnismart.catalog.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaStorage {

    long storeTemporary(UUID uploadId, InputStream input, long maximumBytes) throws IOException;

    InputStream openTemporary(UUID uploadId) throws IOException;

    void promote(UUID uploadId, String objectKey) throws IOException;

    InputStream openObject(String objectKey) throws IOException;

    void deleteTemporary(UUID uploadId) throws IOException;

    void deleteObject(String objectKey) throws IOException;

    int deleteTemporaryOlderThan(Instant cutoff, int maximumDeletes) throws IOException;

    List<StoredObject> findObjectsOlderThan(Instant cutoff, int limit) throws IOException;

    record StoredObject(String objectKey, Instant lastModified) {
    }
}
