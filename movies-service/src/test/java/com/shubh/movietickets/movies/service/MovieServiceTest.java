package com.shubh.movietickets.movies.service;

import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.movies.dto.*;
import com.shubh.movietickets.movies.entity.Movie;
import com.shubh.movietickets.movies.entity.MovieSchedule;
import com.shubh.movietickets.movies.client.TheaterClient;
import com.shubh.movietickets.movies.kafka.ScheduleEventProducer;
import com.shubh.movietickets.movies.repository.MovieRepository;
import com.shubh.movietickets.movies.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private TheaterClient theaterClient;

    @Mock
    private ScheduleEventProducer scheduleEventProducer;

    @InjectMocks
    private MovieService movieService;

    private Movie testMovie;
    private UUID movieId;

    @BeforeEach
    void setUp() {
        movieId = UUID.randomUUID();
        testMovie = Movie.builder()
                .id(movieId)
                .title("Inception")
                .description("A mind-bending thriller")
                .genre("Sci-Fi")
                .durationMinutes(148)
                .language("English")
                .releaseDate(LocalDate.of(2010, 7, 16))
                .build();
    }

    @Test
    @DisplayName("Create movie - success")
    void createMovie_success() {
        CreateMovieRequest request = new CreateMovieRequest(
                "Inception", "A mind-bending thriller", "Sci-Fi",
                148, "English", null, LocalDate.of(2010, 7, 16)
        );
        when(movieRepository.save(any(Movie.class))).thenReturn(testMovie);

        MovieDto result = movieService.createMovie(request);

        assertThat(result.getTitle()).isEqualTo("Inception");
        assertThat(result.getDurationMinutes()).isEqualTo(148);
        verify(movieRepository).save(any(Movie.class));
    }

    @Test
    @DisplayName("Get movie - success")
    void getMovie_success() {
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(testMovie));

        MovieDto result = movieService.getMovie(movieId);

        assertThat(result.getTitle()).isEqualTo("Inception");
        assertThat(result.getGenre()).isEqualTo("Sci-Fi");
    }

    @Test
    @DisplayName("Get movie - not found")
    void getMovie_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(movieRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.getMovie(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get all movies")
    void getAllMovies() {
        when(movieRepository.findAll()).thenReturn(List.of(testMovie));

        List<MovieDto> results = movieService.getAllMovies();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Inception");
    }

    @Test
    @DisplayName("Search movies by title")
    void searchMovies() {
        when(movieRepository.findByTitleContainingIgnoreCase("incep"))
                .thenReturn(List.of(testMovie));

        List<MovieDto> results = movieService.searchMovies("incep");

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("Get schedules for movie")
    void getSchedulesForMovie() {
        MovieSchedule schedule = MovieSchedule.builder()
                .id(UUID.randomUUID())
                .movie(testMovie)
                .screenId(UUID.randomUUID())
                .theaterId(UUID.randomUUID())
                .theaterName("PVR")
                .screenName("Screen 1")
                .showDate(LocalDate.now())
                .showTime(java.time.LocalTime.of(18, 30))
                .totalSeats(100)
                .build();

        when(movieRepository.findById(movieId)).thenReturn(Optional.of(testMovie));
        when(scheduleRepository.findByMovieId(movieId)).thenReturn(List.of(schedule));

        List<ScheduleDto> results = movieService.getSchedulesForMovie(movieId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMovieTitle()).isEqualTo("Inception");
        assertThat(results.get(0).getTotalSeats()).isEqualTo(100);
    }
}
