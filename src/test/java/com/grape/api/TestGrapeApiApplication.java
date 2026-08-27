package com.grape.api;

import org.springframework.boot.SpringApplication;

public class TestGrapeApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(GrapeApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
