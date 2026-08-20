package vn.omnismart.catalog.storage;

import java.io.IOException;

public class FileSizeLimitExceededException extends IOException {

    public FileSizeLimitExceededException(long maximumBytes) {
        super("File exceeds the maximum size of " + maximumBytes + " bytes");
    }
}
