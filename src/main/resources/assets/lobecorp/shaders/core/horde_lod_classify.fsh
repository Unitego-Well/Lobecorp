#version 330
#moj_import <lobecorp:horde_data.glsl>
uniform sampler2D HordeMotion;
uniform sampler2D HordeLodSource;
layout(std140) uniform HordeLodParameters { ivec4 LodRange; };
out vec4 fragColor;
void main() {
    int ordinal = int(gl_FragCoord.y) * LodRange.x + int(gl_FragCoord.x);
    uint selected = 0u;
    if (ordinal < LodRange.y) {
        vec3 point = vector3(HordeMotion, ordinal * 8);
        uint distanceBits = floatBitsToUint(dot(point, point));
        int low = 0;
        int high = 128;
        while (low < high) {
            int middle = (low + high) / 2;
            uint otherDistance = rawWord(HordeLodSource, middle * 2);
            uint otherId = rawWord(HordeLodSource, middle * 2 + 1);
            if (otherDistance < distanceBits || (otherDistance == distanceBits && otherId < uint(ordinal))) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        int allowance = max(0, 128 - int(floor(length(point) / 16.0)) * 16);
        selected = low < allowance && rawWord(HordeLodSource, low * 2 + 1) == uint(ordinal) ? 1u : 0u;
    }
    fragColor = vec4(float(selected) / 255.0, 0.0, 0.0, 0.0);
}
