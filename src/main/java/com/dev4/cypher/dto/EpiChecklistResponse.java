package com.dev4.cypher.dto;

import com.dev4.cypher.model.EpiTipo;

import java.util.List;

/** Checklist global de EPI da empresa — escopo default lido pelo pipeline. */
public record EpiChecklistResponse(List<EpiTipo> epis) {
}
