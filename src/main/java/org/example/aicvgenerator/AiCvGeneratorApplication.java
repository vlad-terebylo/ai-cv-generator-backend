package org.example.aicvgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiCvGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCvGeneratorApplication.class, args);
    }

}
