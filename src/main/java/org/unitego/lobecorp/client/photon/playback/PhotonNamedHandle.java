package org.unitego.lobecorp.client.photon.playback;

import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Parameter;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.ParameterType;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 句柄独占时钟、形参与跟随关系；制作定义和其他实例不接收运行修改。
public class PhotonNamedHandle {
	private record Follow(WeakReference<PhotonNamedHandle> source, UUID parameter) {
	}

	private final UUID id = UUID.randomUUID();
	private final Definition definition;
	private final double authoredDuration;
	private final long order;
	private final PhotonPlaybackClock clock;
	private final Map<String, Parameter> parameterIndex = new LinkedHashMap<>();
	private final Map<UUID, List<Float>> parameters = new LinkedHashMap<>();
	private final Map<UUID, Follow> following = new LinkedHashMap<>();
	private @Nullable Map<UUID, List<Float>> endedValues;
	private boolean durationOverride;
	private final boolean controlled;
	private double controlledPosition;
	private boolean disposed;

	public PhotonNamedHandle(Definition definition, double authoredDuration, long order, double requestedDuration,
	                         boolean loop, boolean controlled, Map<String, List<Float>> inputs) {
		this.definition = definition;

		if (!Double.isFinite(authoredDuration) || authoredDuration < 0) {
			throw new IllegalArgumentException("命名播放制作时长无效。");
		}

		this.authoredDuration = authoredDuration;
		this.order = order;
		this.controlled = controlled;

		for (var parameter : definition.parameters()) {
			parameterIndex.put(parameter.id().toString(), parameter);
			parameterIndex.put(parameter.name(), parameter);
			parameters.put(parameter.id(), parameter.defaults());
		}

		parameters.putAll(validate(inputs));
		durationOverride = requestedDuration >= 0 || controlled;
		double duration = requestedDuration >= 0 ? requestedDuration : controlled
				? definition.autoDuration() ? authoredDuration : definition.duration() : parameterDuration(parameters);

		if (!Double.isFinite(requestedDuration) || requestedDuration < 0 && requestedDuration != -1) {
			throw new IllegalArgumentException("命名播放请求时长无效。");
		}

		clock = new PhotonPlaybackClock(duration, controlled ? false : loop);
	}

	public UUID id() {
		return id;
	}

	public Definition definition() {
		return definition;
	}

	public long order() {
		return order;
	}

	public PhotonPlaybackClock clock() {
		return clock;
	}

	public boolean controlled() {
		return controlled;
	}

	public boolean disposed() {
		return disposed;
	}

	public List<Float> parameter(String idOrName) {
		var definition = parameterDefinition(idOrName);
		return parameters.get(definition.id());
	}

	public Map<UUID, List<Float>> parameters() {
		return Map.copyOf(parameters);
	}

	public void parameters(Map<String, List<Float>> changes) {
		requireCurrent();
		var values = new LinkedHashMap<>(parameters);
		values.putAll(validate(changes));
		double duration = durationOverride ? clock.duration() : parameterDuration(values);
		validateDuration(duration);
		parameters.clear();
		parameters.putAll(values);

		if (!durationOverride && Double.compare(duration, clock.duration()) != 0) {
			clock.duration(duration);
		}
	}

	public void duration(double value) {
		requireCurrent();
		validateDuration(value);
		clock.duration(value);
		durationOverride = true;
	}

	public void follow(String target, PhotonNamedHandle source, String parameter) {
		requireCurrent();

		if (source == this) {
			throw new IllegalArgumentException("形参不能跟随同一播放句柄。");
		}

		var targetDefinition = parameterDefinition(target);
		var sourceDefinition = source.parameterDefinition(parameter);
		targetDefinition.coerce(source.parameters.get(sourceDefinition.id()));
		following.put(targetDefinition.id(), new Follow(new WeakReference<>(source), sourceDefinition.id()));
	}

	public double advance(double ticks) {
		requireCurrent();
		refreshFollowing();
		double previous = controlled ? controlledPosition : clock.advance(ticks);
		freezeEndedValues();
		return previous;
	}

	public void drive(double position) {
		requireCurrent();

		if (!controlled || !Double.isFinite(position) || position < 0 || position > 1) {
			throw new IllegalArgumentException("受控命名播放位置无效。");
		}

		controlledPosition = position;
		clock.seek(position);
	}

	public void completeControlled() {
		requireCurrent();

		if (!controlled) {
			throw new IllegalStateException("该播放不是受控子片段。");
		}

		controlledPosition = 1;
		clock.complete();
		freezeEndedValues();
	}

	public double progress(double partial) {
		return controlled ? controlledPosition : clock.progress(partial);
	}

	public double authoredTime(double partial) {
		return progress(partial) * authoredDuration;
	}

	public double authoredDuration() {
		return authoredDuration;
	}

	public double position(double partial) {
		return controlled ? controlledPosition : clock.position(partial);
	}

	public long loops(double partial) {
		return controlled ? 0 : clock.loops(partial);
	}

	public void dispose() {
		if (!disposed) {
			freezeEndedValues();
			endedValues = Map.copyOf(parameters);
			disposed = true;
			clock.stop();
			following.clear();
		}
	}

	private void refreshFollowing() {
		if (following.isEmpty()) {
			return;
		}

		var changed = new LinkedHashMap<String, List<Float>>();

		for (var entry : following.entrySet()) {
			var source = entry.getValue().source().get();

			if (source == null) {
				continue;
			}

			var sourceValues = source.endedValues == null ? source.parameters : source.endedValues;
			var value = sourceValues.get(entry.getValue().parameter());

			if (value != null) {
				changed.put(entry.getKey().toString(), value);
			}
		}

		parameters(changed);
	}

	private void freezeEndedValues() {
		if (endedValues == null && (clock.state() == PhotonPlaybackClock.State.HOLDING
				|| clock.state() == PhotonPlaybackClock.State.STOPPED)) {
			endedValues = Map.copyOf(parameters);
		}
	}

	private Map<UUID, List<Float>> validate(Map<String, List<Float>> values) {
		var result = new LinkedHashMap<UUID, List<Float>>();

		for (var entry : values.entrySet()) {
			var parameter = parameterDefinition(entry.getKey());
			result.put(parameter.id(), parameter.coerce(entry.getValue()));
		}

		return result;
	}

	private Parameter parameterDefinition(String name) {
		var value = parameterIndex.get(name);

		if (value == null) {
			throw new IllegalArgumentException("命名动画形参不存在：" + name);
		}

		return value;
	}

	private double parameterDuration(Map<UUID, List<Float>> values) {
		if (definition.durationParameter().isEmpty()) {
			return definition.autoDuration() ? authoredDuration : definition.duration();
		}

		var parameter = parameterDefinition(definition.durationParameter());

		if (parameter.type().channels() != 1 || parameter.type() == ParameterType.BOOLEAN
				|| parameter.type() == ParameterType.COLOR) {
			throw new IllegalArgumentException("时长形参必须为单通道数值。");
		}

		return values.get(parameter.id()).getFirst();
	}

	private void validateDuration(double value) {
		if (!Double.isFinite(value) || value < 0 || !controlled && clock.looping() && value == 0) {
			throw new IllegalArgumentException("命名播放时长无效。");
		}
	}

	private void requireCurrent() {
		if (disposed) {
			throw new IllegalStateException("命名播放句柄已释放。");
		}
	}
}
