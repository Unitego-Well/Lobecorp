package org.unitego.lobecorp.client.particle.photon;

import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.unitego.lobecorp.Lobecorp;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class PhotonParticleRuntimeTrial {
	@SubscribeEvent
	public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		ParticleEmitter emitter = new ParticleEmitter();
		emitter.transform()._refreshInternalID();
		emitter.config.setDuration(4);
		emitter.config.setLooping(false);
		emitter.config.setMaxParticles(16);
		emitter.config.setStartLifetime(NumberFunction.constant(60));
		emitter.config.setStartSpeed(NumberFunction.constant(0.05F));
		emitter.config.setStartSize(new NumberFunction3(0.4F, 0.4F, 0.4F));
		emitter.config.emission.setEmissionRate(NumberFunction.constant(2));
		emitter.config.shape.setShape(new Sphere());

		FX fx = new FX();
		fx.getFxData().objects().add(emitter);
		BlockPos anchor = event.getPlayer().blockPosition();
		new BlockEffectExecutor(fx, event.getPlayer().level(), anchor).start();
	}
}
