package org.unitego.lobecorp.registry.client;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.unitego.lobecorp.Lobecorp;

public class VisualQualityConfigScreens {
	public static void register() {
		ModList.get().getModContainerById(Lobecorp.NAMESPACE).orElseThrow()
				.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
	}
}
