package vn.omnismart.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ImageContentInspector {

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> WEBP_CHUNKS = Set.of("VP8 ", "VP8L", "VP8X");

    public InspectedImage inspect(InputStream input) throws IOException {
        byte[] content = input.readAllBytes();
        if (isPng(content)) {
            validateDimensions(content, "PNG");
            return new InspectedImage("image/png");
        }
        if (isJpeg(content)) {
            validateDimensions(content, "JPEG");
            return new InspectedImage("image/jpeg");
        }
        if (isWebP(content)) {
            return new InspectedImage("image/webp");
        }
        throw unsupportedImage();
    }

    private boolean isPng(byte[] content) {
        return content.length >= PNG_SIGNATURE.length
                && Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(content, PNG_SIGNATURE.length));
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 4
                && content[0] == (byte) 0xFF
                && content[1] == (byte) 0xD8
                && content[2] == (byte) 0xFF;
    }

    private boolean isWebP(byte[] content) {
        if (content.length < 20
                || !ascii(content, 0, "RIFF")
                || !ascii(content, 8, "WEBP")) {
            return false;
        }
        String chunk = new String(content, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        if (!WEBP_CHUNKS.contains(chunk)) {
            return false;
        }
        long declaredSize = Integer.toUnsignedLong(
                ByteBuffer.wrap(content, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        long chunkSize = Integer.toUnsignedLong(
                ByteBuffer.wrap(content, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        long firstChunkEnd = 20 + chunkSize + (chunkSize % 2);
        if (declaredSize + 8 != content.length || firstChunkEnd > content.length) {
            return false;
        }
        return switch (chunk) {
            case "VP8X" -> chunkSize == 10 && validDimensions(
                    1 + unsigned24(content, 24),
                    1 + unsigned24(content, 27));
            case "VP8L" -> chunkSize >= 5
                    && content[20] == 0x2F
                    && validDimensions(
                            1 + unsigned(content[21]) + ((unsigned(content[22]) & 0x3F) << 8),
                            1 + ((unsigned(content[22]) & 0xC0) >> 6)
                                    + (unsigned(content[23]) << 2)
                                    + ((unsigned(content[24]) & 0x0F) << 10));
            case "VP8 " -> chunkSize >= 10
                    && content[23] == (byte) 0x9D
                    && content[24] == 0x01
                    && content[25] == 0x2A
                    && validDimensions(
                            littleEndian16(content, 26) & 0x3FFF,
                            littleEndian16(content, 28) & 0x3FFF);
            default -> false;
        };
    }

    private int unsigned24(byte[] content, int offset) {
        return unsigned(content[offset])
                | (unsigned(content[offset + 1]) << 8)
                | (unsigned(content[offset + 2]) << 16);
    }

    private int littleEndian16(byte[] content, int offset) {
        return unsigned(content[offset]) | (unsigned(content[offset + 1]) << 8);
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private boolean validDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
            throw new ProductCatalogException(
                    HttpStatus.BAD_REQUEST,
                    "PRODUCT_MEDIA_DIMENSIONS_INVALID",
                    "Image dimensions are invalid or exceed 40 megapixels");
        }
        return true;
    }

    private boolean ascii(byte[] content, int offset, String expected) {
        byte[] expectedBytes = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return Arrays.equals(
                expectedBytes,
                Arrays.copyOfRange(content, offset, offset + expectedBytes.length));
    }

    private void validateDimensions(byte[] content, String expectedFormat) throws IOException {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw unsupportedImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                if (!reader.getFormatName().equalsIgnoreCase(expectedFormat)) {
                    throw unsupportedImage();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validDimensions(width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    private ProductCatalogException unsupportedImage() {
        return new ProductCatalogException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "PRODUCT_MEDIA_TYPE_UNSUPPORTED",
                "Only valid JPEG, PNG and WebP images are accepted");
    }

    public record InspectedImage(String contentType) {
    }
}
