#version 330

#moj_import <lobecorp:horde_data.glsl>
#moj_import <lobecorp:horde_flow.glsl>

layout(std140) uniform TerrainParameters {
    ivec4 TerrainShape;
    vec4 TerrainUpdate;
};
uniform sampler2D TerrainTargets;
uniform sampler2D TerrainPrevious;
uniform sampler2D TerrainFlow;
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
    float ground = packedTerrainHeight(TerrainTargets, sampleIndex);
    float left = packedTerrainHeight(TerrainTargets, first + max(0, column - 1));
    float right = packedTerrainHeight(TerrainTargets, first + min(TerrainShape.y - 1, column + 1));
    int previous = max(0, sampleIndex / TerrainShape.y - 1) * TerrainShape.y + column;
    float reference = packedTerrainHeight(TerrainTargets, previous);
    int straightRank = hordeTerrainRank(reference, reference, ground);
    int leftRank = column > 0 ? hordeTerrainRank(reference, reference, left) : 3;
    int rightRank = column + 1 < TerrainShape.y ? hordeTerrainRank(reference, reference, right) : 3;
    float spacing = TerrainUpdate.z / float(max(1, TerrainShape.y - 1));
    bool preferLeft = leftRank < rightRank || (leftRank == rightRank
            && abs(left - reference) < abs(right - reference));
    int bestRank = min(leftRank, rightRank);
    float flow = bestRank < straightRank ? (preferLeft ? -spacing : spacing) : 0.0;
    if ((TerrainShape.w & 2) != 0) {
        flow = scalar(TerrainFlow, sampleIndex);
    }
    flow = clamp(flow, -TerrainUpdate.w, TerrainUpdate.w);
    bool heightComponent = component < 2;
    int base = sampleIndex * 4 + (heightComponent ? 0 : 2);
    float target = heightComponent ? ground : flow;
    vec2 state;
    if ((TerrainShape.w & 4) != 0) {
        state = vec2(target, 0.0);
    } else {
        float old = (TerrainShape.w & 1) != 0 ? (heightComponent ? target : 0.0) : scalar(TerrainPrevious, base);
        float velocity = (TerrainShape.w & 1) != 0 ? 0.0 : scalar(TerrainPrevious, base + 1);
        state = hordeDamped(old, velocity, target, TerrainUpdate.y, TerrainUpdate.x);
    }
    uint bits = floatBitsToUint(state[component % 2]);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
