package org.unitego.lobecorp.hitbox;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/// 位于服务端 Level 中的可变判断框实例。
public class HitboxInstance {
	private final HitboxTemplate template;
	private final ServerLevel level;
	private final Map<TypedDataKey<?>, Object> data = new HashMap<>();
	private final Map<UUID, HitRecord> hitRecords = new HashMap<>();
	private HitboxSize size;
	private Vec3 position;
	private Vec3 rotation = Vec3.ZERO;
	private Vec3 localRotation = Vec3.ZERO;
	private Vec3 localOffset = Vec3.ZERO;
	private Vec3 rotationPivot = Vec3.ZERO;
	private Predicate<Entity> additionalFilter = entity -> true;
	private HitboxHitPolicy hitPolicy = HitboxHitPolicy.ONCE_PER_TARGET;
	private HitboxLineOfSightMode lineOfSightMode = HitboxLineOfSightMode.ENTITY_TO_ENTITY;
	@Nullable
	private Entity source;
	private int id;
	private int remainingTicks;
	private int successfulHits;
	private boolean followsSource;
	private boolean inheritsSourceRotation;
	private boolean followsHeadYaw;
	private boolean active;
	private boolean exhausted;
	private boolean removed;

	/// 创建尚未加入 Level 管理器的实例。
	///
	/// @param template 共享模板
	/// @param level 所在服务端维度
	/// @param position 初始中心位置
	/// @param durationTicks 包含预览阶段的总存在时间；1 表示当前 tick
	public HitboxInstance(HitboxTemplate template, ServerLevel level, Vec3 position, int durationTicks) {
		if (durationTicks <= 0) {
			throw new IllegalArgumentException("durationTicks must be positive");
		}
		this.template = template;
		this.level = level;
		this.size = template.initialSize();
		this.position = position;
		this.remainingTicks = durationTicks;
	}

	/// @return Level 内唯一实例编号
	public int id() {
		return id;
	}

	void setId(int id) {
		this.id = id;
	}

	/// @return 所属服务端维度
	public ServerLevel level() {
		return level;
	}

	/// @return 创建实例的共享模板
	public HitboxTemplate template() {
		return template;
	}

	/// @return 当前类型化尺寸
	public HitboxSize size() {
		return size;
	}

	/// 修改当前尺寸，但不允许改变模板确定的形状类型。
	///
	/// @param size 新尺寸
	public void setSize(HitboxSize size) {
		if (size.type() != template.shapeType()) {
			throw new IllegalArgumentException("hitbox shape type cannot be changed");
		}
		this.size = size;
	}

	/// @return 当前世界中心位置
	public Vec3 position() {
		return position;
	}

	/// @param position 新世界中心位置
	public void setPosition(Vec3 position) {
		this.position = position;
	}

	/// @return 以度为单位的 X/Y/Z 欧拉旋转
	public Vec3 rotation() {
		return rotation;
	}

	/// @param rotation 以度为单位的 X/Y/Z 欧拉旋转
	public void setRotation(Vec3 rotation) {
		this.localRotation = rotation;
		if (followsSource) {
			updateFollowTransform();
		} else {
			this.rotation = rotation;
		}
	}

	/// @return 相对来源实体包围盒中心的局部旋转轴承点
	public Vec3 rotationPivot() {
		return rotationPivot;
	}

	/// 设置跟随实例的局部旋转轴承点；零向量表示来源实体包围盒中心。
	///
	/// @param rotationPivot 相对来源实体包围盒中心的局部坐标
	public void setRotationPivot(Vec3 rotationPivot) {
		this.rotationPivot = rotationPivot;
		if (followsSource) {
			updateFollowTransform();
		}
	}

	/// 绑定来源实体并在每 tick 根据局部偏移更新中心。
	///
	/// @param source 来源实体
	/// @param localOffset 随来源朝向旋转的局部偏移
	/// @param inheritRotation 是否把来源俯仰和偏航加入实例旋转
	public void follow(Entity source, Vec3 localOffset, boolean inheritRotation) {
		this.source = source;
		this.localOffset = localOffset;
		this.followsSource = true;
		this.inheritsSourceRotation = inheritRotation;
		this.followsHeadYaw = false;
		updateFollowTransform();
	}

	/// 绑定来源生物并在每 tick 使用头部偏航更新实例旋转。
	///
	/// @param source 来源生物
	/// @param localOffset 随来源身体朝向旋转的局部偏移
	public void followHead(LivingEntity source, Vec3 localOffset) {
		follow(source, localOffset, true);
		followsHeadYaw = true;
		updateFollowTransform();
	}

	/// 仅设置来源，用于生命周期和实体到实体遮挡判断，不自动移动实例。
	///
	/// @param source 来源实体
	public void setSource(@Nullable Entity source) {
		this.source = source;
	}

	/// @return 可选来源实体
	@Nullable
	public Entity source() {
		return source;
	}

	/// 追加实例专用目标过滤条件。
	///
	/// @param filter 在模板过滤器之后执行的条件
	public void appendTargetFilter(Predicate<Entity> filter) {
		additionalFilter = additionalFilter.and(filter);
	}

	boolean accepts(Entity target) {
		return template.targetFilter().test(target) && additionalFilter.test(target);
	}

	/// @return 当前成功命中策略
	public HitboxHitPolicy hitPolicy() {
		return hitPolicy;
	}

	/// @param hitPolicy 新成功命中策略
	public void setHitPolicy(HitboxHitPolicy hitPolicy) {
		this.hitPolicy = hitPolicy;
	}

	/// @return 当前遮挡检查方式
	public HitboxLineOfSightMode lineOfSightMode() {
		return lineOfSightMode;
	}

	/// @param lineOfSightMode 新遮挡检查方式
	public void setLineOfSightMode(HitboxLineOfSightMode lineOfSightMode) {
		this.lineOfSightMode = lineOfSightMode;
	}

	/// 使实例从下一次 Level Tick Post 开始处理命中。
	public void activate() {
		if (!exhausted) {
			active = true;
		}
	}

	/// 停止命中处理，同时保留实例、寿命和历史。
	public void deactivate() {
		active = false;
	}

	/// @return 当前是否处于伤害阶段
	public boolean isActive() {
		return active;
	}

	/// @return 是否已经耗尽全局成功次数
	public boolean isExhausted() {
		return exhausted;
	}

	void exhaust() {
		exhausted = true;
	}

	/// @return 剩余存在 tick 数
	public int remainingTicks() {
		return remainingTicks;
	}

	/// 把剩余存在时间缩短或延长到指定 tick 数。
	///
	/// @param remainingTicks 正的剩余 tick 数
	public void setRemainingTicks(int remainingTicks) {
		if (remainingTicks <= 0) {
			throw new IllegalArgumentException("remainingTicks must be positive");
		}
		this.remainingTicks = remainingTicks;
	}

	void advanceLifetime() {
		remainingTicks--;
	}

	boolean isExpired() {
		return remainingTicks <= 0;
	}

	void updateFollowTransform() {
		if (!followsSource || source == null) {
			return;
		}
		Vec3 sourceCenter = source.getBoundingBox().getCenter();
		Vec3 sourceRotationOffset = HitboxGeometry.rotate(localOffset,
				source.getXRot(), -source.getYRot(), 0.0);
		Vec3 basePosition = source.position().add(sourceRotationOffset);
		Vec3 pivot = sourceCenter.add(HitboxGeometry.rotate(rotationPivot,
				source.getXRot(), -source.getYRot(), 0.0));
		position = pivot.add(HitboxGeometry.rotate(basePosition.subtract(pivot),
				localRotation.x, localRotation.y, localRotation.z));
		if (inheritsSourceRotation) {
			float sourceYaw = followsHeadYaw && source instanceof LivingEntity livingEntity
					? livingEntity.getYHeadRot() : source.getYRot();
			rotation = new Vec3(
					localRotation.x + source.getXRot(),
					localRotation.y - sourceYaw,
					localRotation.z
			);
		} else {
			rotation = localRotation;
		}
	}

	boolean hasInvalidSource() {
		return source != null
				&& (source.isRemoved() || !source.isAlive() || source.level() != level);
	}

	boolean canAttempt(Entity target, long gameTime) {
		HitRecord record = hitRecords.get(target.getUUID());
		int successful = record == null ? 0 : record.successfulHits();
		if (hitPolicy.perTargetMaximum() == 0) {
			return false;
		}
		if (hitPolicy.perTargetMaximum() > 0 && successful >= hitPolicy.perTargetMaximum()) {
			return false;
		}
		if (hitPolicy.mode() == HitboxHitMode.ONCE && successful > 0) {
			return false;
		}
		if (hitPolicy.mode() == HitboxHitMode.INTERVAL && record != null
				&& gameTime - record.lastSuccessfulGameTime() < hitPolicy.intervalTicks()) {
			return false;
		}
		return true;
	}

	void recordSuccess(Entity target, long gameTime) {
		HitRecord previous = hitRecords.get(target.getUUID());
		int successful = previous == null ? 1 : previous.successfulHits() + 1;
		hitRecords.put(target.getUUID(), new HitRecord(successful, gameTime));
		successfulHits++;
	}

	boolean reachedTotalMaximum() {
		return hitPolicy.totalMaximum() == 0
				|| hitPolicy.totalMaximum() > 0 && successfulHits >= hitPolicy.totalMaximum();
	}

	/// 保存只在服务端存在、不会同步或持久化的类型安全参数。
	///
	/// @param key 参数键
	/// @param value 参数值
	/// @param <T> 参数类型
	public <T> void setData(TypedDataKey<T> key, T value) {
		data.put(key, value);
	}

	/// 读取服务端临时参数。
	///
	/// @param key 参数键
	/// @param <T> 参数类型
	/// @return 已保存的值；未设置时返回 {@code null}
	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T getData(TypedDataKey<T> key) {
		return (T) data.get(key);
	}

	/// 移除并返回服务端临时参数。
	///
	/// @param key 参数键
	/// @param <T> 参数类型
	/// @return 被移除的值；未设置时返回 {@code null}
	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T removeData(TypedDataKey<T> key) {
		return (T) data.remove(key);
	}

	/// @return 实例是否已被管理器移除
	public boolean isRemoved() {
		return removed;
	}

	void markRemoved() {
		removed = true;
	}

	private record HitRecord(int successfulHits, long lastSuccessfulGameTime) {
	}
}
