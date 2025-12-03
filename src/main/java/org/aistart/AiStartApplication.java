package org.aistart;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
//redis默认开启向量增强功能，在rag增强会用到，本项目用不到，所以在这里关闭
@MapperScan("org.aistart.mapper")
public class  AiStartApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStartApplication.class, args);
    }

}
