package org.unitego.lobecorp.registry.client;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.google.common.reflect.TypeToken;
import org.unitego.lobecorp.entity.client.renderer.ordeal.SweeperModel;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperVariant;

public interface LcDataTickets {
    DataTicket<SweeperVariant> SWEEPER_VARIANT = DataTickets.create("lobecorp:sweeper_variant", SweeperVariant.class);
    DataTicket<Float> HEALTHY = DataTicket.create("lobecorp:healthy", new TypeToken<>() {});
}
