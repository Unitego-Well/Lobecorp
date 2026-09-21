package org.unitego.lobecorp.entity.ai.movement;

/// 憎恶女皇当前采用的移动执行模式。
public enum TheQueenOfHatredMovementMode {
	GROUND(0),
	HOVER(1),
	THREE_DIMENSIONAL_PURSUIT(2),
	LANDING(3);

	private static final TheQueenOfHatredMovementMode[] VALUES = values();

	private final int id;

	TheQueenOfHatredMovementMode(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}

	public boolean isHovering() {
		return this == HOVER || this == THREE_DIMENSIONAL_PURSUIT;
	}

	public static TheQueenOfHatredMovementMode byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : GROUND;
	}
}
