package com.shubh.movietickets.theaters.service;

import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.theaters.dto.*;
import com.shubh.movietickets.theaters.entity.Screen;
import com.shubh.movietickets.theaters.entity.SeatLayout;
import com.shubh.movietickets.theaters.entity.Theater;
import com.shubh.movietickets.theaters.repository.ScreenRepository;
import com.shubh.movietickets.theaters.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TheaterService {

    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;

    @Transactional
    public TheaterDto createTheater(CreateTheaterRequest request) {
        Theater theater = Theater.builder()
                .name(request.getName())
                .city(request.getCity())
                .address(request.getAddress())
                .build();

        for (CreateScreenRequest screenReq : request.getScreens()) {
            Screen screen = Screen.builder()
                    .name(screenReq.getName())
                    .theater(theater)
                    .build();

            int totalSeats = 0;
            for (SeatLayoutRequest layoutReq : screenReq.getSeatLayouts()) {
                SeatLayout layout = SeatLayout.builder()
                        .screen(screen)
                        .category(layoutReq.getCategory())
                        .rowPrefix(layoutReq.getRowPrefix())
                        .rowCount(layoutReq.getRowCount())
                        .seatsPerRow(layoutReq.getSeatsPerRow())
                        .price(layoutReq.getPrice())
                        .build();
                screen.getSeatLayouts().add(layout);
                totalSeats += layoutReq.getRowCount() * layoutReq.getSeatsPerRow();
            }
            screen.setTotalSeats(totalSeats);
            theater.getScreens().add(screen);
        }

        theater = theaterRepository.save(theater);
        return toDto(theater);
    }

    public List<TheaterDto> getAllTheaters() {
        return theaterRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public List<TheaterDto> getTheatersByCity(String city) {
        return theaterRepository.findByCity(city).stream()
                .map(this::toDto)
                .toList();
    }

    public TheaterDto getTheater(UUID id) {
        Theater theater = theaterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theater", "id", id));
        return toDto(theater);
    }

    public ScreenDto getScreen(UUID screenId) {
        Screen screen = screenRepository.findById(screenId)
                .orElseThrow(() -> new ResourceNotFoundException("Screen", "id", screenId));
        return toScreenDto(screen);
    }

    private TheaterDto toDto(Theater theater) {
        return TheaterDto.builder()
                .id(theater.getId())
                .name(theater.getName())
                .city(theater.getCity())
                .address(theater.getAddress())
                .screens(theater.getScreens().stream()
                        .map(this::toScreenDto)
                        .toList())
                .build();
    }

    private ScreenDto toScreenDto(Screen screen) {
        return ScreenDto.builder()
                .id(screen.getId())
                .name(screen.getName())
                .totalSeats(screen.getTotalSeats())
                .seatLayouts(screen.getSeatLayouts().stream()
                        .map(this::toLayoutDto)
                        .toList())
                .build();
    }

    private SeatLayoutDto toLayoutDto(SeatLayout layout) {
        return SeatLayoutDto.builder()
                .id(layout.getId())
                .category(layout.getCategory())
                .rowPrefix(layout.getRowPrefix())
                .rowCount(layout.getRowCount())
                .seatsPerRow(layout.getSeatsPerRow())
                .price(layout.getPrice())
                .build();
    }
}
