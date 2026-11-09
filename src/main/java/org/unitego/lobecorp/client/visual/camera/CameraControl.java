package org.unitego.lobecorp.client.visual.camera;

import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/// 客户端线程使用的相机所有权入口；同优先级后注册者优先，owner 按对象身份匹配。
/// 有效期只控制参与选择，不删除注册；生命周期结束须 unregister，世界退出可 clear。
public class CameraControl {
	private static final List<Provider> PROVIDERS = new ArrayList<>();

	public static void register(Object owner, int priority, BooleanSupplier valid, ICameraController controller) {
		Objects.requireNonNull(owner);
		Objects.requireNonNull(valid);
		Objects.requireNonNull(controller);
		unregister(owner);
		PROVIDERS.add(new Provider(owner, priority, valid, controller));
	}

	public static void unregister(Object owner) {
		PROVIDERS.removeIf(provider -> provider.owner() == owner);
	}

	public static void clear() {
		PROVIDERS.clear();
	}

	public static @Nullable Frame independent(float partial) {
		var controller = controller();
		return controller == null ? null : controller.independent(partial);
	}

	public static Vec3 position(Vec3 normal, float partial) {
		var controller = controller();
		return controller == null ? normal : controller.position(normal, partial);
	}

	public static void angles(ViewportEvent.ComputeCameraAngles event) {
		var controller = controller();
		if (controller != null) {
			controller.angles(event);
		}
	}

	public static void fov(ViewportEvent.ComputeFov event) {
		var controller = controller();
		if (controller != null) {
			controller.fov(event);
		}
	}

	private static @Nullable ICameraController controller() {
		Provider selected = null;
		for (var provider : List.copyOf(PROVIDERS)) {
			if (provider.valid().getAsBoolean() && (selected == null || provider.priority() >= selected.priority())) {
				selected = provider;
			}
		}

		return selected == null ? null : selected.controller();
	}

	public record Frame(Vec3 position, float yaw, float pitch, float roll) {
	}

	private record Provider(Object owner, int priority, BooleanSupplier valid, ICameraController controller) {
	}
}
