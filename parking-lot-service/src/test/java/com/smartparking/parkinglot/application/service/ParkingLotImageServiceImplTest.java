package com.smartparking.parkinglot.application.service;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.parkinglot.application.dto.ParkingLotImageResponse;
import com.smartparking.parkinglot.application.service.impl.ParkingLotImageServiceImpl;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import com.smartparking.parkinglot.domain.model.ParkingLotImage;
import com.smartparking.parkinglot.domain.repository.ParkingLotImageRepository;
import com.smartparking.parkinglot.domain.repository.ParkingLotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingLotImageServiceImplTest {

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @Mock
    private ParkingLotImageRepository imageRepository;

    @TempDir
    Path tempDir;

    private ParkingLotImageServiceImpl imageService;

    private final Long merchantUserId = 100L;
    private final Long parkingLotId = 20L;

    @BeforeEach
    void setUp() {
        imageService = new ParkingLotImageServiceImpl(
                parkingLotRepository,
                imageRepository,
                tempDir.toString()
        );
    }

    @Test
    @DisplayName("Upload fails when files list is empty")
    void testUploadEmptyFiles() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).build();
        when(parkingLotRepository.findByIdForUpdate(parkingLotId)).thenReturn(Optional.of(lot));

        AppException ex = assertThrows(AppException.class, () ->
                imageService.upload(merchantUserId, parkingLotId, Collections.emptyList()));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Upload fails when lot already reached maximum 5 images")
    void testUploadExceedsMaxImages() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).build();
        when(parkingLotRepository.findByIdForUpdate(parkingLotId)).thenReturn(Optional.of(lot));
        when(imageRepository.countByParkingLotId(parkingLotId)).thenReturn(5L);

        MockMultipartFile file = new MockMultipartFile("files", "test.jpg", "image/jpeg", "image".getBytes());

        AppException ex = assertThrows(AppException.class, () ->
                imageService.upload(merchantUserId, parkingLotId, List.of(file)));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("tối đa 5 ảnh"));
    }

    @Test
    @DisplayName("Upload fails when file extension is not allowed")
    void testUploadInvalidExtension() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).build();
        when(parkingLotRepository.findByIdForUpdate(parkingLotId)).thenReturn(Optional.of(lot));
        when(imageRepository.countByParkingLotId(parkingLotId)).thenReturn(0L);

        MockMultipartFile file = new MockMultipartFile("files", "script.exe", "application/octet-stream", "bad".getBytes());

        AppException ex = assertThrows(AppException.class, () ->
                imageService.upload(merchantUserId, parkingLotId, List.of(file)));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Chỉ chấp nhận file ảnh"));
    }

    @Test
    @DisplayName("Upload successfully saves file and image entity")
    void testUploadSuccess() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).build();
        when(parkingLotRepository.findByIdForUpdate(parkingLotId)).thenReturn(Optional.of(lot));
        when(imageRepository.countByParkingLotId(parkingLotId)).thenReturn(0L);

        ParkingLotImage saved = ParkingLotImage.builder()
                .id(1L)
                .parkingLot(lot)
                .storedFileName("stored.jpg")
                .originalFileName("car.jpg")
                .cover(true)
                .build();
        when(imageRepository.saveAndFlush(any(ParkingLotImage.class))).thenReturn(saved);

        MockMultipartFile file = new MockMultipartFile("files", "car.jpg", "image/jpeg", "content".getBytes());

        List<ParkingLotImageResponse> responses = imageService.upload(merchantUserId, parkingLotId, List.of(file));

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertTrue(responses.get(0).isCover());
        verify(imageRepository, times(1)).saveAndFlush(any(ParkingLotImage.class));
    }

    @Test
    @DisplayName("Delete image removes file and sets another cover if deleted was cover")
    void testDeleteImage() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).build();
        when(parkingLotRepository.findByIdForUpdate(parkingLotId)).thenReturn(Optional.of(lot));

        ParkingLotImage img1 = ParkingLotImage.builder()
                .id(1L)
                .parkingLot(lot)
                .storedFileName("cover.jpg")
                .cover(true)
                .build();
        when(imageRepository.findByIdAndParkingLotId(1L, parkingLotId)).thenReturn(Optional.of(img1));

        ParkingLotImage img2 = ParkingLotImage.builder()
                .id(2L)
                .parkingLot(lot)
                .storedFileName("second.jpg")
                .cover(false)
                .build();
        when(imageRepository.findFirstByParkingLotIdOrderByCreatedAtAsc(parkingLotId))
                .thenReturn(Optional.of(img2));

        imageService.delete(merchantUserId, parkingLotId, 1L);

        verify(imageRepository, times(1)).delete(img1);
        assertTrue(img2.isCover());
        verify(imageRepository, times(1)).save(img2);
    }

    @Test
    @DisplayName("getImages returns list of images")
    void testGetImages() {
        when(parkingLotRepository.existsById(parkingLotId)).thenReturn(true);
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).build();
        ParkingLotImage img = ParkingLotImage.builder()
                .id(1L)
                .parkingLot(lot)
                .storedFileName("img.jpg")
                .originalFileName("orig.jpg")
                .cover(true)
                .build();
        when(imageRepository.findByParkingLotIdOrderByCoverDescCreatedAtAsc(parkingLotId)).thenReturn(List.of(img));

        List<ParkingLotImageResponse> res = imageService.getImages(parkingLotId);
        assertEquals(1, res.size());
        assertEquals("orig.jpg", res.get(0).getOriginalFileName());
    }

    @Test
    @DisplayName("getContent returns StoredImage when file exists")
    void testGetContent() throws IOException {
        Path file = tempDir.resolve("content.jpg");
        Files.writeString(file, "hello");

        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).build();
        ParkingLotImage img = ParkingLotImage.builder()
                .id(1L)
                .parkingLot(lot)
                .storedFileName("content.jpg")
                .originalFileName("orig.jpg")
                .contentType("image/jpeg")
                .build();
        when(imageRepository.findByIdAndParkingLotId(1L, parkingLotId)).thenReturn(Optional.of(img));

        var stored = imageService.getContent(parkingLotId, 1L);
        assertNotNull(stored);
        assertEquals("image/jpeg", stored.contentType());
    }

    @Test
    @DisplayName("setCover marks target image as cover")
    void testSetCover() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).build();
        when(parkingLotRepository.findByIdForUpdate(parkingLotId)).thenReturn(Optional.of(lot));

        ParkingLotImage img1 = ParkingLotImage.builder().id(1L).parkingLot(lot).cover(false).build();
        ParkingLotImage img2 = ParkingLotImage.builder().id(2L).parkingLot(lot).cover(true).build();

        when(imageRepository.findByIdAndParkingLotId(1L, parkingLotId)).thenReturn(Optional.of(img1));
        when(imageRepository.findByParkingLotIdOrderByCoverDescCreatedAtAsc(parkingLotId)).thenReturn(List.of(img1, img2));

        var response = imageService.setCover(merchantUserId, parkingLotId, 1L);
        assertNotNull(response);
        assertTrue(img1.isCover());
        assertFalse(img2.isCover());
        verify(imageRepository, times(1)).saveAll(any());
    }
}
