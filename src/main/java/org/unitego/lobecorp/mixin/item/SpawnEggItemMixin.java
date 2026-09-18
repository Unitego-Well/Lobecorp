package org.unitego.lobecorp.mixin.item;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.item.LcSpawnEggItem;

import java.util.Objects;
import java.util.Optional;

@Mixin(SpawnEggItem.class)
public abstract class SpawnEggItemMixin {
    @WrapMethod(method = "getType")
    private static @Nullable EntityType<?> wild_wind$getType(ItemStack itemStack, Operation<EntityType<?>> original) {
        EntityType<?> call = original.call(itemStack);
        if (call != null) {
            return call;
        }
        if (itemStack.getItem() instanceof LcSpawnEggItem spawnEggItem) {
            return spawnEggItem.getType();
        }
        return null;
    }

    @WrapMethod(method = "byId")
    private static Optional<Holder<Item>> wild_wind$byId(EntityType<?> type, Operation<Optional<Holder<Item>>> original) {
        Optional<Holder<Item>> call = original.call(type);
        if (call.isPresent()) {
            return call;
        }
        return BuiltInRegistries.ITEM.stream()
                .filter(LcSpawnEggItem.class::isInstance)
                .map(LcSpawnEggItem.class::cast)
                .filter(item -> item.getType() == type)
                .map(BuiltInRegistries.ITEM::wrapAsHolder)
                .findAny();
    }

    @WrapMethod(method = "spawnsEntity")
    private static boolean wild_wind$spawnsEntity(ItemStack itemStack, EntityType<?> type, Operation<Boolean> original) {
        boolean call = original.call(itemStack, type);
        if (call) {
            return true;
        }
        if (itemStack.getItem() instanceof LcSpawnEggItem spawnEggItem) {
            return Objects.equals(spawnEggItem.getType(), type);
        }
        return false;
    }
}
