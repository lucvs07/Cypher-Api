package com.dev4.cypher.service;

import com.dev4.cypher.dto.CameraResponse;
import com.dev4.cypher.dto.StationResponse;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CatalogService {

    private final EstacaoRepository estacoes;
    private final CameraRepository cameras;

    public CatalogService(EstacaoRepository estacoes, CameraRepository cameras) {
        this.estacoes = estacoes;
        this.cameras = cameras;
    }

    @Transactional(readOnly = true)
    public List<CameraResponse> cameras() {
        return cameras.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StationResponse> stations() {
        Map<String, List<CameraResponse>> porEstacao = cameras.findAll().stream()
                .collect(Collectors.groupingBy(Camera::getEstacaoId,
                        Collectors.mapping(this::toResponse, Collectors.toList())));

        return estacoes.findAll().stream()
                .map(e -> new StationResponse(e.getId(), e.getNome(), e.getSetor(), e.isAtiva(),
                        porEstacao.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    private CameraResponse toResponse(Camera c) {
        return new CameraResponse(c.getId(), c.getNome(), c.getEstacaoId(), c.getStreamUrl());
    }
}
