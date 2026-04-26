package com.dev4.cypher.service;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.repository.AlertaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AlertaService {

    @Autowired
    private AlertaRepository alertaRepository;

    public Alerta criar(Alerta alerta) {
        return alertaRepository.save(alerta);
    }

    public List<Alerta> listar() {
        return alertaRepository.findAll();
    }

    public Optional<Alerta> buscarPorId(Long id) {
        return alertaRepository.findById(id);
    }

    public Optional<Alerta> atualizar(Long id, Alerta dadosAtualizados) {
        return alertaRepository.findById(id).map(alerta -> {
            alerta.setTipo(dadosAtualizados.getTipo());
            alerta.setNivel(dadosAtualizados.getNivel());
            alerta.setDescricao(dadosAtualizados.getDescricao());
            alerta.setZona(dadosAtualizados.getZona());
            alerta.setCameraId(dadosAtualizados.getCameraId());
            alerta.setDataHora(dadosAtualizados.getDataHora());
            alerta.setStatus(dadosAtualizados.getStatus());
            return alertaRepository.save(alerta);
        });
    }

    public boolean remover(Long id) {
        if (alertaRepository.existsById(id)) {
            alertaRepository.deleteById(id);
            return true;
        }
        return false;
    }
}