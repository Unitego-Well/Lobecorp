#version 330

#moj_import <lobecorp:horde_data.glsl>
#moj_import <lobecorp:horde_flow.glsl>

layout(std140) uniform RegionParameters {
    ivec4 RegionOrigin;
    vec4 RegionUpdate;
    vec4 RegionSettings;
};
uniform sampler2D RegionTargets;
uniform sampler2D RegionPrevious;
out vec4 fragColor;

void main() {
    int pixel = int(gl_FragCoord.y) * int(RegionSettings.y) + int(gl_FragCoord.x);
    int slot = pixel / 13;
    int component = pixel % 13;
    if (slot >= RegionOrigin.w) {
        fragColor = vec4(0.0);
        return;
    }
    int raw = slot * 7;
    int signedGeneration = integer(RegionTargets, raw + 6);
    int generation = abs(signedGeneration);
    uint bits = 0u;
    if (component == 12) {
        bits = uint(generation);
    } else if (generation != 0) {
        ivec3 minimum = ivec3(integer(RegionTargets, raw), integer(RegionTargets, raw + 1), integer(RegionTargets, raw + 2));
        vec3 halfSize = vec3(integer(RegionTargets, raw + 3), integer(RegionTargets, raw + 4),
                integer(RegionTargets, raw + 5)) * 0.5;
        vec3 center = vec3(minimum - RegionOrigin.xyz) - RegionUpdate.xyz + halfSize;
        int axis = component % 3;
        bool radius = component % 6 >= 3;
        float target = radius ? (signedGeneration < 0 ? 0.0 : halfSize[axis]) : center[axis];
        int base = slot * 13;
        bool fresh = integer(RegionPrevious, base + 12) != generation;
        int positionComponent = component % 6;
        float old = fresh ? (radius ? 0.0 : target) : scalar(RegionPrevious, base + positionComponent);
        float velocity = fresh ? 0.0 : scalar(RegionPrevious, base + positionComponent + 6);
        float omega = RegionSettings.x;
        float dt = RegionUpdate.w;
        vec2 state = hordeDamped(old, velocity, target, omega, dt);
        float value = state.x;
        float nextVelocity = state.y;
        if (radius && value < 0.0) {
            value = 0.0;
            nextVelocity = max(0.0, nextVelocity);
        }
        bits = floatBitsToUint(component < 6 ? value : nextVelocity);
    }
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
