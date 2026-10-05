package org.unitego.lobecorp.mixin.photon.render.client;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.math.noise.PerlinNoise;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.client.gameobject.emitter.data.NoiseSetting;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.render.PhotonNoisePreviewState;

@Mixin(NoiseSetting.NoisePreview.class)
public abstract class NoisePreviewMixin {
	@Shadow
	@Final
	private double seed;
	@Unique
	private int[] lobecorp$colors;
	@Unique
	private int lobecorp$columns;
	@Unique
	private int lobecorp$rows;
	@Unique
	private float lobecorp$frequency;
	@Unique
	private NoiseSetting.Quality lobecorp$quality;
	@Unique
	private CompoundTag lobecorp$remap;

	@Inject(method = "drawInternal", at = @At("HEAD"), cancellable = true)
	private void lobecorp$batchPreview(GUIContext graphics, float x, float y, float width, float height, CallbackInfo ci) {
		var setting = ((NoisePreviewAccessor) this).lobecorp$config();
		ci.cancel();
		if (!(width > 0 && height > 0) || !Float.isFinite(width) || !Float.isFinite(height))
			return;
		int columns = (int) Math.ceil(width);
		int rows = setting.getQuality() == NoiseSetting.Quality.Noise1D ? 1 : (int) Math.ceil(height);
		var remap = PersistedParser.serializeNBT(setting.getRemap(), Platform.getFrozenRegistry());
		if (lobecorp$colors == null || columns != lobecorp$columns || rows != lobecorp$rows
				|| Float.compare(setting.getFrequency(), lobecorp$frequency) != 0
				|| setting.getQuality() != lobecorp$quality || !remap.equals(lobecorp$remap)) {
			var colors = new int[Math.multiplyExact(columns, rows)];
			var noise = new PerlinNoise(seed);
			for (int row = 0; row < rows; row++)
				for (int column = 0; column < columns; column++) {
					float value = ((float) switch (setting.getQuality()) {
						case Noise1D -> noise.noise(column * setting.getFrequency());
						case Noise2D -> noise.noise(column * setting.getFrequency(), row * setting.getFrequency());
						case Noise3D -> noise.noise(column * setting.getFrequency(), row * setting.getFrequency(), 1);
					} + 1) / 2;
					if (setting.getRemap().isEnable())
						value = (setting.getRemap().getRemapCurve().get(value, () -> 0f).floatValue() + 1) / 2;
					int gray = Math.clamp((int) (value * 255), 0, 255);
					colors[row * columns + column] = 0xff000000 | gray << 16 | gray << 8 | gray;
				}
			lobecorp$colors = colors;
			lobecorp$columns = columns;
			lobecorp$rows = rows;
			lobecorp$frequency = setting.getFrequency();
			lobecorp$quality = setting.getQuality();
			lobecorp$remap = remap;
		}
		graphics.addGuiElement(new PhotonNoisePreviewState(graphics.pose.copyPose(), x, y,
				setting.getQuality() == NoiseSetting.Quality.Noise1D ? height : 1,
				columns, rows, lobecorp$colors, graphics.elementColor, graphics.peekScissor()));
	}
}
