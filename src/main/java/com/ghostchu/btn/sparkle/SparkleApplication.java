package com.ghostchu.btn.sparkle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SparkleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SparkleApplication.class, args);
    }

}
