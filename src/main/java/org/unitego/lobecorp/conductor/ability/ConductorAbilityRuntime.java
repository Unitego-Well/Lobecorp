package org.unitego.lobecorp.conductor.ability;

import net.minecraft.resources.Identifier;

import java.util.UUID;

/// 单个能力在指挥家目录中的运行摘要。
public record ConductorAbilityRuntime(UUID owner, Identifier id, ConductorAbility ability) {
}
