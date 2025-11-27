package org.aistart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.aistart.mapper")
public class  AiStartApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStartApplication.class, args);
    }

}
