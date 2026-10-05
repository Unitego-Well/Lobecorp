package org.unitego.lobecorp.config.photon.emitter;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.TransformRefConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.TransformRef;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.RandomCurve;
import com.lowdragmc.photon.gui.editor.view.FXHierarchyView;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTooltipUtil;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 发射调度、来源、变换继承和结束策略；旧发射器配置不受影响。
 */
public class PhotonEmitterSpawnConfig implements IConfigurable, IPersistedSerializable {
	/**
	 * 默认一次发射周期的长度，单位 tick。
	 */
	public static final int DEFAULT_DURATION = 100;
	/**
	 * 默认触发间隔，单位 tick。
	 */
	public static final int DEFAULT_INTERVAL = 20;
	/**
	 * 默认同时保留的生成实例数。
	 */
	public static final int DEFAULT_MAX_INSTANCES = 64;
	/**
	 * 默认每游戏 tick 最多新建的实例数。
	 */
	public static final int DEFAULT_MAX_PER_TICK = 16;
	/**
	 * 默认嵌套来源层数，限制异步递归发射。
	 */
	public static final int DEFAULT_MAX_DEPTH = 8;
	/**
	 * 与 Photon 每 tick 的积分子步上限一致的播放倍率上限。
	 */
	public static final int MAX_PLAYBACK_SPEED = 16;
	/**
	 * 单个模板子树的最大对象数，防止异常资源构造过多对象。
	 */
	public static final int MAX_TEMPLATE_OBJECTS = 4096;

	@Configurable(name = PhotonEmitterSpawnerTextUtil.ENABLED)
	public boolean enabled = true;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.SOURCE)
	@ConfigSelector(subConfiguratorBuilder = "buildSourceConfigurator")
	public Source source = Source.PROJECT;
	@Persisted
	public final TransformRef template = new TransformRef();
	@Nullable
	@Persisted
	public Identifier fxLocation;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.INCLUDE_CHILDREN)
	public boolean includeChildren = true;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.DURATION)
	@ConfigNumber(range = {1, Integer.MAX_VALUE})
	public int duration = DEFAULT_DURATION;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.LOOPING)
	public boolean looping = true;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.START_DELAY)
	@ConfigNumber(range = {0, Integer.MAX_VALUE})
	public int startDelay;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.MODE)
	public Mode mode = Mode.INTERVAL;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.INTERVAL)
	@ConfigNumber(range = {1, Integer.MAX_VALUE})
	public int interval = DEFAULT_INTERVAL;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.COUNT)
	@NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class}, numberType = ConfigNumber.Type.INTEGER, min = 0)
	public NumberFunction count = NumberFunction.constant(1);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.RATE)
	@NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class}, min = 0)
	public NumberFunction rate = NumberFunction.constant(1f / DEFAULT_INTERVAL);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.PROBABILITY)
	@NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class}, min = 0, max = 1)
	public NumberFunction probability = NumberFunction.constant(1);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.MAX_INSTANCES)
	@ConfigNumber(range = {0, Integer.MAX_VALUE})
	public int maxInstances = DEFAULT_MAX_INSTANCES;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.MAX_TOTAL)
	@ConfigNumber(range = {0, Integer.MAX_VALUE})
	public int maxTotal;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.MAX_PER_TICK)
	@ConfigNumber(range = {1, Integer.MAX_VALUE})
	public int maxPerTick = DEFAULT_MAX_PER_TICK;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.MAX_DEPTH)
	@ConfigNumber(range = {1, DEFAULT_MAX_DEPTH})
	public int maxDepth = DEFAULT_MAX_DEPTH;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.INSTANCE_LIFETIME)
	@ConfigNumber(range = {0, Integer.MAX_VALUE})
	public int instanceLifetime;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.END_BEHAVIOR)
	public EndBehavior endBehavior = EndBehavior.WAIT;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.CLEAR_ON_RESTART)
	public boolean clearOnRestart = true;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.FOLLOW)
	public boolean follow;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.INHERIT_ROTATION)
	public boolean inheritRotation = true;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.INHERIT_SCALE)
	public boolean inheritScale;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.INHERIT_COLOR)
	public boolean inheritColor;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.INHERIT_VELOCITY)
	public boolean inheritVelocity;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.LAUNCH_SPEED)
	@NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class})
	public NumberFunction launchSpeed = NumberFunction.constant(0);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.SHAPE, subConfigurable = true)
	public final PhotonEmitterSpawnShape shape = new PhotonEmitterSpawnShape();
	@Configurable(name = PhotonEmitterSpawnerTextUtil.PARAMETERS, subConfigurable = true)
	public final PhotonEmitterSpawnParameters parameters = new PhotonEmitterSpawnParameters();

	private void buildSourceConfigurator(Source value, ConfiguratorGroup group) {
		if (value == Source.FX) {
			buildFxConfigurator(group);
			return;
		}

		group.addConfigurator(new TransformRefConfigurator(PhotonEmitterSpawnerTextUtil.TEMPLATE,
				() -> template, ref -> template.setTransformId(ref.getTransformId()), new TransformRef(), true) {
			{
				var clear = new Button().setText(PhotonEmitterSpawnerTextUtil.CLEAR_TEMPLATE)
						.setOnClick(_ -> {
							onValueUpdatePassively(new TransformRef());
							updateValue();
						});
				clear.getStyle().tooltips(PhotonEmitterSpawnerTextUtil.CLEAR_TEMPLATE + PhotonEditorTooltipUtil.TOOLTIP_SUFFIX);
				addInlineChild(clear);
			}

			@Override
			protected boolean canDropObject(@NotNull Object object) {
				if (object instanceof FXHierarchyView.DraggingNode(var node)) {
					return node.key instanceof Emitter;
				}

				return object instanceof Emitter;
			}

			@Override
			protected void onDropObject(@NotNull Object object) {
				if (object instanceof FXHierarchyView.DraggingNode(var node)) {
					onValueUpdatePassively(new TransformRef(node.key.transform()));
					updateValue();
					return;
				}

				if (object instanceof Emitter emitter) {
					onValueUpdatePassively(new TransformRef(emitter.transform()));
					updateValue();
				}
			}
		});
	}

	private void buildFxConfigurator(ConfiguratorGroup group) {
		var candidates = new ArrayList<String>();
		candidates.add("");
		FXHelper.listAllFX().forEach(location -> candidates.add(location.toString()));
		group.addConfigurator(new SearchComponentConfigurator<>(PhotonEmitterSpawnerTextUtil.FX,
				() -> fxLocation == null ? "" : fxLocation.toString(),
				value -> fxLocation = value.isEmpty() ? null : Identifier.tryParse(value),
				"", true, (word, handler) -> {
			var search = word.toLowerCase(Locale.ROOT);
			for (var candidate : candidates) {
				if (Thread.currentThread().isInterrupted()) {
					return;
				}

				if (candidate.toLowerCase(Locale.ROOT).contains(search)) {
					handler.accept(candidate);
				}
			}
		}, value -> value, UIElementProvider.text(Component::literal)));
	}

	public enum Source implements StringRepresentable {
		PROJECT, FX;

		@Override
		public @NotNull String getSerializedName() {
			return PhotonEmitterSpawnerTextUtil.enumKey(this);
		}
	}

	public enum Mode implements StringRepresentable {
		ONCE, INTERVAL, RATE, ONE_SHOT;

		@Override
		public @NotNull String getSerializedName() {
			return PhotonEmitterSpawnerTextUtil.enumKey(this);
		}
	}

	public enum EndBehavior implements StringRepresentable {
		WAIT, STOP_EMISSION, CLEAR;

		@Override
		public @NotNull String getSerializedName() {
			return PhotonEmitterSpawnerTextUtil.enumKey(this);
		}
	}
}
