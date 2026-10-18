package com.forkfast.backend.service;

import com.forkfast.backend.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqMessageListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqMessageListener.class);

    @RabbitListener(queues = RabbitMqConfig.TEST_QUEUE)
    public void recibirMensajePrueba(String mensaje) {
        log.info("Mensaje recibido desde RabbitMQ en la cola '{}': {}",
                RabbitMqConfig.TEST_QUEUE, mensaje);
    }
}
