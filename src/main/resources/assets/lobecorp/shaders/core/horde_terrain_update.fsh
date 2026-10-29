#version 330

#moj_import <lobecorp:horde_data.glsl>
#moj_import <lobecorp:horde_flow.glsl>

layout(std140) uniform TerrainParameters {
    ivec4 TerrainShape;
    vec4 TerrainUpdate;
};
uniform sampler2D TerrainTargets;
uniform sampler2D TerrainPrevious;
out vec4 fragColor;

void main() {
    int pixel = int(gl_FragCoord.y) * TerrainShape.z + int(gl_FragCoord.x);
    int sampleIndex = pixel / 4;
    int component = pixel % 4;
    if (sampleIndex >= TerrainShape.x * TerrainShape.y) {
        fragColor = vec4(0.0);
        return;
    }
    int column = sampleIndex % TerrainShape.y;
    int first = sampleIndex - column;
    float ground = scalar(TerrainTargets, sampleIndex);
    float left = scalar(TerrainTargets, first + max(0, column - 1));
    float right = scalar(TerrainTargets, first + min(TerrainShape.y - 1, column + 1));
    float obstacle = smoothstep(1.0, 3.0, ground - min(left, right));
    float spacing = TerrainUpdate.z / float(max(1, TerrainShape.y - 1));
    float preferredSide = abs(left - right) < 0.1
    ? (column * 2 < TerrainShape.y ? -1.0 : 1.0) : (left < right ? -1.0 : 1.0);
    float radius = obstacle * spacing * 0.5;
    float flow = radius > 0.0 ? preferredSide * hordeGapPush(0.0, 0.0, radius, spacing, preferredSide) : 0.0;
    flow = clamp(flow, -TerrainUpdate.w, TerrainUpdate.w);
    bool heightComponent = component < 2;
    int base = sampleIndex * 4 + (heightComponent ? 0 : 2);
    float target = heightComponent ? ground : flow;
    float old = TerrainShape.w != 0 ? (heightComponent ? target : 0.0) : scalar(TerrainPrevious, base);
    float velocity = TerrainShape.w != 0 ? 0.0 : scalar(TerrainPrevious, base + 1);
    vec2 state = hordeDamped(old, velocity, target, TerrainUpdate.y, TerrainUpdate.x);
    uint bits = floatBitsToUint(state[component % 2]);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
