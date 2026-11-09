package org.unitego.lobecorp.client.visual.input;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.unitego.lobecorp.mixin.input.client.ClientInputAccessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;
import java.util.function.BooleanSupplier;

/// 各功能独立持有控制权；释放一个来源不会解除其它来源的限制。
public class PlayerInteractionControl {

	private static final Map<Object, Request> requests = new LinkedHashMap<>();
	private static final Set<Integer> swallowed = new HashSet<>();

	public record Policy(boolean keyboard, boolean mouse, boolean movement, boolean turn) {
		public static final Policy CINEMATIC = new Policy(true, true, true, true);
		public static final Policy OBSERVER = new Policy(false, false, true, false);
	}

	private record Request(int priority, BooleanSupplier valid, Policy policy, Runnable escape) {
	}

	public static void acquire(Object owner, int priority, BooleanSupplier valid, Policy policy, Runnable escape) {
		requests.put(owner, new Request(priority, valid, policy, escape));
		KeyMapping.releaseAll();
		var minecraft = Minecraft.getInstance();
		if (policy.mouse() && minecraft.player != null && minecraft.gameMode != null) {
			if (minecraft.player.isUsingItem()) {
				minecraft.gameMode.releaseUsingItem(minecraft.player);
			}
			minecraft.gameMode.stopDestroyBlock();
		}
	}

	public static void release(Object owner) {
		if (requests.remove(owner) != null) {
			KeyMapping.releaseAll();
		}
	}

	private static void prune() {
		for (var entry : List.copyOf(requests.entrySet())) {
			if (!entry.getValue().valid().getAsBoolean()) {
				requests.remove(entry.getKey(), entry.getValue());
			}
		}
	}

	public static boolean keyboardBlocked() {
		prune();
		return requests.values().stream().anyMatch(request -> request.policy().keyboard());
	}

	public static boolean mouseBlocked() {
		prune();
		return requests.values().stream().anyMatch(request -> request.policy().mouse());
	}

	public static boolean turnBlocked() {
		prune();
		return requests.values().stream().anyMatch(request -> request.policy().turn());
	}

	public static boolean keyboard(long window, int action, KeyEvent event) {
		if (window != Minecraft.getInstance().getWindow().handle()) {
			return false;
		}
		if (swallowed.contains(event.key())) {
			if (action == InputConstants.RELEASE) {
				swallowed.remove(event.key());
			}
			return true;
		}
		if (!keyboardBlocked()) {
			return false;
		}
		if (action != InputConstants.RELEASE) {
			swallowed.add(event.key());
		}

		KeyMapping.releaseAll();
		if (action == InputConstants.PRESS && event.isEscape()) {
			requests.values().stream().filter(value -> value.policy().keyboard())
					.max(Comparator.comparingInt(Request::priority)).ifPresent(request -> request.escape().run());
		}
		return true;
	}

	public static void movement(MovementInputUpdateEvent event) {
		prune();
		if (requests.values().stream().noneMatch(request -> request.policy().movement())) {
			return;
		}

		var input = event.getInput();
		input.keyPresses = Input.EMPTY;
		((ClientInputAccessor) input).lobecorp$setMoveVector(Vec2.ZERO);
	}

	public static void clear() {
		requests.clear();
		swallowed.clear();
		KeyMapping.releaseAll();
	}
}
