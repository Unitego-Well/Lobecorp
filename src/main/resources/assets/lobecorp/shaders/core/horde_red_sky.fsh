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
    vec2 horizontal = ray.xz / max(length(ray.xz), 0.0001);
    float bearing = dot(horizontal, normalize(DirectionStrength.xz));
    float angular = smoothstep(-0.25, 0.9, bearing);
    float strength = DirectionStrength.w;
    float angle = atan(ray.z, ray.x);
    float front = mix(0.28, 1.02, smoothstep(0.10, 0.72, strength));
    front += 0.035 * sin(angle * 3.0 + PhaseDepth.x * 0.008)
        + 0.015 * sin(angle * 7.0 - PhaseDepth.x * 0.012);
    float vertical = 1.0 - smoothstep(front - 0.12, front + 0.08, ray.y);
    float horizon = smoothstep(-0.035, 0.025, ray.y);
    float leading = exp(-abs(ray.y - front + 0.12) * 18.0) * 0.22;
    vec3 crimson = mix(vec3(0.2902, 0.0549, 0.0941), vec3(0.5490, 0.1216, 0.1490),
        clamp(bearing * 0.6 + leading, 0.0, 1.0));
    crimson = mix(crimson, vec3(0.1647, 0.0706, 0.1882), 1.0 - angular);
    float alpha = clamp(strength * angular * vertical * horizon, 0.0, 0.78);
    if (alpha <= 0.001) discard;
    fragColor = vec4(crimson, alpha);
}
