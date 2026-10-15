#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(std140) uniform PhotonMaterial {
    vec4 HDR;
    vec4 U_SpriteUV;
    float DiscardThreshold;
    int HDRMode;
    float Bits;
};

layout(std140) uniform PhotonMask {
    float MaskValue;
    float AlphaCutoff;
};

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord0;
#ifdef LOBECORP_OCCLUSION_PIXEL
    float bits = max(Bits, 1.0);
    uv = (floor(texCoord0 * bits) + 0.5) / bits;
#elif defined(LOBECORP_OCCLUSION_SPRITE)
    uv = vec2(
        U_SpriteUV.x + texCoord0.x * (U_SpriteUV.z - U_SpriteUV.x),
        U_SpriteUV.y + texCoord0.y * (U_SpriteUV.w - U_SpriteUV.y)
    );
#endif

    float alpha = (texture(Sampler0, uv) * vertexColor * ColorModulator).a;
    if (!(alpha > 0.0) || !(alpha >= max(DiscardThreshold, AlphaCutoff))) {
        discard;
    }

    fragColor = vec4(0.0);
}
