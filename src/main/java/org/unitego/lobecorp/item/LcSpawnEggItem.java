package org.unitego.lobecorp.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Supplier;

public class LcSpawnEggItem extends SpawnEggItem {
	private final Supplier<? extends EntityType<?>> supplierType;
	private EntityType<?> type;

	public LcSpawnEggItem(Supplier<? extends EntityType<?>> type, Properties properties) {
		super(properties);
		this.supplierType = type;
	}

	public EntityType<? extends Entity> getType() {
		if (type == null) {
			type = supplierType.get();
		}
		return type;
	}
}
