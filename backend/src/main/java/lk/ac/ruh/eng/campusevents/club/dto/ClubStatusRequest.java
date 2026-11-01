package lk.ac.ruh.eng.campusevents.club.dto;

import jakarta.validation.constraints.NotNull;
import lk.ac.ruh.eng.campusevents.domain.ClubStatus;

public record ClubStatusRequest(@NotNull ClubStatus status) { }
