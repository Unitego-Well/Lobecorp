package org.unitego.lobecorp.registry.conductor.client;

import com.google.common.reflect.TypeToken;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.unitego.lobecorp.client.conductor.render.ConductorRendering;

/// 指挥家实体 render-state modifier 注册入口。
public class ConductorRenderStateRegistration {
	public static void register(RegisterRenderStateModifiersEvent event) {
		event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
		}, ConductorRendering::updateEntitySelection);
	}
}
