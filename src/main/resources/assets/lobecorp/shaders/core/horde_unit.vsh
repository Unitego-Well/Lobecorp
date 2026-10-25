#version 330

#moj_import <lobecorp:horde_motion.glsl>

flat out int unitRowDelta;
flat out int unitColumn;

void main() {
    int ordinal = gl_VertexID / 6 + int(RowColumns.w);
    unitRowDelta = ordinal / int(RowColumns.z);
    unitColumn = int(RowColumns.y) + ordinal % int(RowColumns.z);
    float station = RowColumns.x - float(unitRowDelta) * SpacingLength.x;
    ivec2 address = unitAddress(unitRow(uint(unitRowDelta)), uint(unitColumn), HordeUnitPrevious);
    if (address.x < 0 || station < SegmentRegions.x || station >= SegmentRegions.y
        || station < 0.0 || station >= SpacingLength.w) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    int triangle = gl_VertexID % 6;
    int cornerIndex = triangle == 3 ? 2 : triangle == 4 ? 3 : triangle == 5 ? 0 : triangle;
    vec2 corner = vec2(cornerIndex == 1 || cornerIndex == 2 ? float(HORDE_UNIT_VALUES) : 0.0,
        cornerIndex >= 2 ? 1.0 : 0.0);
    vec2 pixel = vec2(address) + corner;
    gl_Position = vec4(pixel / vec2(textureSize(HordeUnitPrevious, 0)) * 2.0 - 1.0, 0.0, 1.0);
}
