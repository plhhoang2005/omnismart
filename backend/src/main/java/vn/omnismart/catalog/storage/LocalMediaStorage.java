package vn.omnismart.catalog.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalMediaStorage implements MediaStorage {

    private final Path temporaryRoot;
    private final Path objectRoot;

    public LocalMediaStorage(
            @Value("${omnismart.product-media.storage-root:./data/product-media}") String storageRoot)
            throws IOException {
        Path root = Path.of(storageRoot).toAbsolutePath().normalize();
        this.temporaryRoot = root.resolve("temporary");
        this.objectRoot = root.resolve("objects");
        Files.createDirectories(temporaryRoot);
        Files.createDirectories(objectRoot);
    }

    @Override
    public long storeTemporary(UUID uploadId, InputStream input, long maximumBytes) throws IOException {
        Path target = temporaryPath(uploadId);
        long total = 0;
        try (InputStream source = input;
                OutputStream output = Files.newOutputStream(
                        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw new FileSizeLimitExceededException(maximumBytes);
                }
                output.write(buffer, 0, read);
            }
            return total;
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    @Override
    public InputStream openTemporary(UUID uploadId) throws IOException {
        return Files.newInputStream(temporaryPath(uploadId));
    }

    @Override
    public void promote(UUID uploadId, String objectKey) throws IOException {
        Path source = temporaryPath(uploadId);
        Path destination = objectPath(objectKey);
        Files.createDirectories(destination.getParent());
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    @Override
    public InputStream openObject(String objectKey) throws IOException {
        return Files.newInputStream(objectPath(objectKey));
    }

    @Override
    public void deleteTemporary(UUID uploadId) throws IOException {
        Files.deleteIfExists(temporaryPath(uploadId));
    }

    @Override
    public void deleteObject(String objectKey) throws IOException {
        Files.deleteIfExists(objectPath(objectKey));
    }

    @Override
    public int deleteTemporaryOlderThan(Instant cutoff, int maximumDeletes) throws IOException {
        int deleted = 0;
        try (Stream<Path> files = Files.list(temporaryRoot)) {
            for (Path path : files.sorted(Comparator.comparing(Path::toString)).toList()) {
                if (deleted >= maximumDeletes) {
                    break;
                }
                if (Files.isRegularFile(path)
                        && Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)
                        && Files.deleteIfExists(path)) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    @Override
    public List<StoredObject> findObjectsOlderThan(Instant cutoff, int limit) throws IOException {
        try (Stream<Path> files = Files.walk(objectRoot)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> lastModified(path).isBefore(cutoff))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(limit)
                    .map(path -> new StoredObject(
                            objectRoot.relativize(path).toString().replace('\\', '/'),
                            lastModified(path)))
                    .toList();
        }
    }

    private Path temporaryPath(UUID uploadId) {
        return temporaryRoot.resolve(uploadId + ".upload");
    }

    private Path objectPath(String objectKey) {
        Path resolved = objectRoot.resolve(objectKey).normalize();
        if (!resolved.startsWith(objectRoot) || resolved.equals(objectRoot)) {
            throw new IllegalArgumentException("Invalid media object key");
        }
        return resolved;
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read media modification time", exception);
        }
    }
}
