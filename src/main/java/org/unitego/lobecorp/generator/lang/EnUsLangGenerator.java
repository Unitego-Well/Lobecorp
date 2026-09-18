package org.unitego.lobecorp.generator.lang;

import net.minecraft.data.PackOutput;
import org.unitego.lobecorp.Lobecorp;

public class EnUsLangGenerator extends BasicLangGenerator {
	public EnUsLangGenerator(PackOutput output) {
		super(output, Lobecorp.NAMESPACE, LangHandler.EN_US);
	}

	@Override
	public void addTranslations() {
		super.addTranslations();
		addPackDescription(Lobecorp.NAMESPACE, "Lobotomy Corporation");
	}
}
