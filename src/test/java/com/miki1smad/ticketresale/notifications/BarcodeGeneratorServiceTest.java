package com.miki1smad.ticketresale.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BarcodeGeneratorServiceTest {

    private BarcodeGeneratorService barcodeGeneratorService;

    @BeforeEach
    void setUp() {
        barcodeGeneratorService = new BarcodeGeneratorService();
    }

    @Test
    void shouldGenerateAndDecodeCode128Barcode() throws Exception {
        String originalToken = "TKT_123e4567-e89b-12d3-a456-426614174000_abcdef123456";

        byte[] barcodeImage = barcodeGeneratorService.generateCode128BarcodeImage(originalToken, 500, 120);
        assertThat(barcodeImage).isNotEmpty();

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(ImageIO.read(new ByteArrayInputStream(barcodeImage)))));
        Result result = new MultiFormatReader().decode(bitmap);

        assertThat(result.getText()).isEqualTo(originalToken);
    }

    @Test
    void shouldGenerateAndDecodeQrCode() throws Exception {
        String originalToken = "TKT_123e4567-e89b-12d3-a456-426614174000_abcdef123456";

        byte[] qrCodeImage = barcodeGeneratorService.generateQrCodeImage(originalToken, 300, 300);
        assertThat(qrCodeImage).isNotEmpty();

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(ImageIO.read(new ByteArrayInputStream(qrCodeImage)))));
        Result result = new MultiFormatReader().decode(bitmap);

        assertThat(result.getText()).isEqualTo(originalToken);
    }
}
