package com.chefit.gateway.dto;

import java.util.List;

public record Recipe(
    String id,
    String name,
    String time,
    String servings,
    List<Ingredient> ingredients,
    List<String> steps
) {
}
