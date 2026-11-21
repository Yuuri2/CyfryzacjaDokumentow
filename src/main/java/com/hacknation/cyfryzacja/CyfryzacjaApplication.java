package com.hacknation.cyfryzacja;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.hacknation")
public class CyfryzacjaApplication {

	public static void main(String[] args) {
		SpringApplication.run(CyfryzacjaApplication.class, args);
	}

}
