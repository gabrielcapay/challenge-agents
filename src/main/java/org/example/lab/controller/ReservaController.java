package org.example.lab.controller;

import org.example.lab.model.Reserva;
import org.example.lab.service.ReservaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ReservaController {
    private final ReservaService service;

    public ReservaController(ReservaService service) { this.service = service; }

    @GetMapping("/api/equipos/{id}/disponibilidad")
    public Map<String, Boolean> availability(@PathVariable String id, @RequestParam String inicio, @RequestParam String fin) {
        return Map.of("disponible", service.available(id, inicio, fin));
    }

    @GetMapping("/api/reservas")
    public List<Reserva> list() { return service.list(); }

    @PostMapping("/api/reservas")
    public Reserva create(@RequestBody ReservationInput input) {
        return service.create(input.equipoId(), input.solicitante(), input.inicio(), input.fin());
    }

    public record ReservationInput(String equipoId, String solicitante, String inicio, String fin) { }
}
