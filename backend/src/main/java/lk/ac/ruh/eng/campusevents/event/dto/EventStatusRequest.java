package lk.ac.ruh.eng.campusevents.event.dto;

import jakarta.validation.constraints.NotNull;
import lk.ac.ruh.eng.campusevents.domain.EventStatus;

public record EventStatusRequest(@NotNull EventStatus status) { }
