package com.shubh.movietickets.theaters.repository;

import com.shubh.movietickets.theaters.entity.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TheaterRepository extends JpaRepository<Theater, UUID> {
    List<Theater> findByCity(String city);
}
