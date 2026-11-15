package com.ordredesarcane.online.catalog;

public record AbilityOption(
        String label,
        String action,
        String target,
        Integer value,
        String duration
) {
}
