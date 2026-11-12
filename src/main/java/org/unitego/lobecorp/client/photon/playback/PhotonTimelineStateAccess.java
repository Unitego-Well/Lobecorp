package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.photon.client.gameobject.FXObject;
import org.unitego.lobecorp.client.photon.editor.PhotonAuthoringPose;

public interface PhotonTimelineStateAccess {
	boolean lobecorp$recording();

	PhotonAuthoringPose lobecorp$authoredPose(FXObject object, PhotonAuthoringPose current);
}
