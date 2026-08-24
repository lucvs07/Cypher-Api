package com.dev4.cypher.dto;

import com.dev4.cypher.model.Turno;

import java.util.List;

public record TeamResponse(String id, String nome, Turno turno, List<String> estacaoIds, boolean ativa) {
}
