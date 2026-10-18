package com.forkfast.backend.service;

import com.forkfast.backend.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitMqMessageService {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqMessageService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void enviarMensajePrueba(String mensaje) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.TEST_QUEUE, mensaje);
    }
}
