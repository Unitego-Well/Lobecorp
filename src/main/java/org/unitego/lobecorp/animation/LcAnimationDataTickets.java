package org.unitego.lobecorp.animation;

import com.geckolib.constant.dataticket.DataTicket;
import org.unitego.lobecorp.Lobecorp;

public final class LcAnimationDataTickets {
	public static final DataTicket<LcAnimationFrame> ANIMATION_FRAME =
			DataTicket.create(Lobecorp.name("animation_frame"), LcAnimationFrame.class);

	private LcAnimationDataTickets() {
	}
}
