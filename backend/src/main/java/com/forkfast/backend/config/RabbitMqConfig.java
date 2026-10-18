package com.forkfast.backend.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String TEST_QUEUE = "forkfast.prueba";

    @Bean
    public Queue testQueue() {
        return new Queue(TEST_QUEUE, true);
    }
}
