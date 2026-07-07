package mx.personas.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiPersonalDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiPersonalDataApplication.class, args);
    }
}
