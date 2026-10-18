package com.forkfast.backend.controller;

import com.forkfast.backend.config.RabbitMqConfig;
import com.forkfast.backend.service.RabbitMqMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mensajeria")
public class MensajeriaController {

    private final RabbitMqMessageService messageService;

    public MensajeriaController(RabbitMqMessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/prueba")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensajeEnviado enviarMensajePrueba(@Valid @RequestBody SolicitudMensaje solicitud) {
        messageService.enviarMensajePrueba(solicitud.mensaje());
        return new MensajeEnviado("Mensaje enviado a RabbitMQ", RabbitMqConfig.TEST_QUEUE,
                solicitud.mensaje());
    }

    public record SolicitudMensaje(@NotBlank String mensaje) { }

    public record MensajeEnviado(String estado, String cola, String mensaje) { }
}
