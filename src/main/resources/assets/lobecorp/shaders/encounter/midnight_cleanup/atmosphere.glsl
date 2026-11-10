vec2 size = vec2(textureSize(source, 0));
float aspect = size.x / size.y;
vec2 edge = (uv - vec2(0.5)) * vec2(aspect, 1.0);
float radius = length(edge) / length(vec2(aspect, 1.0) * 0.5);
float shade = smoothstep(0.30, 0.98, radius) * 0.58;
// 管面轻微弯曲，屏外像素归黑，不把边缘拉伸成固定色条。
vec2 tube = uv * 2.0 - 1.0;
vec2 curved = tube * (1.0 + 0.004 * dot(tube, tube)) * 0.5 + 0.5;
float visible = step(0.0, curved.x) * step(curved.x, 1.0)
        * step(0.0, curved.y) * step(curved.y, 1.0);
float cycle = floor(time * 0.34);
float band = 1.0 - smoothstep(0.006, 0.024, abs(uv.y - fract(cycle * 0.381 + 0.19)));
float tear = step(0.95, fract(time * 0.34)) * band * 0.0020;
float rolling = 1.0 - smoothstep(0.025, 0.075, abs(uv.y - fract(time * 0.18)));
float wave = sin(uv.y * 38.0 + time * 2.1) * 0.00012
        + rolling * sin(time * 17.0) * 0.00025;
vec2 sampleUv = clamp(curved + vec2(wave + tear, 0.0), vec2(0.0), vec2(1.0));
vec4 scene = texture(source, sampleUv);
vec2 chroma = vec2((1.05 + 0.35 * sin(time * 1.7)) / size.x, 0.0);
vec3 color = vec3(texture(source, clamp(sampleUv + chroma, vec2(0.0), vec2(1.0))).r,
        scene.g, texture(source, clamp(sampleUv - chroma, vec2(0.0), vec2(1.0))).b);
vec2 cell = floor(uv * size);
float grain = fract(sin(dot(cell, vec2(12.9898, 78.233)) + floor(time * 24.0) * 0.73) * 43758.5453) - 0.5;
// 三个输出像素一组的扫描线和RGB荫罩，强度不再是不可辨认的千分之几。
float scan = 0.84 + 0.16 * pow(0.5 + 0.5 * sin(uv.y * size.y * 2.094395), 1.4);
float stripe = mod(cell.x, 3.0);
vec3 phosphor = stripe < 1.0 ? vec3(1.0, 0.88, 0.88)
        : stripe < 2.0 ? vec3(0.88, 1.0, 0.88) : vec3(0.88, 0.88, 1.0);
float hum = 1.0 - rolling * 0.055;
color = max(vec3(0.0), color * scan * phosphor * hum + grain * 0.028);
result = vec4(color * mix(vec3(1.0), vec3(0.24, 0.18, 0.32), shade) * visible, scene.a);
