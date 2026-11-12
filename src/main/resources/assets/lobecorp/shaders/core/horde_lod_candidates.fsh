#version 330

#moj_import <lobecorp:horde_data.glsl>
#moj_import <lobecorp:horde_lod_priority.glsl>
#moj_import <minecraft:projection.glsl>

uniform sampler2D HordeMotion;
layout(std140) uniform HordeLodParameters {
    ivec4 LodRange; // 输出宽度、总容量、全局首编号（0）、本帧总数量。
    ivec4 LodMotion; // 运动首编号（0）、运动每单位字数、保留、保留。
    vec4 LodOrigin;
};
out vec4 fragColor;

layout(std140) uniform HordeVisibility {
    mat4 ViewRotation;
    vec4 VisibilityShape;
    vec4 VisibilityViewport;
};
uniform sampler2D HordeDepthEven;
uniform sampler2D HordeDepthOdd;

// 使用覆盖所有动画帧的保守包围球，屏幕边缘部分可见的单位仍可入选。
bool inFrustum(mat4 viewProjection, vec3 point, float radius) {
    vec4 clip = viewProjection * vec4(point, 1.0);
    vec3 w = vec3(viewProjection[0][3], viewProjection[1][3], viewProjection[2][3]);
    for (int axis = 0; axis < 3; axis++) {
        vec3 row = vec3(viewProjection[0][axis], viewProjection[1][axis], viewProjection[2][axis]);
        if (clip[axis] + clip.w < -radius * length(row + w)
                || clip.w - clip[axis] < -radius * length(w - row)) {
            return false;
        }
    }
    return true;
}

#ifndef HORDE_DISABLE_OCCLUSION
float maximumDepth(ivec2 cell, int mip) {
    vec4 encoded = (mip & 1) == 0 ? texelFetch(HordeDepthEven, cell, mip)
            : texelFetch(HordeDepthOdd, cell, mip);
    uvec4 bytes = uvec4(round(encoded * 255.0));
    return uintBitsToFloat(bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u));
}

// 整个保守包围盒都在当前不透明场景之后才拒绝；部分露出、近面相交均保留。
bool notOccluded(mat4 viewProjection, vec3 point, float radius) {
    vec2 low = vec2(1.0), high = vec2(0.0);
    float front = 1.0;
    for (int corner = 0; corner < 8; corner++) {
        vec3 direction = vec3((corner & 1) == 0 ? -1.0 : 1.0,
                (corner & 2) == 0 ? -1.0 : 1.0, (corner & 4) == 0 ? -1.0 : 1.0);
        vec4 clip = viewProjection * vec4(point + direction * radius, 1.0);
        if (clip.w <= 0.0001) {
            return true;
        }
        vec3 ndc = clip.xyz / clip.w;
        vec2 uv = ndc.xy * 0.5 + 0.5;
        low = min(low, uv);
        high = max(high, uv);
        float depth = VisibilityShape.z > 0.5 ? ndc.z : ndc.z * 0.5 + 0.5;
        front = min(front, VisibilityShape.y > 0.5 ? 1.0 - depth : depth);
    }
    ivec2 screen = ivec2(VisibilityViewport.xy);
    ivec2 first = clamp(ivec2(floor(clamp(low, 0.0, 1.0) * vec2(screen))), ivec2(0), screen - 1);
    ivec2 last = clamp(ivec2(ceil(clamp(high, 0.0, 1.0) * vec2(screen))) - 1, first, screen - 1);
    ivec2 extent = last - first + 1;
    int mip = clamp(int(ceil(log2(float(max(extent.x, extent.y))))) - 1, 0, int(VisibilityShape.w) - 1);
    int cellSize = 1 << (mip + 1);
    ivec2 a = first / cellSize, b = last / cellSize;
    float farthest = max(max(maximumDepth(a, mip), maximumDepth(ivec2(b.x, a.y), mip)),
            max(maximumDepth(ivec2(a.x, b.y), mip), maximumDepth(b, mip)));
    return front <= farthest + 0.00001;
}

#endif

void main() {
    int pixel = int(gl_FragCoord.y) * LodRange.x + int(gl_FragCoord.x);
    int ordinal = pixel / 2;
    if (ordinal >= LodRange.y) {
        discard;
    }
    uint bits = 0xffffffffu;
    if (ordinal < LodRange.w) {
        int local = ordinal - LodRange.z;
        if (local < 0 || local >= LodRange.w) {
            discard;
        }
        vec3 relative = vector3(HordeMotion, (LodMotion.x + local) * LodMotion.y);
        float distanceSquared = dot(relative, relative);
        float radius = VisibilityShape.x * scalar(HordeMotion, (LodMotion.x + local) * LodMotion.y + 3);
        mat4 viewProjection = ProjMat * ViewRotation;
        if (distanceSquared < 16384.0 && inFrustum(viewProjection, relative, radius)
#ifndef HORDE_DISABLE_OCCLUSION
                && notOccluded(viewProjection, relative, radius)
#endif
                && scalar(HordeMotion, (LodMotion.x + local) * LodMotion.y + 4) >= 0.0) {
            float edge = scalar(HordeMotion, (LodMotion.x + local) * LodMotion.y + 8);
            bits = pixel % 2 == 0 ? hordeLodKey(distanceSquared, edge) : uint(ordinal);
        }
    }
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
