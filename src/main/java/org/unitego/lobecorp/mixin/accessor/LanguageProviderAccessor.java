package org.unitego.lobecorp.mixin.accessor;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LanguageProvider.class, remap = false)
public interface LanguageProviderAccessor {
	@Accessor("locale")
	String lobecorp$getLocale();

	@Accessor("output")
	PackOutput lobecorp$getOutput();
}
