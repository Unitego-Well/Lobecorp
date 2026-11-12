package org.unitego.lobecorp.client.photon.runtime.creation;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXData;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/// 不可变模板程序：只持有已编译定义和静态来源元数据，不持有运行实例、场景或动态配置。
public record PhotonTemplateProgram(
		String key,
		PhotonRuntimeFactory.Definition definition,
		Source source,
		Set<String> path,
		@Nullable Identifier fxLocation,
		@Nullable UUID projectTarget,
		List<UUID> objectIds,
		boolean includeChildren,
		PhotonRuntimeFactory.Mode mode,
		@Nullable WeakReference<FXData> sourceData,
		@Nullable WeakReference<FX> sourceFx,
		@Nullable WeakReference<IScene> sourceScope,
		@Nullable Object structureKey,
		boolean reusable) {
	public PhotonTemplateProgram {
		Objects.requireNonNull(key);
		Objects.requireNonNull(definition);
		Objects.requireNonNull(source);
		Objects.requireNonNull(mode);
		path = Set.copyOf(path);
		objectIds = List.copyOf(objectIds);
		if (source == Source.FX && fxLocation == null) {
			throw new IllegalArgumentException("FX 模板程序缺少资源 ID。");
		}
		if (source == Source.PROJECT && projectTarget == null) {
			throw new IllegalArgumentException("PROJECT 模板程序缺少目标 ID。");
		}
	}

	public static PhotonTemplateProgram root(PhotonRuntimeFactory.Definition definition,
	                                         @Nullable Identifier fxLocation,
	                                         @Nullable FX source, @Nullable FXData sourceData, boolean reusable) {
		return new PhotonTemplateProgram(definition.key(), definition, Source.ROOT, Set.of(),
				fxLocation, null, definition.objectIds(), false, PhotonRuntimeFactory.mode(),
				sourceData == null ? null : new WeakReference<>(sourceData),
				source == null ? null : new WeakReference<>(source), null, null, reusable);
	}

	public static PhotonTemplateProgram project(PhotonRuntimeFactory.Definition definition,
	                                            @Nullable IScene scope, UUID target, boolean children,
	                                            Set<String> path, Object structureKey) {
		return new PhotonTemplateProgram(definition.key(), definition, Source.PROJECT, path,
				null, target, definition.objectIds(), children, PhotonRuntimeFactory.mode(),
				null, null, scope == null ? null : new WeakReference<>(scope), structureKey, true);
	}

	/// 仅按当前未绑定作者 FXData 重新准备可变子程序；受管来源仍必须身份相等。
	public static PhotonTemplateProgram fx(PhotonRuntimeFactory.Definition definition, Identifier location,
	                                       Set<String> path, @Nullable FX source, FXData data,
	                                       boolean reusable) {
		return new PhotonTemplateProgram(definition.key() + "/fx:" + location, definition, Source.FX,
				path, location, null, definition.objectIds(), false, PhotonRuntimeFactory.mode(),
				new WeakReference<>(data), source == null ? null : new WeakReference<>(source),
				null, null, reusable);
	}

	/// 不可变定义仍是根资产；来源键与路径属于当前子发射器，显式转为可执行 FX 子程序。
	public PhotonTemplateProgram asFxChild(Identifier location, Set<String> newPath) {
		return new PhotonTemplateProgram(key + "/fx:" + location, definition, Source.FX, newPath,
				location, null, objectIds, false, mode, sourceData, sourceFx, null, null, reusable);
	}

	/// 未登记来源只在当前调用内可消费，不能作为长期 prepared 槽缓存。
	public boolean reusable() {
		return reusable;
	}

	public PhotonRuntimeFactory.Lease acquire(Object world) {
		return PhotonRuntimeFactory.acquire(this, world);
	}

	public boolean current() {
		return PhotonRuntimeFactory.definitionCurrent(definition);
	}

	public boolean matchesFx(Identifier location, Set<String> expectedPath, FXData currentData) {
		return source == Source.FX && current() && Objects.equals(fxLocation, location)
				&& path.equals(expectedPath) && sourceData != null && sourceData.get() == currentData
				&& (!reusable || PhotonRuntimeFactory.sourceCurrent(sourceFx == null ? null : sourceFx.get()));
	}

	/// 未登记可变来源只在 prepare 调用内有效；首次消费必须按当前内容重新取得定义。
	public boolean transientFx() {
		return source == Source.FX && !reusable;
	}

	public boolean matchesProject(IScene scope, UUID target, boolean children, Set<String> expectedPath) {
		return source == Source.PROJECT && reusable && current() && Objects.equals(projectTarget, target)
				&& includeChildren == children && path.equals(expectedPath)
				&& sourceScope != null && sourceScope.get() == scope
				&& PhotonRuntimeFactory.projectProgramCurrent(this);
	}

	public @Nullable IScene sourceScopeValue() {
		return sourceScope == null ? null : sourceScope.get();
	}

	public enum Source {
		ROOT, FX, PROJECT
	}
}
