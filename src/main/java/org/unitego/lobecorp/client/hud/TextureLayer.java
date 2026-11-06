package org.unitego.lobecorp.client.hud;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 单段纹理相对状态条左上角的偏移与尺寸。
@NullMarked
public class TextureLayer {
	private Identifier texture;
	private float x;
	private float y;
	private float width;
	private float height;
	private float textureWidth;
	private float textureHeight;
	private int u;
	private int v;

	public TextureLayer(Identifier texture, float x, float y, float width, float height,
	                    float textureWidth, float textureHeight, int u, int v) {
		this.texture = texture;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.u = u;
		this.v = v;
	}

	public TextureLayer(Identifier texture, float x, float y, float width, float height) {
		this(texture, x, y, width, height, width, height, 0, 0);
	}

	public Identifier getTexture() {
		return texture;
	}

	public void setTexture(Identifier texture) {
		this.texture = texture;
	}

	public float getHeight() {
		return height;
	}

	public void setHeight(float height) {
		this.height = height;
	}

	public float getTextureHeight() {
		return textureHeight;
	}

	public void setTextureHeight(float textureHeight) {
		this.textureHeight = textureHeight;
	}

	public float getTextureWidth() {
		return textureWidth;
	}

	public void setTextureWidth(float textureWidth) {
		this.textureWidth = textureWidth;
	}

	public int getU() {
		return u;
	}

	public void setU(int u) {
		this.u = u;
	}

	public int getV() {
		return v;
	}

	public void setV(int v) {
		this.v = v;
	}

	public float getWidth() {
		return width;
	}

	public void setWidth(float width) {
		this.width = width;
	}

	public float getX() {
		return x;
	}

	public void setX(float x) {
		this.x = x;
	}

	public float getY() {
		return y;
	}

	public void setY(float y) {
		this.y = y;
	}

	public Identifier texture() {
		return texture;
	}

	public float x() {
		return x;
	}

	public float y() {
		return y;
	}

	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	public float textureWidth() {
		return textureWidth;
	}

	public float textureHeight() {
		return textureHeight;
	}

	public int u() {
		return u;
	}

	public int v() {
		return v;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (TextureLayer) obj;
		return Objects.equals(this.texture, that.texture) &&
				Float.floatToIntBits(this.x) == Float.floatToIntBits(that.x) &&
				Float.floatToIntBits(this.y) == Float.floatToIntBits(that.y) &&
				Float.floatToIntBits(this.width) == Float.floatToIntBits(that.width) &&
				Float.floatToIntBits(this.height) == Float.floatToIntBits(that.height) &&
				Float.floatToIntBits(this.textureWidth) == Float.floatToIntBits(that.textureWidth) &&
				Float.floatToIntBits(this.textureHeight) == Float.floatToIntBits(that.textureHeight) &&
				this.u == that.u &&
				this.v == that.v;
	}

	@Override
	public int hashCode() {
		return Objects.hash(texture, x, y, width, height, textureWidth, textureHeight, u, v);
	}

	@Override
	public String toString() {
		return "Layer[" +
				"texture=" + texture + ", " +
				"x=" + x + ", " +
				"y=" + y + ", " +
				"width=" + width + ", " +
				"height=" + height + ", " +
				"textureWidth=" + textureWidth + ", " +
				"textureHeight=" + textureHeight + ", " +
				"u=" + u + ", " +
				"v=" + v + ']';
	}

}
