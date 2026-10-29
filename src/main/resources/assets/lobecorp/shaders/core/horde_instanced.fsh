#version 330

#moj_import <minecraft:fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Glowmask;

in vec2 texCoord;
in vec4 lighting;
in float sphericalDistance;
in float cylindricalDistance;
flat in float glowing;

out vec4 fragColor;

void main() {
    vec4 albedo = texture(Sampler0, texCoord);
    if (albedo.a < 0.1 || lighting.a <= 0.001) {
        discard;
    }
    vec4 glow = texture(Glowmask, texCoord);
    vec3 color = mix(albedo.rgb * lighting.rgb, glow.rgb, clamp(glow.a * glowing, 0.0, 1.0));
    fragColor = apply_fog(vec4(color, 1.0), sphericalDistance, cylindricalDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
