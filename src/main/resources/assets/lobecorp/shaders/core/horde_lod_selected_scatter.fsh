#version 330
out vec4 fragColor;
void main() {
    // 保持原始整字编码为1，不写成255或带alpha的其它非零值。
    fragColor = vec4(1.0 / 255.0, 0.0, 0.0, 0.0);
}
