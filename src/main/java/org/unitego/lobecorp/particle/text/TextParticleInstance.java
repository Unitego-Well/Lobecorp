package org.unitego.lobecorp.particle.text;

import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// 服务端持有的文本展示实例；修改通过 setter 同步，逐字与关键帧求值留在客户端。
public class TextParticleInstance {
	/// 默认每刻显露一个字符，可通过显示规则修改。
	public static final int DEFAULT_CHARACTER_INTERVAL_TICKS = 1;
	private static final String REMOVED_MESSAGE = "文本展示实例已移除";
	private static final String INVALID_SIZE_MESSAGE = "文本字号必须为有限正数";
	private static final String INVALID_VECTOR_MESSAGE = "文本位置和旋转必须为有限数值";
	private static final String INVALID_INTERVAL_MESSAGE = "逐字间隔必须至少为一刻";
	private static final String INVALID_INDEX_MESSAGE = "字符索引不能为负数";

	private final ServerLevel level;
	private final UUID id = UUID.randomUUID();
	private final Map<Integer, TextGlyphAnimation> characterAnimations = new HashMap<>();
	private Component text;
	private Vec3 position;
	private Vec3 rotation = Vec3.ZERO;
	private float size;
	private TextRevealMode revealMode = TextRevealMode.DIRECT;
	private int characterIntervalTicks = DEFAULT_CHARACTER_INTERVAL_TICKS;
	private TextGlyphAnimation animation = TextGlyphAnimation.NONE;
	private int outlineColor;
	private long revision;
	private long startedTick;
	private boolean removed;

	/// 创建尚未加入世界管理器的实例；字号是单行字符高度对应的世界格数。
	public TextParticleInstance(ServerLevel level, Vec3 position, Component text, float size) {
		this.level = Objects.requireNonNull(level);
		this.position = requireFinite(position);
		this.text = copyText(text);
		this.size = requireSize(size);
		this.startedTick = level.getGameTime();
	}

	public ServerLevel level() {
		return level;
	}

	public UUID id() {
		return id;
	}

	public boolean isRemoved() {
		return removed;
	}

	/// 返回独立组件树，避免外部原地修改绕过同步。
	public Component text() {
		return copyText(text);
	}

	public Vec3 position() {
		return position;
	}

	public Vec3 rotation() {
		return rotation;
	}

	public float size() {
		return size;
	}

	/// 复制完整组件树；文字或样式变化时重新开始逐字显示。
	public void setText(Component text) {
		ensureActive();
		var copied = copyText(text);
		if (this.text.equals(copied)) {
			return;
		}

		this.text = copied;
		changed(true);
	}

	public void setPosition(Vec3 position) {
		ensureActive();
		this.position = requireFinite(position);
		changed(false);
	}

	/// 整段文字的 Euler XYZ 旋转，单位为度。
	public void setRotation(Vec3 rotation) {
		ensureActive();
		this.rotation = requireFinite(rotation);
		changed(false);
	}

	public void setSize(float size) {
		ensureActive();
		this.size = requireSize(size);
		changed(false);
	}

	/// 显示方式或逐字间隔变化时重新开始，字位始终按完整文本排版。
	public void setReveal(TextRevealMode mode, int characterIntervalTicks) {
		ensureActive();
		if (characterIntervalTicks < 1) {
			throw new IllegalArgumentException(INVALID_INTERVAL_MESSAGE);
		}

		this.revealMode = Objects.requireNonNull(mode);
		this.characterIntervalTicks = characterIntervalTicks;
		changed(true);
	}

	/// 所有未单独覆盖的字符共用此动画，各自从显露时开始求值。
	public void setAnimation(TextGlyphAnimation animation) {
		ensureActive();
		this.animation = Objects.requireNonNull(animation);
		changed(false);
	}

	/// 索引按本地翻译后的排版字符计数，空格计入，换行不计入。
	public void setCharacterAnimation(int index, TextGlyphAnimation animation) {
		ensureActive();
		if (index < 0) {
			throw new IllegalArgumentException(INVALID_INDEX_MESSAGE);
		}

		characterAnimations.put(index, Objects.requireNonNull(animation));
		changed(false);
	}

	public void clearCharacterAnimation(int index) {
		ensureActive();
		if (characterAnimations.remove(index) != null) {
			changed(false);
		}
	}

	/// ARGB 描边颜色；零关闭描边，启用时使用原版一像素轮廓。
	public void setOutlineColor(int outlineColor) {
		ensureActive();
		this.outlineColor = outlineColor;
		changed(false);
	}

	public void restart() {
		ensureActive();
		changed(true);
	}

	/// 立即通知客户端移除；实例不能再次加入。
	public void remove() {
		if (removed) {
			return;
		}

		removed = true;
		TextParticles.remove(this);
	}

	/// 快照组件与实例内的可变组件树隔离，调用者不能通过快照修改实例。
	public TextParticleSnapshot snapshot() {
		return new TextParticleSnapshot(id, revision, startedTick, copyText(text), position, rotation, size,
				revealMode, characterIntervalTicks, animation, characterAnimations, outlineColor);
	}

	private void changed(boolean restart) {
		revision++;
		if (restart) {
			startedTick = level.getGameTime();
		}

		TextParticles.update(this);
	}

	private void ensureActive() {
		if (removed) {
			throw new IllegalStateException(REMOVED_MESSAGE);
		}
	}

	private Component copyText(Component component) {
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		var encoded = ComponentSerialization.CODEC.encodeStart(ops, Objects.requireNonNull(component)).getOrThrow();
		return ComponentSerialization.CODEC.parse(ops, encoded).getOrThrow();
	}

	private static Vec3 requireFinite(Vec3 vector) {
		if (!Objects.requireNonNull(vector).isFinite()) {
			throw new IllegalArgumentException(INVALID_VECTOR_MESSAGE);
		}

		return vector;
	}

	private static float requireSize(float size) {
		if (!Float.isFinite(size) || size <= 0.0F) {
			throw new IllegalArgumentException(INVALID_SIZE_MESSAGE);
		}

		return size;
	}
}
