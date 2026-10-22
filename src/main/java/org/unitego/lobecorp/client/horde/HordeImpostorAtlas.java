package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.util.horde.HordeImpostorRasterizerUtil;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/// 按资源版本共享四视角奔跑图集；每帧只推进一个姿态，完成前保留三维显示。
public class HordeImpostorAtlas {
	public static final double TRANSITION_BLOCKS = 4;
	private static final Map<HordeVisualDefinition, Entry> ENTRIES = new HashMap<>();
	private static long nextId;
	private static String signature = "";
	private static final HordeMeshCache.Mesh QUAD = new HordeMeshCache.Mesh(new float[]{
			-0.5f, 0, 0, 0, 1, 0, 1, 0,
			0.5f, 0, 0, 1, 1, 0, 1, 0,
			0.5f, 1, 0, 1, 0, 0, 1, 0,
			-0.5f, 1, 0, 0, 0, 0, 1, 0
	});
	public static final HordeMeshCache.Pair QUAD_PAIR = new HordeMeshCache.Pair(QUAD, QUAD, 0);

	public static void beginFrame() {
		String current = HordeVisualConfig.IMPOSTOR_FRAMES.get() + ":" + HordeVisualConfig.IMPOSTOR_RESOLUTION.get();
		if (!current.equals(signature)) {
			clear();
			signature = current;
		}
		if (!HordeVisualConfig.IMPOSTORS.get()) {
			return;
		}
		for (var entry : ENTRIES.values()) {
			if (entry.pending != null) {
				entry.advance();
				break;
			}
		}
	}

	public static Atlas get(HordeVisualDefinition visual) {
		return HordeVisualConfig.IMPOSTORS.get() ? ENTRIES.computeIfAbsent(visual, Entry::new).atlas : null;
	}

	public static String statistics() {
		int ready = 0, pending = 0;
		for (var entry : ENTRIES.values()) {
			ready += entry.atlas == null ? 0 : 1;
			pending += entry.pending == null ? 0 : 1;
		}
		return "atlases=" + ready + ", baking=" + pending;
	}

	public static void clear() {
		for (var entry : ENTRIES.values()) {
			entry.close();
		}
		ENTRIES.clear();
	}

	public static class Atlas implements AutoCloseable {
		private final Identifier color, glow;
		private final GpuBuffer bounds;
		private final double duration;

		private Atlas(Pending pending) {
			long id = nextId++;
			color = Lobecorp.id("horde/impostor_" + id + ".png");
			glow = color.withPath(path -> path.replace(".png", "_glowmask.png"));
			duration = HordeMeshCache.duration(pending.visual);
			var bytes = MemoryUtil.memAlloc(32);
			try {
				bytes.putFloat(pending.raster.radius()).putFloat(pending.raster.minimumY())
						.putFloat(pending.raster.maximumY()).putFloat(pending.frames);
				bytes.putFloat(pending.size).putFloat(pending.size * 4).putFloat(pending.size * pending.frames).putFloat(0).flip();
				bounds = RenderSystem.getDevice().createBuffer(() -> "Horde baked bounds", GpuBuffer.USAGE_UNIFORM, bytes);
			} finally {
				MemoryUtil.memFree(bytes);
			}
			var manager = Minecraft.getInstance().getTextureManager();
			try {
				manager.register(color, new DynamicTexture(() -> "Horde baked albedo", pending.raster.takeColor()));
				manager.register(glow, new DynamicTexture(() -> "Horde baked emission", pending.raster.takeGlow()));
			} catch (RuntimeException | Error exception) {
				manager.release(color);
				manager.release(glow);
				bounds.close();
				throw exception;
			}
		}

		public Identifier color() { return color; }
		public Identifier glow() { return glow; }
		public GpuBuffer bounds() { return bounds; }
		public float phase(HordeClient.Instance instance) {
			return (float) ((instance.time() / 20 / duration
					+ instance.phase() / (double) HordeVisualConfig.POSE_GROUPS.get()) % 1);
		}

		@Override
		public void close() {
			Minecraft.getInstance().getTextureManager().release(color);
			Minecraft.getInstance().getTextureManager().release(glow);
			bounds.close();
		}
	}

	private static class Entry implements AutoCloseable {
		private Pending pending;
		private Atlas atlas;

		private Entry(HordeVisualDefinition visual) {
			pending = new Pending(visual);
		}

		private void advance() {
			try {
				if (pending.advance()) {
					atlas = new Atlas(pending);
					pending.close();
					pending = null;
				}
			} catch (IOException | RuntimeException exception) {
				Lobecorp.LOGGER.warn("群体四视角图集烘焙失败，保留三维模型", exception);
				pending.close();
				pending = null;
			}
		}

		@Override
		public void close() {
			if (pending != null) { pending.close(); }
			if (atlas != null) { atlas.close(); }
		}
	}

	private static class Pending implements AutoCloseable {
		private final HordeVisualDefinition visual;
		private final int frames = HordeVisualConfig.IMPOSTOR_FRAMES.get(), size = HordeVisualConfig.IMPOSTOR_RESOLUTION.get();
		private final ArrayList<HordeMeshCache.Mesh> poses = new ArrayList<>();
		private HordeImpostorRasterizerUtil raster;
		private int baked;

		private Pending(HordeVisualDefinition visual) {
			this.visual = visual;
		}

		private boolean advance() throws IOException {
			if (poses.size() < frames) {
				var pose = HordeMeshCache.bakePose(visual, poses.size(), frames);
				if (pose == null) {
					throw new IOException("奔跑模型或动画不可用");
				}
				poses.add(pose);
				return false;
			}
			if (raster == null) {
				var resources = Minecraft.getInstance().getResourceManager();
				NativeImage source = NativeImage.read(resources.getResourceOrThrow(visual.texture()).open());
				NativeImage glow = null;
				try {
					var emission = resources.getResource(visual.texture().withPath(path -> path.replace(".png", "_glowmask.png")));
					glow = emission.isPresent() ? NativeImage.read(emission.get().open()) : new NativeImage(1, 1, true);
					raster = new HordeImpostorRasterizerUtil(poses, size, source, glow);
				} catch (IOException | RuntimeException | Error exception) {
					source.close();
					if (glow != null) { glow.close(); }
					throw exception;
				}
			}
			raster.bake(poses.get(baked), baked);
			return ++baked == frames;
		}

		@Override
		public void close() {
			if (raster != null) { raster.close(); raster = null; }
			poses.clear();
		}
	}
}
