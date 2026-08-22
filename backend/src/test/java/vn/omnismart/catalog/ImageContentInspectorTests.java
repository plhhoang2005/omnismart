package vn.omnismart.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class ImageContentInspectorTests {

    private final ImageContentInspector inspector = new ImageContentInspector();

    @Test
    void detectsJpegByDecodedContentRatherThanFilenameOrClaimedMime() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);

        assertThat(inspector.inspect(new ByteArrayInputStream(output.toByteArray())).contentType())
                .isEqualTo("image/jpeg");
    }

    @Test
    void acceptsStructurallyValidWebpContainerAndRejectsSpoofedHeader() throws Exception {
        byte[] webp = new byte[30];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, webp, 0, 4);
        ByteBuffer.wrap(webp, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(22);
        System.arraycopy("WEBPVP8X".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, webp, 8, 8);
        ByteBuffer.wrap(webp, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(10);

        assertThat(inspector.inspect(new ByteArrayInputStream(webp)).contentType())
                .isEqualTo("image/webp");

        webp[4] = 0;
        assertThatThrownBy(() -> inspector.inspect(new ByteArrayInputStream(webp)))
                .isInstanceOf(ProductCatalogException.class)
                .extracting("code")
                .isEqualTo("PRODUCT_MEDIA_TYPE_UNSUPPORTED");
    }
}
