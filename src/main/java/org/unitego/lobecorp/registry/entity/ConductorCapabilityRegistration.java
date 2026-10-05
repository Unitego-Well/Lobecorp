package org.unitego.lobecorp.registry.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.warden.Warden;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.unitego.lobecorp.conductor.control.ConductorCapabilities;
import org.unitego.lobecorp.conductor.control.ConductorMobAdapters.BreezeControl;
import org.unitego.lobecorp.conductor.control.ConductorMobAdapters.DragonControl;
import org.unitego.lobecorp.conductor.control.ConductorMobAdapters.GhastControl;
import org.unitego.lobecorp.conductor.control.ConductorMobAdapters.QueenControl;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.registry.LcCapabilities;

/// 指挥家 EntityCapability 的注册适配器。
public class ConductorCapabilityRegistration {
	public static void register(RegisterCapabilitiesEvent event) {
		for (var type : BuiltInRegistries.ENTITY_TYPE) {
			event.registerEntity(LcCapabilities.CONDUCTOR_CONTROL, type, (entity, context) -> {
				if (entity instanceof Sweeper sweeper)
					return new ConductorCapabilities.SweeperControl(sweeper);
				if (entity instanceof Warden warden)
					return new ConductorCapabilities.WardenControl(warden);
				if (entity instanceof Ghast ghast)
					return new GhastControl(ghast);
				if (entity instanceof Breeze breeze)
					return new BreezeControl(breeze);
				if (entity instanceof EnderDragon dragon)
					return new DragonControl(dragon);
				if (entity instanceof TheQueenOfHatred queen)
					return new QueenControl(queen);
				return entity instanceof Mob mob ? new ConductorCapabilities.MobControl(mob) : null;
			});
		}
	}
}
