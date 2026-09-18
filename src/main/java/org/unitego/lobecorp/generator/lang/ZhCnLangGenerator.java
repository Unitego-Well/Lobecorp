package org.unitego.lobecorp.generator.lang;

import net.minecraft.data.PackOutput;
import org.unitego.lobecorp.Lobecorp;

public class ZhCnLangGenerator extends BasicLangGenerator {
	public ZhCnLangGenerator(PackOutput output) {
		super(output, Lobecorp.NAMESPACE, LangHandler.ZH_CN);
	}

	@Override
	public void addTranslations() {
		super.addTranslations();
		addPackDescription(Lobecorp.NAMESPACE, "脑叶公司");
	}
}
