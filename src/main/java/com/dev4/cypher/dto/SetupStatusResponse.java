package com.dev4.cypher.dto;

/** Corpo de `GET /api/v1/setup/status` — o front decide onboarding-vs-/login a partir daqui (spec E4 §2.5/§2.6). */
public record SetupStatusResponse(boolean configured) {
}
