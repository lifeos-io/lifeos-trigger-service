package com.lifeos.trigger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs due jobs on a schedule ({@code JobScheduler}). */
@SpringBootApplication
@EnableScheduling
public class TriggerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TriggerServiceApplication.class, args);
    }
}
