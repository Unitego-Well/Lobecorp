#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

out vec4 vertexColor;
out vec2 texCoord;
out vec2 glyphPosition;
flat out float dissolve;
flat out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
    texCoord = UV0;
    int packedCoord = UV2.y & 65535;
    glyphPosition = vec2(float(packedCoord & 255) / 255.0, float((packedCoord >> 8) & 255) / 160.0);
    dissolve = float(UV2.x & 4095) / 4095.0;
    seed = float((UV2.x >> 12) & 7) / 7.0;
}
