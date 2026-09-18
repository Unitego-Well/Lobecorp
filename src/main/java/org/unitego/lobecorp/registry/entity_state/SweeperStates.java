package org.unitego.lobecorp.registry.entity_state;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.entity.entity_state.EntityState;

import static org.unitego.lobecorp.Lobecorp.id;

public interface SweeperStates {
	Identifier ACTION = id("sweeper/action");

	EntityState ATTACK = new EntityState(id("sweeper/attack"), ACTION);
	EntityState LEAP = new EntityState(id("sweeper/leap"), ACTION);
	EntityState DISPOSE_CORPSE = new EntityState(id("sweeper/dispose_corpse"), ACTION);
}
