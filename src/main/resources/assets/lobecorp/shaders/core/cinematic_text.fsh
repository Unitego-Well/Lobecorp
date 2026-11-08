#version 330

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord;
in vec2 glyphPosition;
flat in float dissolve;
flat in float seed;
out vec4 fragColor;

vec2 atlasOrigin;
vec2 atlasSpan;

float glyph(vec2 p) {
    if (any(lessThan(p, vec2(0.0))) || any(greaterThan(p, vec2(1.0)))) return 0.0;
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 inset = 0.5 / size;
    vec2 uv = clamp(atlasOrigin + p * atlasSpan, atlasOrigin + inset, atlasOrigin + atlasSpan - inset);
    vec4 sampleColor = texture(Sampler0, uv);
    float coverage = sampleColor.a;
#ifdef INTENSITY
    coverage = sampleColor.r;
#endif
    return coverage;
}

// 前缘只侵蚀真实字形；纵向笔画和转折内的相邻覆盖改变前缘行进速度。
float arrival(vec2 p) {
    float connected = glyph(p + vec2(0.0, 0.07)) + glyph(p - vec2(0.0, 0.07));
    float winding = sin(p.x * 15.0 + seed * 6.0 + sin(p.y * 9.0)) * 0.12;
    return clamp(0.18 + (1.0 - p.y) * 0.62 + winding
            + abs(p.x - mix(0.24, 0.76, seed)) * 0.19 - connected * 0.055, 0.04, 0.78);
}

void main() {
    if (dissolve >= 0.999) discard;
    atlasSpan = vec2(dFdx(texCoord.x) / dFdx(glyphPosition.x),
                     dFdy(texCoord.y) / dFdy(glyphPosition.y));
    atlasOrigin = texCoord - glyphPosition * atlasSpan;
    float mask = glyph(glyphPosition);
    if (dissolve <= 0.0001) {
        if (mask < 0.1) discard;
        fragColor = vec4(vertexColor.rgb, vertexColor.a * mask);
        return;
    }

    vec2 pixels = max(vec2(4.0), abs(atlasSpan) * vec2(textureSize(Sampler0, 0)));
    vec2 p = (floor(glyphPosition * pixels) + 0.5) / pixels;
    float front = arrival(p);
    float wet = smoothstep(front - 0.025, front + 0.025, dissolve);
    float eaten = smoothstep(front + 0.14, front + 0.19, dissolve);
    float edge = 1.0 - smoothstep(0.0, 0.045, abs(dissolve - front));
    vec3 crimson = mix(vec3(0.26, 0.006, 0.025), vec3(0.58, 0.035, 0.065), edge * 0.75);
    float retained = mask * (1.0 - eaten);
    vec3 color = mix(vertexColor.rgb, crimson, wet);

    // 少量黏尾从已浸染的笔画延伸，完全消散后不产生独立余滴。
    float tail = 0.0;
    float neck = exp(-pow((p.x - mix(0.28, 0.72, seed)) / 0.065, 2.0));
    if (mask < 0.1 && neck > 0.05) {
        for (int i = 1; i <= 4; i++) {
            vec2 above = p - vec2(0.0, float(i) * 0.055);
            float at = arrival(above);
            float attached = glyph(above) * smoothstep(at, at + 0.05, dissolve)
                    * (1.0 - smoothstep(at + 0.12, at + 0.21, dissolve));
            tail = max(tail, attached * neck * (1.0 - float(i) / 5.0));
        }
    }
    float alpha = max(retained, tail);
    if (alpha < 0.035) discard;
    if (retained < tail) color = crimson;
    fragColor = vec4(color, alpha * vertexColor.a);
}
