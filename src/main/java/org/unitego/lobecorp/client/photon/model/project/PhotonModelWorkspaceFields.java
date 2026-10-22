package org.unitego.lobecorp.client.photon.model.project;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.Consumer;

import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import com.google.gson.JsonPrimitive;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import net.minecraft.network.chat.Component;

/// 按当前模型结构显示原生属性控件；未知数据只展示，不隐式重写身份或结构。
public class PhotonModelWorkspaceFields {
	public static void build(UIElement parent, PhotonModelWorkspaceSession session, String path) {
		var value = session.value(path);
		if (value == null || value.isJsonNull()) {
			return;
		}
		if (value.isJsonObject()) {
			for (var entry : value.getAsJsonObject().entrySet()) {
				field(parent, session, path + "/" + encode(entry.getKey()), entry.getKey(), entry.getValue());
			}
		} else if (value.isJsonArray()) {
			for (int index = 0; index < value.getAsJsonArray().size(); index++) {
				field(parent, session, path + "/" + index, Integer.toString(index), value.getAsJsonArray().get(index));
			}
		} else {
			field(parent, session, path, path.substring(path.lastIndexOf('/') + 1), value);
		}
	}

	private static void field(UIElement parent, PhotonModelWorkspaceSession session, String path, String name, JsonElement value) {
		if (value.isJsonArray() && (List.of("translation", "pivot", "scale", "rotation").contains(name)
				|| path.contains("/deltas/")) && value.getAsJsonArray().asList().stream().allMatch(JsonElement::isJsonPrimitive)) {
			vector(parent, session, path, name, value.getAsJsonArray());
			return;
		}
		if (!value.isJsonPrimitive()) {
			var group = new ConfiguratorGroup(name, false);
			parent.addChild(group);
			group.configuratorContainer(container -> build(container, session, path));
			return;
		}
		var primitive = value.getAsJsonPrimitive();
		if (name.equals("id") || name.equals("version")) {
			parent.addChild(new TextElement().setText(Component.literal(name + ": " + primitive.getAsString())));
		} else if (primitive.isBoolean()) {
			parent.addChild(new BooleanConfigurator(name, () -> session.valueOr(path, primitive).getAsBoolean(),
					next -> session.set(path, new JsonPrimitive(next)), primitive.getAsBoolean(), false));
		} else if (primitive.isNumber()) {
			parent.addChild(new NumberConfigurator(name, () -> session.valueOr(path, primitive).getAsDouble(),
					next -> session.set(path, new JsonPrimitive(next)), primitive.getAsDouble(), false));
		} else {
			parent.addChild(new StringConfigurator(name, () -> session.valueOr(path, primitive).getAsString(),
					next -> session.set(path, new JsonPrimitive(next)), primitive.getAsString(), false));
		}
	}

	private static void vector(UIElement parent, PhotonModelWorkspaceSession session, String path, String name, JsonArray initial) {
		boolean rotation = name.equals("rotation") && initial.size() == 4;
		if (!rotation && initial.size() != 3) {
			return;
		}
		Supplier<Vector3f> getter = () -> {
			var values = session.valueOr(path, initial).getAsJsonArray();
			if (rotation) {
				var angles = new Quaternionf(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat(), values.get(3).getAsFloat())
						.normalize().getEulerAnglesXYZ(new Vector3f());
				return angles.mul((float) (180 / Math.PI));
			}
			return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
		};
		Consumer<Vector3f> setter = next -> {
			var values = new JsonArray();
			if (rotation) {
				var quaternion = new Quaternionf().rotationXYZ((float) Math.toRadians(next.x), (float) Math.toRadians(next.y), (float) Math.toRadians(next.z));
				values.add(quaternion.x);
				values.add(quaternion.y);
				values.add(quaternion.z);
				values.add(quaternion.w);
			} else {
				values.add(next.x);
				values.add(next.y);
				values.add(next.z);
			}
			session.set(path, values);
		};
		String label = PhotonModelTextUtil.key(name.equals("translation") ? "position" : name);
		var row = new Configurator(label);
		String[] axes = {"x", "y", "z"};
		for (int index = 0; index < 3; index++) {
			int component = index;
			var number = new NumberConfigurator(axes[index], () -> getter.get().get(component), next -> {
				var value = getter.get();
				value.setComponent(component, next.floatValue());
				setter.accept(value);
			}, name.equals("scale") ? 1 : 0, false).setRange(-Float.MAX_VALUE, Float.MAX_VALUE);
			number.layout(layout -> layout.flex(1).minWidth(40).height(14));
			row.inlineContainer.addChild(number);
		}
		row.inlineContainer.layout(layout -> layout.gapAll(2).marginLeft(2).flexDirection(FlexDirection.ROW).wrap(FlexWrap.WRAP));
		row.setCopiable(() -> {
			var value = new Vector3f(getter.get());
			return () -> new Vector3f(value);
		});
		row.setPastable(Vector3f.class, setter);
		parent.addChild(row);
	}

	public static String encode(String value) {
		return value.replace("~", "~0").replace("/", "~1");
	}
}
