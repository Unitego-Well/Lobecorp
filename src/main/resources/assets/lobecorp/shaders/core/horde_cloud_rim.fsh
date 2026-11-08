#version 330

#moj_import <minecraft:fog.glsl>

layout(std140) uniform RimParameters {
    vec4 SourceStrength;
    vec4 TimeCell;
};
uniform sampler2D CloudMask;
in vec2 cellUv;
in float vertexDistance;
in float cloudAlpha;
flat in ivec2 cloudCell;
flat in int eligibleFace;
out vec4 fragColor;

bool emptyCell(ivec2 cell) {
    ivec2 size = textureSize(CloudMask, 0);
    ivec2 wrapped = ((cell % size) + size) % size;
    return texelFetch(CloudMask, wrapped, 0).a < 10.0 / 255.0;
}

float innerEdge(float distance) {
    return smoothstep(0.0, 0.015, distance) * (1.0 - smoothstep(0.025, 0.085, distance));
}

void main() {
    if (eligibleFace == 0 || emptyCell(cloudCell)) discard;
    vec2 source = normalize(SourceStrength.xz);
    float rim = 0.0;
    if (emptyCell(cloudCell + ivec2(-1, 0))) rim = max(rim, innerEdge(cellUv.x) * max(0.0, -source.x));
    if (emptyCell(cloudCell + ivec2(1, 0))) rim = max(rim, innerEdge(1.0 - cellUv.x) * max(0.0, source.x));
    if (emptyCell(cloudCell + ivec2(0, -1))) rim = max(rim, innerEdge(cellUv.y) * max(0.0, -source.y));
    if (emptyCell(cloudCell + ivec2(0, 1))) rim = max(rim, innerEdge(1.0 - cellUv.y) * max(0.0, source.y));
    vec2 p = vec2(cloudCell) + cellUv;
    vec2 phase = p * 6.2831853 / vec2(textureSize(CloudMask, 0));
    float bands = sin(phase.x * 31.0 + sin(phase.y * 23.0) + TimeCell.x * 0.002)
        * sin(phase.y * 19.0 - phase.x * 13.0 - TimeCell.x * 0.001);
    float segment = smoothstep(0.20, 0.65, bands);
    float fog = 1.0 - linear_fog_value(vertexDistance, 0.0, FogCloudsEnd);
    float alpha = rim * segment * SourceStrength.w * 0.48 * fog * cloudAlpha;
    if (alpha < 0.001) discard;
    fragColor = vec4(0.48, 0.055, 0.12, alpha);
}
