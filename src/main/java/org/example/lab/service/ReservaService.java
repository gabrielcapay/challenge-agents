package org.example.lab.service;

import org.example.lab.model.Reserva;
import org.example.lab.repository.ReservaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class ReservaService {
    private final ReservaRepository repository;

    public ReservaService(ReservaRepository repository) { this.repository = repository; }

    public List<Reserva> list() { return repository.findAll(); }

    public boolean available(String equipment, String start, String end) {
        Interval interval = interval(start, end);
        return repository.available(equipment, interval.start(), interval.end());
    }

    public Reserva create(String equipment, String applicant, String start, String end) {
        if (applicant == null || applicant.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitante obligatorio");
        Interval interval = interval(start, end);
        return repository.create(equipment, applicant, interval.start(), interval.end());
    }

    private Interval interval(String start, String end) {
        try {
            Instant from = OffsetDateTime.parse(start).toInstant();
            Instant to = OffsetDateTime.parse(end).toInstant();
            if (!from.isBefore(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inicio debe preceder fin");
            return new Interval(from, to);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inicio y fin requieren fecha ISO 8601 con desplazamiento", e);
        }
    }

    private record Interval(Instant start, Instant end) { }
}
