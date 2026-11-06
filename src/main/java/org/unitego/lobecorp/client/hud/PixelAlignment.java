package org.unitego.lobecorp.client.hud;

public enum PixelAlignment {
	INHERIT, NONE, ROUND, FLOOR, CEIL;

	public float apply(float value) {
		return switch (this) {
			case ROUND -> Math.round(value);
			case FLOOR -> (float) Math.floor(value);
			case CEIL -> (float) Math.ceil(value);
			default -> value;
		};
	}
}
