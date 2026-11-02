package org.example.lab.service;

import org.example.lab.model.Equipo;
import org.example.lab.repository.EquipoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EquipoService {
    private final EquipoRepository repository;

    public EquipoService(EquipoRepository repository) {
        this.repository = repository;
    }

    public List<Equipo> list() {
        return repository.findAll();
    }
}
