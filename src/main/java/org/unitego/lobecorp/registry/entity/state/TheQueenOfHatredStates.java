package org.unitego.lobecorp.registry.entity.state;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.world.entity.state.EntityState;

import static org.unitego.lobecorp.Lobecorp.id;

public interface TheQueenOfHatredStates {
	/// 女皇姿态状态的互斥分组标识。
	Identifier POSTURE = id("the_queen_of_hatred/posture");

	/// 女皇坐姿状态，属于姿态互斥分组。
	EntityState SITTING = new EntityState(id("the_queen_of_hatred/sitting"), POSTURE);
	/// 女皇边缘坐姿状态，属于姿态互斥分组。
	EntityState SITTING_EDGE = new EntityState(id("the_queen_of_hatred/sitting_edge"), POSTURE);
	/// 女皇普通坐姿退出状态，属于姿态互斥分组。
	EntityState SITTING_FADE_OUT = new EntityState(id("the_queen_of_hatred/sitting_fade_out"), POSTURE);
	/// 女皇边缘坐姿退出状态，属于姿态互斥分组。
	EntityState SITTING_EDGE_FADE_OUT = new EntityState(id("the_queen_of_hatred/sitting_edge_fade_out"), POSTURE);
}
