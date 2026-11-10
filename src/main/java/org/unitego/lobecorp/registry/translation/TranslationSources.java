package org.unitego.lobecorp.registry.translation;

import org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslations;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEmitterSpawnerTranslations;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEffectLibraryTranslations;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTooltipTranslations;
import org.unitego.lobecorp.registry.translation.photon.model.PhotonModelTranslations;
import org.unitego.lobecorp.registry.translation.photon.performance.PhotonPerformanceTranslations;
import org.unitego.lobecorp.registry.translation.photon.visual.PhotonVisualQualityTranslations;

import java.util.List;

/// DataGen加载未被运行代码引用的常量声明，不保留第二份文案或重放登记过程。
public class TranslationSources {
	private static final List<Class<?>> sources = List.of(
			PhotonAnimationTranslations.class, PhotonEditorTranslations.class,
			PhotonEmitterSpawnerTranslations.class, PhotonEffectLibraryTranslations.class,
			PhotonEditorTooltipTranslations.class, PhotonModelTranslations.class,
			PhotonPerformanceTranslations.class, PhotonVisualQualityTranslations.class,
			PhotonTranslationKeys.class,
			CommandTranslationKeys.class,
			ConductorTranslationKeys.class,
			DebugTranslationKeys.class,
			EntityTranslationKeys.class,
			PackTranslationKeys.class,
			VisualTranslationKeys.class,
			ConfigTranslationKeys.class,
			ClientConfigTranslationKeys.class,
			ServerConfigTranslationKeys.class,
			HordeConfigTranslationKeys.class,
			HordeTranslationKeys.class,
			EncounterTranslationKeys.class,
			MidnightCleanupTranslationKeys.class);

	public static void load() {
		for (var source : sources) {
			try {
				Class.forName(source.getName(), true, source.getClassLoader());
			} catch (ClassNotFoundException exception) {
				throw new IllegalStateException("翻译常量类型不可加载：" + source.getName(), exception);
			}
		}
	}
}
