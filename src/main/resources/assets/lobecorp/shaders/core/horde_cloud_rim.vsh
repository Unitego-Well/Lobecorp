#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(std140) uniform CloudInfo {
    vec4 CloudColor;
    vec3 CloudOffset;
    vec3 CellSize;
};
layout(std140) uniform RimParameters {
    vec4 SourceStrength;
    vec4 TimeCell;
};
uniform isamplerBuffer CloudFaces;

out vec2 cellUv;
out float vertexDistance;
out float cloudAlpha;
flat out ivec2 cloudCell;
flat out int eligibleFace;

void main() {
    int record = (gl_VertexID / 4) * 3;
    int flags = texelFetch(CloudFaces, record + 2).r;
    int direction = flags & 7;
    int x = (texelFetch(CloudFaces, record).r << 1) | ((flags >> 7) & 1);
    int z = (texelFetch(CloudFaces, record + 1).r << 1) | ((flags >> 6) & 1);
    int corner = gl_VertexID & 3;
    vec2 uv = vec2(corner < 2 ? 1.0 : 0.0, corner == 1 || corner == 2 ? 1.0 : 0.0);
    if (direction == 1) uv.x = 1.0 - uv.x;
    float y = direction == 1 ? CellSize.y + 0.01 : -0.01;
    vec3 pos = vec3((vec2(x, z) + uv) * CellSize.xz, 0.0).xzy;
    pos.y = y;
    pos += CloudOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    cellUv = uv;
    cloudCell = ivec2(x, z) + ivec2(TimeCell.yz);
    eligibleFace = direction < 2 && (flags & 16) == 0 ? 1 : 0;
    vertexDistance = fog_spherical_distance(pos);
    cloudAlpha = CloudColor.a;
}
