package org.unitego.lobecorp.registry.entity_state;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.entity.entity_state.EntityState;

import static org.unitego.lobecorp.Lobecorp.id;

public interface TheQueenOfHatredStates {
	Identifier POSTURE = id("the_queen_of_hatred/posture");

	EntityState SITTING = new EntityState(id("the_queen_of_hatred/sitting"), POSTURE);
	EntityState SITTING_EDGE = new EntityState(id("the_queen_of_hatred/sitting_edge"), POSTURE);
	EntityState SITTING_FADE_OUT = new EntityState(id("the_queen_of_hatred/sitting_fade_out"), POSTURE);
	EntityState SITTING_EDGE_FADE_OUT = new EntityState(id("the_queen_of_hatred/sitting_edge_fade_out"), POSTURE);
}
