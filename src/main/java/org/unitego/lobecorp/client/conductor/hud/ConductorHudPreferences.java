package org.unitego.lobecorp.client.conductor.hud;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.unitego.lobecorp.conductor.data.ConductorData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class ConductorHudPreferences {
	private static final String FILE_NAME = "lobecorp-conductor-hud.properties";
	private static final String TEMP_SUFFIX = ".tmp";
	private static final String ROSTER_KEY = "rosterCollapsed";
	private static final String SKILLS_KEY = "skillsCollapsed";
	private static final String MOTION_KEY = "reduceMotion";
	private static final String FORMATION_KEY = "formationMode";
	private static final String READ_FAILURE = "Could not read conductor HUD preferences";
	private static final String WRITE_FAILURE = "Could not save conductor HUD preferences";

	public boolean rosterCollapsed;
	public boolean skillsCollapsed;
	public boolean reduceMotion;
	public ConductorData.FormationMode formationMode = ConductorData.FormationMode.FORMATION;

	public static ConductorHudPreferences load() {
		ConductorHudPreferences result = new ConductorHudPreferences();
		Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
		if (!Files.isRegularFile(path))
			return result;
		Properties values = new Properties();
		try (var reader = Files.newBufferedReader(path)) {
			values.load(reader);
			result.rosterCollapsed = Boolean.parseBoolean(values.getProperty(ROSTER_KEY));
			result.skillsCollapsed = Boolean.parseBoolean(values.getProperty(SKILLS_KEY));
			result.reduceMotion = Boolean.parseBoolean(values.getProperty(MOTION_KEY));
			result.formationMode = ConductorData.FormationMode.valueOf(values.getProperty(FORMATION_KEY, result.formationMode.name()));
		} catch (IOException | IllegalArgumentException exception) {
			LogUtils.getLogger().warn(READ_FAILURE, exception);
		}
		return result;
	}

	public void save() {
		Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
		Path temporary = path.resolveSibling(FILE_NAME + TEMP_SUFFIX);
		Properties values = new Properties();
		values.setProperty(ROSTER_KEY, Boolean.toString(rosterCollapsed));
		values.setProperty(SKILLS_KEY, Boolean.toString(skillsCollapsed));
		values.setProperty(MOTION_KEY, Boolean.toString(reduceMotion));
		values.setProperty(FORMATION_KEY, formationMode.name());
		try {
			Files.createDirectories(path.getParent());
			try (var writer = Files.newBufferedWriter(temporary)) {
				values.store(writer, null);
			}
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			LogUtils.getLogger().warn(WRITE_FAILURE, exception);
		}
	}
}
