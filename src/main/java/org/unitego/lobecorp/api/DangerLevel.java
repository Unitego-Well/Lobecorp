package org.unitego.lobecorp.api;

public enum DangerLevel {
	ZAYIN(0, 1, "zayin", "#00ff00"),
	TETH(1, 2, "teth", "#1e90ff"),
	HE(2, 3, "he", "#ffff00"),
	WAW(3, 4, "waw", "#8a2be2"),
	ALEPH(4, 5, "aleph", "#ff0000");

	private final int id;
	private final int levelValue;
	private final String name;
	private final String colour;

	DangerLevel(int id, int levelValue, String name, String colour) {
		this.id = id;
		this.levelValue = levelValue;
		this.name = name;
		this.colour = colour;
	}

	public int getId() {
		return id;
	}

	public int getLevelValue() {
		return levelValue;
	}

	public String getName() {
		return name;
	}

	public String getColour() {
		return colour;
	}
}
