#version 330

layout(std140) uniform SkyParameters {
    mat4 InverseViewProjection;
    vec4 DirectionStrength;
    vec4 PhaseDepth;
};
uniform sampler2D SkyDepth;
in vec2 skyUv;
out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float cloudNoise(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(cell), hash3(cell + vec3(1, 0, 0)), f.x),
                   mix(hash3(cell + vec3(0, 1, 0)), hash3(cell + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash3(cell + vec3(0, 0, 1)), hash3(cell + vec3(1, 0, 1)), f.x),
                   mix(hash3(cell + vec3(0, 1, 1)), hash3(cell + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// 世界来向的远处低天先起染，再以错落的柔前缘逐渐推进到上空。
float waveContour(float across, float elevation, float phase, float progress) {
    float reach = mix(-0.08, 1.65, progress);
    float arch = 0.11 * sqrt(max(0.0, 1.0 - across * across));
    float irregular = sin(across * 5.2 + phase * 0.0017) * 0.045
            + sin(across * 9.1 - phase * 0.0011) * 0.022;
    float boundary = reach + arch + irregular;
    float front = 1.0 - smoothstep(boundary - 0.065, boundary + 0.065, elevation);
    return front * smoothstep(0.0, 0.10, progress);
}

void main() {
    if (PhaseDepth.z > 0.5) {
        float depth = texture(SkyDepth, skyUv).r;
        bool background = PhaseDepth.y > 0.5 ? depth <= 0.00001 : depth >= 0.99999;
        if (!background) discard;
    }
    float farDepth = PhaseDepth.y > 0.5 ? 0.0 : 1.0;
    vec3 ray = normalize((InverseViewProjection * vec4(skyUv * 2.0 - 1.0, farDepth, 1.0)).xyz);
    if (ray.y < -0.20) discard;
    vec3 source = normalize(vec3(DirectionStrength.x, 0.0, DirectionStrength.z));
    vec3 side = vec3(-source.z, 0.0, source.x);
    float along = dot(ray, source), across = dot(ray, side);
    float coverage = clamp(PhaseDepth.w, 0.0, 1.0);
    vec3 wind = vec3(PhaseDepth.x * 0.0007, 0.0, -PhaseDepth.x * 0.0003);
    // 云纹固定在世界方向，来向平滑转动只推动覆盖边界，不拖动整片纹理。
    vec3 p = vec3(ray.x * 1.6 + ray.z * 0.7, ray.y * 4.2 - ray.z * 1.1, ray.z * 2.0) + wind;
    float broad = cloudNoise(p);
    float density = smoothstep(0.22, 0.83, broad * 0.78 + cloudNoise(p * 2.15 + vec3(7.1, 2.0, -3.7)) * 0.22);
    float contourNoise = (cloudNoise(p * 1.3 + vec3(2.1, 8.7, -4.0)) - 0.5) * 0.08;
    float primary = waveContour(across, ray.y + contourNoise, PhaseDepth.x, coverage);
    float following = waveContour(across + 0.11, ray.y + contourNoise - 0.24,
            PhaseDepth.x + 170.0, coverage * 0.91);
    // 远处仅保留来源侧小片，近处连续开放背面及天顶，不留下固定半球裁切。
    float spread = smoothstep(0.02, 0.98, coverage);
    float directionEdge = mix(0.97, -1.35, spread);
    float directionMask = smoothstep(directionEdge - 0.05, directionEdge + 0.05, along + ray.y * 0.35);
    float veil = max(primary, following * 0.74) * directionMask;
    float lowerSky = smoothstep(-0.20, -0.12, ray.y);
    float sourceSide = smoothstep(-0.25, 0.95, along);
    float redMass = density * (0.38 + sourceSide * 0.62);
    vec3 crimson = mix(vec3(0.24, 0.039, 0.082), vec3(0.49, 0.072, 0.135), redMass);
    // 近黑只留于窄浪谷，主体保持可读的猩红中间调。
    float trough = smoothstep(0.77, 0.94, density) * smoothstep(0.72, 0.96, primary);
    crimson = mix(crimson, vec3(0.10, 0.019, 0.047), trough * 0.52);
    float crestLight = (1.0 - smoothstep(0.12, 0.43, primary)) * smoothstep(0.01, 0.16, primary);
    crimson = mix(crimson, vec3(0.65, 0.12, 0.19), crestLight * 0.52);
    // 亮红略高于天地接触线，来源侧错落增强，贴地仍为暗酒红。
    float lowBand = exp(-pow((ray.y - 0.055) / 0.075, 2.0)) * sourceSide;
    float hotspot = lowBand * smoothstep(0.28, 0.8, broad);
    crimson = mix(crimson, vec3(0.57, 0.075, 0.16), hotspot * 0.6);
    crimson = mix(vec3(0.19, 0.027, 0.055), crimson, smoothstep(-0.07, 0.025, ray.y));
    float horizonSeal = (1.0 - smoothstep(0.015, 0.15, abs(ray.y))) * (0.35 + sourceSide * 0.65);
    float alpha = clamp(DirectionStrength.w * max(veil, horizonSeal * coverage * directionMask) * lowerSky, 0.0, 0.94);
    if (alpha <= 0.001) discard;
    fragColor = vec4(crimson, alpha);
}
