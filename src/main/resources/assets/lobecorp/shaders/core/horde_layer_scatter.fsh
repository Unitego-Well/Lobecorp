#version 330
out vec4 fragColor;
void main() {
    // 重复命中统一写入相同标志，不读取目标纹理，也不需要原子操作。
    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
