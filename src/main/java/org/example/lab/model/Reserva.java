package org.example.lab.model;

import java.time.Instant;

public record Reserva(String id, String equipoId, String solicitante, Instant inicio, Instant fin, String estado) { }
