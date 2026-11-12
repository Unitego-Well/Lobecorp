#version 330
#moj_import <lobecorp:horde_data.glsl>
#moj_import <lobecorp:horde_lod_priority.glsl>
uniform sampler2D HordeLodSource;
layout(std140) uniform HordeLodParameters { ivec4 LodRange; ivec4 LodMotion; };
void main() {
    gl_PointSize = 1.0;
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    int rank = gl_VertexID;
    uint key = rawWord(HordeLodSource, rank * 2);
    uint identity = rawWord(HordeLodSource, rank * 2 + 1);
    if (!hordeLodAccepted(key, identity, rank, LodRange.y)) {
        return;
    }
    ivec2 pixel = ivec2(int(identity) % LodRange.x, int(identity) / LodRange.x);
    vec2 center = (vec2(pixel) + 0.5) / vec2(LodRange.x, LodRange.z);
    gl_Position = vec4(center * 2.0 - 1.0, 0.0, 1.0);
}
