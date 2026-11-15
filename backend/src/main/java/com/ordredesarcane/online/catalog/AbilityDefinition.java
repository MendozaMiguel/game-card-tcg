package com.ordredesarcane.online.catalog;

import java.util.List;

public record AbilityDefinition(
        String trigger,
        String condition,
        String target,
        String action,
        Integer value,
        Integer secondaryValue,
        String duration,
        List<AbilityOption> options
) {
}
