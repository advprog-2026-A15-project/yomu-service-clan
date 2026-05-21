package id.ac.ui.cs.advprog.yomu.clan.internal.controller.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateClanRequest(
        @NotBlank String name,
        String description,
        UUID leaderId
) {
}
