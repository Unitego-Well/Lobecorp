#version 330

#moj_import <lobecorp:horde_data.glsl>

layout(std140) uniform IrisFrameParameters {
    ivec4 FrameShape;
};
uniform sampler2D FrameAnimation;
uniform sampler2D FrameUvs;
out vec4 fragColor;

vec2 uvAt(int vertex) {
    return vec2(scalar(FrameUvs, vertex * 4), scalar(FrameUvs, vertex * 4 + 1));
}

void main() {
    int pixel = int(gl_FragCoord.y) * FrameShape.z + int(gl_FragCoord.x);
    int ordinal = pixel / 10;
    int component = pixel % 10;
    if (ordinal >= FrameShape.x * FrameShape.y) {
        fragColor = vec4(0.0);
        return;
    }
    int base = ordinal * 6;
    float value;
    if (component < 6) {
        value = scalar(FrameAnimation, base + component);
    } else {
        int vertex = ordinal % FrameShape.x;
        int first = ordinal / FrameShape.x * FrameShape.x * 6;
        int quad = vertex / 4 * 4;
        vec3 normal = vector3(FrameAnimation, base + 3);
        normal = length(normal) > 0.0001 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
        vec3 p0 = vector3(FrameAnimation, first + quad * 6);
        vec3 e1 = vector3(FrameAnimation, first + (quad + 1) * 6) - p0;
        vec3 e2 = vector3(FrameAnimation, first + (quad + 2) * 6) - p0;
        vec2 d1 = uvAt(quad + 1) - uvAt(quad);
        vec2 d2 = uvAt(quad + 2) - uvAt(quad);
        float determinant = d1.x * d2.y - d1.y * d2.x;
        vec3 tangent = abs(normal.y) > 0.9 ? cross(normal, vec3(0.0, 0.0, 1.0))
        : cross(normal, vec3(0.0, 1.0, 0.0));
        float handedness = 1.0;
        if (abs(determinant) > 0.000001) {
            vec3 t = (e1 * d2.y - e2 * d1.y) / determinant;
            vec3 bitangent = (e2 * d1.x - e1 * d2.x) / determinant;
            t -= normal * dot(normal, t);
            if (length(t) > 0.0001) {
                tangent = normalize(t);
                handedness = dot(cross(normal, tangent), bitangent) < 0.0 ? -1.0 : 1.0;
            }
        }
        tangent = normalize(tangent);
        value = component == 9 ? handedness : tangent[component - 6];
    }
    uint bits = floatBitsToUint(value);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
