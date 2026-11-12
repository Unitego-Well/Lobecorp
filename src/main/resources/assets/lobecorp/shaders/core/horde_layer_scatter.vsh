#version 330
uniform sampler2D LayerHistory;
// 沿用原有112字节参数布局；矩阵与球体字段在散射中不使用。
layout(std140) uniform HordeLayerMaskParameters {
    mat4 LayerModelView;
    ivec4 MaskViewport;
    ivec4 MaskSettings;
    vec4 MaskBounds;
};
uint decodeId(vec4 color) {
    uvec4 bytes = uvec4(round(color * 255.0));
    return bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u);
}
void main() {
    gl_PointSize = 1.0;
    // draw(first,count)让顶点编号包含分块偏移，每个有效历史像素仅生成一个点。
    int pixelsPerLayer = MaskViewport.x * MaskViewport.y;
    int source = gl_VertexID;
    int layer = source / pixelsPerLayer;
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    if (layer >= MaskSettings.x) {
        return;
    }
    int local = source % pixelsPerLayer;
    ivec2 tile = ivec2(layer % MaskViewport.z, layer / MaskViewport.z) * MaskViewport.xy;
    ivec2 pixel = tile + ivec2(local % MaskViewport.x, local / MaskViewport.x);
    uint encoded = decodeId(texelFetch(LayerHistory, pixel, 0));
    if (encoded == 0u || encoded > uint(MaskSettings.y)) {
        return;
    }
    int identity = int(encoded - 1u);
    ivec2 destination = ivec2(identity % MaskSettings.z, identity / MaskSettings.z);
    vec2 center = (vec2(destination) + 0.5) / vec2(MaskSettings.zw);
    gl_Position = vec4(center * 2.0 - 1.0, 0.0, 1.0);
    // 开启程序点大小时明确为1；关闭时由固定点大小1保持相同覆盖。
}
