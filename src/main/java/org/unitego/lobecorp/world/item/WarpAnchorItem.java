package org.unitego.lobecorp.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.registry.world.item.LcDataComponents;
import org.unitego.lobecorp.registry.world.item.LcItems;
import org.unitego.lobecorp.registry.translation.WarpTranslationKeys;
import org.unitego.lobecorp.world.item.component.WarpAnchor;

import java.util.Optional;

public class WarpAnchorItem extends Item {
	/// 五秒蓄力，单位为游戏刻。
	public static final int CHARGE_TICKS = 100;
	/// 使用者脚下为底面中心，水平半径五点五格，高度十一格。
	public static final double HALF_RANGE = 5.5;

	public WarpAnchorItem(Properties properties) {
		super(properties);
	}

	private static ItemStack findFuel(Player player) {
		for (var stack : player.getInventory()) {
			if (stack.is(LcItems.CANNED_ENKEPHALIN.get())) {
				return stack;
			}
		}

		return ItemStack.EMPTY;
	}

	private static void message(Player player, String key) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.translatable(key), true);
		}
	}

	private static boolean clear(ItemStack stack, WarpAnchor data, ServerPlayer player) {
		if (data.returnPoint().isPresent()) {
			stack.set(LcDataComponents.WARP_ANCHOR.get(), data.clearReturnPoint());
			message(player, WarpTranslationKeys.RETURN_CLEARED);
			return true;
		}

		if (data.anchor().isEmpty()) {
			message(player, WarpTranslationKeys.NO_DESTINATION);
			return false;
		}

		if (!data.anchorEditable()) {
			message(player, WarpTranslationKeys.ANCHOR_LOCKED);
			return false;
		}

		stack.set(LcDataComponents.WARP_ANCHOR.get(), data.clearAnchor());
		message(player, WarpTranslationKeys.CLEARED);
		return true;
	}

	private static boolean activate(ItemStack stack, WarpAnchor data, ServerPlayer player) {
		if (data.returnPoint().isPresent()) {
			if (!transfer(data.returnPoint().get(), player)) {
				return false;
			}

			stack.set(LcDataComponents.WARP_ANCHOR.get(), data.clearReturnPoint());
			return true;
		}

		if (data.anchor().isEmpty()) {
			if (!data.anchorEditable()) {
				message(player, WarpTranslationKeys.ANCHOR_LOCKED);
				return false;
			}

			stack.set(LcDataComponents.WARP_ANCHOR.get(), data.withAnchor(WarpAnchor.Destination.capture(player)));
			message(player, WarpTranslationKeys.RECORDED);
			return true;
		}

		// 必须在跨维度或视角变化前捕获；只有使用者成功传送后才提交返记录。
		var departure = WarpAnchor.Destination.capture(player);
		if (!transfer(data.anchor().get(), player)) {
			return false;
		}

		stack.set(LcDataComponents.WARP_ANCHOR.get(), data.withReturnPoint(departure));
		return true;
	}

	private static Optional<WarpAnchor.Location> resolveLanding(WarpAnchor.Destination destination, ServerLevel level, ServerPlayer player) {
		if (destination.location().isPresent()) {
			return destination.location();
		}

		var column = BlockPos.containing(player.getX(), 0, player.getZ());
		if (!level.getWorldBorder().isWithinBounds(column)) {
			return Optional.empty();
		}

		level.getChunkAt(column);
		var surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		var ground = surface.below();
		var state = level.getBlockState(ground);
		if (!state.getFluidState().isEmpty() || !state.isFaceSturdy(level, ground, Direction.UP)) {
			return Optional.empty();
		}

		var position = new Vec3(player.getX(), surface.getY(), player.getZ());
		return Optional.of(new WarpAnchor.Location(position, player.getYRot(), player.getXRot()));
	}

	private static boolean canLand(ServerLevel level, Entity entity, Vec3 position) {
		var bounds = entity.getBoundingBox().move(position.subtract(entity.position()));
		return !level.isOutsideBuildHeight(BlockPos.containing(position))
				&& !level.isOutsideBuildHeight(BlockPos.containing(position.x, Math.nextDown(bounds.maxY), position.z))
				&& level.getWorldBorder().isWithinBounds(bounds) && level.noCollision(entity, bounds, true);
	}

	private static boolean transfer(WarpAnchor.Destination destination, ServerPlayer player) {
		var level = player.level().getServer().getLevel(destination.dimension());
		if (level == null) {
			message(player, WarpTranslationKeys.UNAVAILABLE);
			return false;
		}

		var resolved = resolveLanding(destination, level, player);
		if (resolved.isEmpty()) {
			message(player, WarpTranslationKeys.UNAVAILABLE);
			return false;
		}

		var landing = resolved.get();
		var center = player.position();
		var bounds = new AABB(center.x - HALF_RANGE, center.y, center.z - HALF_RANGE,
				center.x + HALF_RANGE, center.y + 2 * HALF_RANGE, center.z + HALF_RANGE);
		var entities = player.level().getEntities(player, bounds, entity -> !entity.isRemoved() && bounds.contains(entity.position()));
		var transfers = entities.stream().map(entity -> new EntityTransfer(entity,
				landing.position().add(entity.position().subtract(center)), entity.getYRot(), entity.getXRot())).toList();
		if (destination.location().isEmpty() && (!canLand(level, player, landing.position())
				|| transfers.stream().anyMatch(transfer -> !canLand(level, transfer.entity(), transfer.position())))) {
			message(player, WarpTranslationKeys.UNAVAILABLE);
			return false;
		}

		// 先解除骑乘，避免原版递归传送乘客导致重复转移或带走范围外实体。
		entities.forEach(entity -> {
			entity.stopRiding();
			entity.ejectPassengers();
		});
		player.stopRiding();
		player.ejectPassengers();
		var transition = new TeleportTransition(level, landing.position(), Vec3.ZERO,
				landing.yaw(), landing.pitch(), TeleportTransition.PLACE_PORTAL_TICKET);
		if (player.teleport(transition) == null) {
			return false;
		}

		for (var transfer : transfers) {
			transfer.entity().teleport(transition.withPosition(transfer.position()).withRotation(transfer.yaw(), transfer.pitch()));
		}

		return true;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		var stack = player.getItemInHand(hand);
		if (findFuel(player).isEmpty()) {
			if (!level.isClientSide()) {
				message(player, WarpTranslationKeys.FUEL_REQUIRED);
			}
			return InteractionResult.FAIL;
		}

		player.startUsingItem(hand);
		if (!player.isUsingItem()) {
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide()) {
			var data = stack.getOrDefault(LcDataComponents.WARP_ANCHOR.get(), WarpAnchor.EMPTY);
			stack.set(LcDataComponents.WARP_ANCHOR.get(), data.beginCharge(player, player.isShiftKeyDown()));
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		if (level.isClientSide()) {
			return;
		}

		var data = stack.getOrDefault(LcDataComponents.WARP_ANCHOR.get(), WarpAnchor.EMPTY);
		if (data.hasMoved(user)) {
			stack.set(LcDataComponents.WARP_ANCHOR.get(), data.finishCharge());
			user.stopUsingItem();
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return CHARGE_TICKS;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingTime) {
		if (!level.isClientSide()) {
			var data = stack.getOrDefault(LcDataComponents.WARP_ANCHOR.get(), WarpAnchor.EMPTY);
			stack.set(LcDataComponents.WARP_ANCHOR.get(), data.finishCharge());
		}
		return false;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (!(user instanceof ServerPlayer player)) {
			return stack;
		}

		var data = stack.getOrDefault(LcDataComponents.WARP_ANCHOR.get(), WarpAnchor.EMPTY);
		if (data.hasMoved(player)) {
			stack.set(LcDataComponents.WARP_ANCHOR.get(), data.finishCharge());
			return stack;
		}

		boolean clearing = data.clearing();
		data = data.finishCharge();
		stack.set(LcDataComponents.WARP_ANCHOR.get(), data);
		var fuel = findFuel(player);
		if (fuel.isEmpty()) {
			message(player, WarpTranslationKeys.FUEL_REQUIRED);
			return stack;
		}

		boolean completed = clearing ? clear(stack, data, player) : activate(stack, data, player);
		if (completed) {
			fuel.shrink(1);
		}
		return stack;
	}

	private record EntityTransfer(Entity entity, Vec3 position, float yaw, float pitch) {
	}
}
