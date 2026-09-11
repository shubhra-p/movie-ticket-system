package com.shubh.movietickets.movies.repository;

import com.shubh.movietickets.movies.entity.MovieSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<MovieSchedule, UUID> {
    List<MovieSchedule> findByMovieId(UUID movieId);
    List<MovieSchedule> findByScreenId(UUID screenId);
    List<MovieSchedule> findByMovieIdAndShowDate(UUID movieId, LocalDate showDate);
    boolean existsByScreenIdAndShowDateAndShowTime(UUID screenId, LocalDate showDate, java.time.LocalTime showTime);
}
