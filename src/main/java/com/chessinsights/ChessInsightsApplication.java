package com.chessinsights;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChessInsightsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChessInsightsApplication.class, args);
    }
}
