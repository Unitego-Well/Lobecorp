#version 330

layout(std140) uniform SkyParameters {
    mat4 InverseViewProjection;
    vec4 DirectionStrength;
    vec4 PhaseDepth;
};
uniform sampler2D SkyDepth;
in vec2 skyUv;
out vec4 fragColor;

void main() {
    if (PhaseDepth.z > 0.5) {
        float depth = texture(SkyDepth, skyUv).r;
        bool background = PhaseDepth.y > 0.5 ? depth <= 0.00001 : depth >= 0.99999;
        if (!background) discard;
    }
    float farDepth = PhaseDepth.y > 0.5 ? 0.0 : 1.0;
    vec3 ray = normalize((InverseViewProjection * vec4(skyUv * 2.0 - 1.0, farDepth, 1.0)).xyz);
    if (ray.y < -0.035) discard;
    vec3 source = normalize(vec3(DirectionStrength.x, 0.0, DirectionStrength.z));
    vec3 side = vec3(-source.z, 0.0, source.x);
    float along = dot(ray, source);
    float across = dot(ray, side);
    float coverage = clamp(PhaseDepth.w, 0.0, 1.0);
    // 三维方向场在天顶连续，不使用方位角或归一化水平分量。
    float wisps = 0.06 * sin(dot(ray, vec3(3.1, 2.3, -1.7)) + PhaseDepth.x * 0.003)
        + 0.025 * sin(dot(ray, vec3(-5.2, 3.7, 2.1)) - PhaseDepth.x * 0.005);
    float field = along + mix(0.15, 1.15, coverage) * ray.y + 0.10 * across * ray.y + wisps;
    float edge = mix(0.85, 0.25, coverage);
    float veil = smoothstep(edge - 0.24, edge + 0.24, field);
    float horizon = smoothstep(-0.035, 0.025, ray.y);
    float warm = smoothstep(-0.15, 0.95, along) * (0.8 + 0.2 * sin(ray.y * 4.0 + across * 2.0));
    vec3 crimson = mix(vec3(0.16, 0.025, 0.045), vec3(0.34, 0.075, 0.085), warm);
    float fringe = 1.0 - smoothstep(edge - 0.12, edge + 0.35, field);
    crimson = mix(crimson, vec3(0.12, 0.035, 0.15), fringe * 0.65);
    float alpha = clamp(DirectionStrength.w * veil * horizon, 0.0, 0.78);
    if (alpha <= 0.001) discard;
    fragColor = vec4(crimson, alpha);
}
