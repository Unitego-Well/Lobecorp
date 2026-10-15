package org.unitego.lobecorp.registry.entity.state;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.world.entity.state.EntityState;

import static org.unitego.lobecorp.Lobecorp.id;

public interface SweeperStates {
	/// 清道夫动作状态的互斥分组标识，供技能和同步状态共同使用。
	Identifier ACTION = id("sweeper/action");

	/// 清道夫普通攻击状态，属于动作互斥分组。
	EntityState ATTACK = new EntityState(id("sweeper/attack"), ACTION);
	/// 清道夫跃击状态，属于动作互斥分组。
	EntityState LEAP = new EntityState(id("sweeper/leap"), ACTION);
	/// 清道夫清理动作状态，属于动作互斥分组。
	EntityState SWEEP = new EntityState(id("sweeper/sweep"), ACTION);
	/// 清道夫重组动作状态，属于动作互斥分组。
	EntityState REASSEMBLE = new EntityState(id("sweeper/reassemble"), ACTION);
}
