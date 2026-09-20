package com.dev4.cypher.dto;

import com.dev4.cypher.model.EpiTipo;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Substitui integralmente o checklist global de EPI da empresa. */
public record EpiChecklistRequest(@NotNull List<EpiTipo> epis) {
}
