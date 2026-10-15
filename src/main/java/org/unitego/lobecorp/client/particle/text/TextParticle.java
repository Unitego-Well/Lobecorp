package org.unitego.lobecorp.client.particle.text;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.locale.Language;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jetbrains.annotations.NotNull;
import org.unitego.lobecorp.particle.text.TextParticleSnapshot;
import org.unitego.lobecorp.registry.particle.client.TextParticleRegistration;

import java.util.List;

/// 一个服务端实例对应一个客户端粒子，字符没有独立实体、碰撞或位移。
public class TextParticle extends Particle {

	private TextParticleSnapshot snapshot;
	private TextParticleLayout layout;
	private Language language;
	private long layoutEpoch;
	private boolean unicodeFont;
	private boolean japaneseGlyphs;
	private long playbackTicks;
	private double[] maximumScales;

	public TextParticle(ClientLevel level, TextParticleSnapshot snapshot) {
		super(level, snapshot.position().x(), snapshot.position().y(), snapshot.position().z());
		this.snapshot = snapshot;
		hasPhysics = false;
		playbackTicks = Math.max(0, level.getGameTime() - snapshot.startedTick());
		ensureLayout();
	}

	public void update(TextParticleSnapshot next) {
		if (next.revision() <= snapshot.revision()) {
			return;
		}

		if (!snapshot.text().equals(next.text()) || snapshot.outlineColor() != next.outlineColor()) {
			layout = null;
		}

		if (snapshot.startedTick() != next.startedTick()) {
			playbackTicks = Math.max(0, level.getGameTime() - next.startedTick());
		}

		snapshot = next;
		maximumScales = null;
		setPos(next.position().x(), next.position().y(), next.position().z());
		ensureLayout();
		updateBounds();
	}

	@Override
	public void tick() {
		playbackTicks++;
		ensureLayout();
		var refreshed = layout.refreshObfuscated(Minecraft.getInstance().font, snapshot.outlineColor());
		if (refreshed != layout) {
			layout = refreshed;
			updateBounds();
		}
	}

	@Override
	public @NotNull ParticleRenderType getGroup() {
		return TextParticleRegistration.TYPE;
	}

	public void ensureLayout() {
		var minecraft = Minecraft.getInstance();
		var currentLanguage = Language.getInstance();
		boolean currentUnicode = minecraft.options.forceUnicodeFont().get();
		boolean currentJapanese = minecraft.options.japaneseGlyphVariants().get();
		if (layout != null && language == currentLanguage && layoutEpoch == TextParticleClient.layoutEpoch()
				&& unicodeFont == currentUnicode && japaneseGlyphs == currentJapanese) {
			return;
		}

		layout = TextParticleLayout.create(minecraft.font, snapshot.text(), snapshot.outlineColor());
		language = currentLanguage;
		layoutEpoch = TextParticleClient.layoutEpoch();
		unicodeFont = currentUnicode;
		japaneseGlyphs = currentJapanese;
		updateBounds();
	}

	private void updateBounds() {
		if (maximumScales == null || maximumScales.length != layout.glyphs().size()) {
			maximumScales = new double[layout.glyphs().size()];
			for (int index = 0; index < maximumScales.length; index++) {
				maximumScales[index] = snapshot.characterAnimations().getOrDefault(index, snapshot.animation()).maxScale();
			}
		}

		double radius = 0;
		for (int index = 0; index < layout.glyphs().size(); index++) {
			var glyph = layout.glyphs().get(index);
			double centerDistance = Math.hypot(glyph.x(), glyph.y());
			radius = Math.max(radius, centerDistance + glyph.radius() * maximumScales[index]);
		}

		// 用包围球的外接 AABB 覆盖任意三轴旋转，运行时无需逐帧重算。
		radius *= snapshot.size() / layout.lineHeight();
		setBoundingBox(new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius));
	}

	public void extract(Vec3 camera, float partialTick, List<TextParticleGroup.Draw> draws) {
		double elapsed = playbackTicks + partialTick;
		var rotation = snapshot.rotation();
		float unit = snapshot.size() / layout.lineHeight();
		var base = new Matrix4f().translation((float) (x - camera.x()), (float) (y - camera.y()), (float) (z - camera.z()))
				.rotateXYZ((float) Math.toRadians(rotation.x()), (float) Math.toRadians(rotation.y()), (float) Math.toRadians(rotation.z()))
				.scale(unit, -unit, unit);
		int light = getLightCoords(partialTick);

		for (int index = 0; index < layout.glyphs().size(); index++) {
			double delay = revealRank(index) * (double) snapshot.characterIntervalTicks();
			if (elapsed < delay) {
				continue;
			}

			var glyph = layout.glyphs().get(index);
			var animation = snapshot.characterAnimations().getOrDefault(index, snapshot.animation());
			var transform = animation.sample(elapsed - delay);
			var angles = transform.rotation();
			var scale = transform.scale();
			var pose = new Matrix4f(base).translate(glyph.x(), glyph.y(), 0)
					.rotateXYZ((float) Math.toRadians(angles.x()), (float) Math.toRadians(angles.y()), (float) Math.toRadians(angles.z()))
					.scale((float) scale.x(), (float) scale.y(), (float) scale.z());

			for (var mesh : glyph.meshes()) {
				draws.add(new TextParticleGroup.Draw(mesh, pose, light));
			}
		}
	}

	private int revealRank(int index) {
		int count = layout.glyphs().size();
		return switch (snapshot.revealMode()) {
			case DIRECT -> 0;
			case LEFT_TO_RIGHT -> index;
			case RIGHT_TO_LEFT -> count - index - 1;
			case CENTER_OUT -> Math.abs(2 * index - count + 1) / 2;
		};
	}
}
