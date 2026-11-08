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
    vec3 p = vec3(along * 1.6 + across * 0.7, ray.y * 4.2 - across * 1.1, across * 2.0) + wind;
    float broad = cloudNoise(p);
    float density = smoothstep(0.22, 0.83, broad * 0.78 + cloudNoise(p * 2.15 + vec3(7.1, 2.0, -3.7)) * 0.22);
    float field = along + mix(0.15, 1.15, coverage) * ray.y + across * ray.y * 0.10 + (broad - 0.5) * 0.18;
    float edge = mix(0.85, 0.25, coverage);
    float veil = smoothstep(edge - 0.24, edge + 0.24, field);
    float lowerSky = smoothstep(-0.20, -0.12, ray.y);
    float sourceSide = smoothstep(-0.25, 0.95, along);
    float redMass = density * (0.38 + sourceSide * 0.62);
    vec3 crimson = mix(vec3(0.071, 0.031, 0.067), vec3(0.227, 0.027, 0.082), redMass);
    crimson = mix(crimson, vec3(0.475, 0.059, 0.133), smoothstep(0.42, 0.92, redMass) * 0.85);
    // 亮红略高于天地接触线，来源侧错落增强，贴地仍为暗酒红。
    float lowBand = exp(-pow((ray.y - 0.055) / 0.075, 2.0)) * sourceSide;
    float hotspot = lowBand * smoothstep(0.28, 0.8, broad);
    crimson = mix(crimson, vec3(0.57, 0.075, 0.16), hotspot * 0.6);
    crimson = mix(vec3(0.10, 0.015, 0.035), crimson, smoothstep(-0.07, 0.025, ray.y));
    float horizonSeal = (1.0 - smoothstep(0.015, 0.15, abs(ray.y))) * (0.35 + sourceSide * 0.65);
    float alpha = clamp(DirectionStrength.w * max(veil, horizonSeal) * lowerSky, 0.0, 0.94);
    if (alpha <= 0.001) discard;
    fragColor = vec4(crimson, alpha);
}
