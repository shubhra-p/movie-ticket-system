package com.shubh.movietickets.theaters.service;

import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.theaters.dto.*;
import com.shubh.movietickets.theaters.entity.*;
import com.shubh.movietickets.theaters.repository.ScreenRepository;
import com.shubh.movietickets.theaters.repository.TheaterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TheaterServiceTest {

    @Mock
    private TheaterRepository theaterRepository;

    @Mock
    private ScreenRepository screenRepository;

    @InjectMocks
    private TheaterService theaterService;

    private Theater testTheater;
    private UUID theaterId;
    private UUID screenId;

    @BeforeEach
    void setUp() {
        theaterId = UUID.randomUUID();
        screenId = UUID.randomUUID();

        SeatLayout regularLayout = SeatLayout.builder()
                .id(UUID.randomUUID())
                .category(SeatCategory.REGULAR)
                .rowPrefix("A")
                .rowCount(5)
                .seatsPerRow(10)
                .price(BigDecimal.valueOf(200))
                .build();

        SeatLayout premiumLayout = SeatLayout.builder()
                .id(UUID.randomUUID())
                .category(SeatCategory.PREMIUM)
                .rowPrefix("F")
                .rowCount(2)
                .seatsPerRow(8)
                .price(BigDecimal.valueOf(400))
                .build();

        Screen screen = Screen.builder()
                .id(screenId)
                .name("Screen 1")
                .totalSeats(66)
                .seatLayouts(List.of(regularLayout, premiumLayout))
                .build();

        testTheater = Theater.builder()
                .id(theaterId)
                .name("PVR Cinemas")
                .city("Mumbai")
                .address("Phoenix Mall")
                .screens(List.of(screen))
                .build();
    }

    @Test
    @DisplayName("Create theater - calculates totalSeats correctly")
    void createTheater_success() {
        CreateTheaterRequest request = new CreateTheaterRequest(
                "PVR Cinemas", "Mumbai", "Phoenix Mall",
                List.of(new CreateScreenRequest("Screen 1", List.of(
                        new SeatLayoutRequest(SeatCategory.REGULAR, "A", 5, 10, BigDecimal.valueOf(200)),
                        new SeatLayoutRequest(SeatCategory.PREMIUM, "F", 2, 8, BigDecimal.valueOf(400))
                )))
        );

        when(theaterRepository.save(any(Theater.class))).thenReturn(testTheater);

        TheaterDto result = theaterService.createTheater(request);

        assertThat(result.getName()).isEqualTo("PVR Cinemas");
        assertThat(result.getCity()).isEqualTo("Mumbai");
        assertThat(result.getScreens()).hasSize(1);
        assertThat(result.getScreens().get(0).getTotalSeats()).isEqualTo(66);
        assertThat(result.getScreens().get(0).getSeatLayouts()).hasSize(2);
    }

    @Test
    @DisplayName("Get theater by ID - success")
    void getTheater_success() {
        when(theaterRepository.findById(theaterId)).thenReturn(Optional.of(testTheater));

        TheaterDto result = theaterService.getTheater(theaterId);

        assertThat(result.getId()).isEqualTo(theaterId);
        assertThat(result.getName()).isEqualTo("PVR Cinemas");
    }

    @Test
    @DisplayName("Get theater by ID - not found")
    void getTheater_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(theaterRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> theaterService.getTheater(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get theaters by city")
    void getTheatersByCity() {
        when(theaterRepository.findByCity("Mumbai")).thenReturn(List.of(testTheater));

        List<TheaterDto> results = theaterService.getTheatersByCity("Mumbai");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCity()).isEqualTo("Mumbai");
    }

    @Test
    @DisplayName("Get all theaters")
    void getAllTheaters() {
        when(theaterRepository.findAll()).thenReturn(List.of(testTheater));

        List<TheaterDto> results = theaterService.getAllTheaters();

        assertThat(results).hasSize(1);
    }
}
