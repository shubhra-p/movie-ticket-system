package com.shubh.movietickets.movies;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MoviesServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MoviesServiceApplication.class, args);
    }
}
