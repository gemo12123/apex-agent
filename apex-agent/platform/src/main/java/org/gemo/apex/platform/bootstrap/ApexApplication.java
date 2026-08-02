package org.gemo.apex.platform.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "org.gemo.apex.platform")
public class ApexApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApexApplication.class, args);
    }
}
