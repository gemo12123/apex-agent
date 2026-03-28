package org.gemo.apex;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Apex Agent Application
 */
@MapperScan("org.gemo.apex.memory.persistence.mapper")
@SpringBootApplication(scanBasePackages = "org.gemo.apex")
public class ApexApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApexApplication.class, args);
    }
}
