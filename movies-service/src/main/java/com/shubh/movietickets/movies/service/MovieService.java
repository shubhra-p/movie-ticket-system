package com.shubh.movietickets.movies.service;

import com.shubh.movietickets.common.event.ScheduleCreatedEvent;
import com.shubh.movietickets.common.exception.ConflictException;
import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.movies.client.TheaterClient;
import com.shubh.movietickets.movies.dto.*;
import com.shubh.movietickets.movies.entity.Movie;
import com.shubh.movietickets.movies.entity.MovieSchedule;
import com.shubh.movietickets.movies.kafka.ScheduleEventProducer;
import com.shubh.movietickets.movies.repository.MovieRepository;
import com.shubh.movietickets.movies.repository.ScheduleRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {

    private final MovieRepository movieRepository;
    private final ScheduleRepository scheduleRepository;
    private final TheaterClient theaterClient;
    private final ScheduleEventProducer scheduleEventProducer;

    @Transactional
    public MovieDto createMovie(CreateMovieRequest request) {
        Movie movie = Movie.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .genre(request.getGenre())
                .durationMinutes(request.getDurationMinutes())
                .language(request.getLanguage())
                .posterUrl(request.getPosterUrl())
                .releaseDate(request.getReleaseDate())
                .build();
        movie = movieRepository.save(movie);
        return toDto(movie);
    }

    public List<MovieDto> getAllMovies() {
        return movieRepository.findAll().stream().map(this::toDto).toList();
    }

    public MovieDto getMovie(UUID id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", id));
        return toDto(movie);
    }

    public List<MovieDto> searchMovies(String title) {
        return movieRepository.findByTitleContainingIgnoreCase(title).stream()
                .map(this::toDto).toList();
    }

    @Transactional
    @CircuitBreaker(name = "theaters-service", fallbackMethod = "createScheduleFallback")
    public ScheduleDto createSchedule(CreateScheduleRequest request) {
        // Validate movie exists
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", request.getMovieId()));

        // Validate screen exists via Feign call to Theaters Service
        var screenResponse = theaterClient.getScreen(request.getScreenId());
        var screenInfo = screenResponse.getData();

        // Check for schedule conflict
        if (scheduleRepository.existsByScreenIdAndShowDateAndShowTime(
                request.getScreenId(), request.getShowDate(), request.getShowTime())) {
            throw new ConflictException("Screen already has a show at " +
                    request.getShowDate() + " " + request.getShowTime());
        }

        // Find theater info, the screen belongs to a theater
        // Get it from the screen name for now, but it need theaterId
        // The Feign response for screen doesn't include theaterId directly,
        // so it will store the screenId and look up theater info separately
        // For simplicity, it will store the screenId and fetch theater when needed

        MovieSchedule schedule = MovieSchedule.builder()
                .movie(movie)
                .screenId(request.getScreenId())
                .theaterId(screenInfo.id()) // it'll set this properly
                .theaterName("") // populated below
                .screenName(screenInfo.name())
                .showDate(request.getShowDate())
                .showTime(request.getShowTime())
                .totalSeats(screenInfo.totalSeats())
                .build();

        schedule = scheduleRepository.save(schedule);

        // Publish Kafka event for Seats Service to seed seat rows
        ScheduleCreatedEvent event = ScheduleCreatedEvent.builder()
                .sourceService("movies-service")
                .scheduleId(schedule.getId())
                .screenId(request.getScreenId())
                .showDate(request.getShowDate())
                .showTime(request.getShowTime())
                .seatLayouts(screenInfo.seatLayouts().stream()
                        .map(sl -> ScheduleCreatedEvent.SeatLayoutInfo.builder()
                                .category(sl.category())
                                .rowPrefix(sl.rowPrefix())
                                .rowCount(sl.rowCount())
                                .seatsPerRow(sl.seatsPerRow())
                                .price(sl.price())
                                .build())
                        .toList())
                .build();

        scheduleEventProducer.publishScheduleCreated(event);

        return toScheduleDto(schedule, movie.getTitle());
    }

    public ScheduleDto createScheduleFallback(CreateScheduleRequest request, Throwable t) {
        log.error("Circuit breaker triggered for theaters-service: {}", t.getMessage());
        throw new RuntimeException("Theaters Service is unavailable. Please try again later.");
    }

    public List<ScheduleDto> getSchedulesForMovie(UUID movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", movieId));
        return scheduleRepository.findByMovieId(movieId).stream()
                .map(s -> toScheduleDto(s, movie.getTitle()))
                .toList();
    }

    public ScheduleDto getSchedule(UUID scheduleId) {
        MovieSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", "id", scheduleId));
        return toScheduleDto(schedule, schedule.getMovie().getTitle());
    }

    private MovieDto toDto(Movie movie) {
        return MovieDto.builder()
                .id(movie.getId())
                .title(movie.getTitle())
                .description(movie.getDescription())
                .genre(movie.getGenre())
                .durationMinutes(movie.getDurationMinutes())
                .language(movie.getLanguage())
                .posterUrl(movie.getPosterUrl())
                .releaseDate(movie.getReleaseDate())
                .build();
    }

    private ScheduleDto toScheduleDto(MovieSchedule schedule, String movieTitle) {
        return ScheduleDto.builder()
                .id(schedule.getId())
                .movieId(schedule.getMovie().getId())
                .movieTitle(movieTitle)
                .screenId(schedule.getScreenId())
                .theaterId(schedule.getTheaterId())
                .theaterName(schedule.getTheaterName())
                .screenName(schedule.getScreenName())
                .showDate(schedule.getShowDate())
                .showTime(schedule.getShowTime())
                .totalSeats(schedule.getTotalSeats())
                .build();
    }
}
