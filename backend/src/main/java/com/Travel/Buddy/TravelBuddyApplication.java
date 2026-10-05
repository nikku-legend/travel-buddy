package com.Travel.Buddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TravelBuddyApplication {

	public static void main(String[] args) {
		SpringApplication.run(TravelBuddyApplication.class, args);
	}

}
