package org.unitego.lobecorp.registry.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.google.common.reflect.TypeToken;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperVariant;

public interface LcDataTickets {
	/// 清道夫变体
	DataTicket<SweeperVariant> SWEEPER_VARIANT = create("sweeper_variant", SweeperVariant.class);
	/// 清道夫当前生物质占上限的比例
	DataTicket<Float> SWEEPER_BIOMASS_RATIO = createFloat("sweeper_biomass_ratio");
	/// 最大生命值
	DataTicket<Float> HEALTHY = createFloat("healthy");
	/// 是否是尸体
	DataTicket<Boolean> IS_CORPSE = createFloatBoolean("is_corpse");

	static <T> DataTicket<T> create(String id, Class<? extends T> objectType) {
		return DataTicket.create(Lobecorp.name(id), objectType);
	}

	static <T> DataTicket<T> create(String id, TypeToken<T> token) {
		return DataTicket.create(Lobecorp.name(id), token);
	}

	static <T> DataTicket<Float> createFloat(String id) {
		return create(id, TypeToken.of(Float.class));
	}

	static <T> DataTicket<Boolean> createFloatBoolean(String id) {
		return create(id, TypeToken.of(Boolean.class));
	}
}
