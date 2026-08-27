package com.grape.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GrapeApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrapeApiApplication.class, args);
	}

}
