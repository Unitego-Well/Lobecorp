package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor.AnimationTrackUIState;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.GradientClip;
import com.lowdragmc.photon.client.fx.timeline.CurveClip;
import com.lowdragmc.photon.client.fx.timeline.ExprClip;
import com.lowdragmc.photon.client.fx.timeline.property.ColorAnimatedProperty;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.joml.Vector2f;

import java.util.Map;
import java.util.Set;

@Mixin(AnimationTrackUIState.class)
@SuppressWarnings("UnnecessaryModifier")
public interface AnimationTrackUIStateAccessor {
	@Accessor("selectedAxis")
	public int lobecorp$getSelectedAxis();

	@Accessor("selectedGradientClips")
	public Set<GradientClip> lobecorp$getGradientClips();

	@Accessor("selectedCurveClips")
	public Set<CurveClip> lobecorp$getCurveClips();

	@Accessor("selectedExprClips")
	public Set<ExprClip> lobecorp$getExprClips();

	@Accessor("selectedProperty")
	public AnimatedProperty lobecorp$getSelectedProperty();

	@Accessor("selectedStop")
	public ColorAnimatedProperty.ColorKey lobecorp$getSelectedStop();

	@Accessor("dragProperty")
	public AnimatedProperty lobecorp$getDragProperty();

	@Accessor("dragSnapshot")
	public ECBCurves[] lobecorp$getDragSnapshot();

	@Accessor("dragAxis")
	public int lobecorp$getDragAxis();

	@Accessor("dragKey")
	public int lobecorp$getDragKey();

	@Accessor("dragHandle")
	public int lobecorp$getDragHandle();

	@Accessor("selKeyAxis")
	public void lobecorp$setPrimaryAxis(int axis);

	@Accessor("selKeyIndex")
	public void lobecorp$setPrimaryIndex(int index);

	@Accessor("selectedStop")
	public void lobecorp$setSelectedStop(ColorAnimatedProperty.ColorKey stop);

	@Accessor("selectedKeys")
	public Set<Long> lobecorp$getSelectedKeys();

	@Accessor("dragProperty")
	public void lobecorp$setDragProperty(AnimatedProperty property);

	@Accessor("dragSnapshot")
	public void lobecorp$setDragSnapshot(ECBCurves[] snapshot);

	@Accessor("keyGroupDrag")
	public void lobecorp$setKeyGroupDrag(boolean dragging);

	@Accessor("keyDragOrigins")
	public Map<Long, Vector2f> lobecorp$getKeyDragOrigins();
}
