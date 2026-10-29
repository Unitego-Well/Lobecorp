#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

#moj_import <lobecorp:horde_motion.glsl>
#moj_import <lobecorp:horde_cards.glsl>

out vec2 texCoord;
out vec4 lighting;
out float sphericalDistance;
out float cylindricalDistance;
flat out float glowing;

// 动画已烘焙为每帧静态顶点；这里只选帧并读取位置/法线。
void animate(int vertex, int frameStart, inout vec3 position, inout vec3 normal) {
    int offset = (frameStart + vertex) * 6;
    position = vec3(scalar(HordeAnimation, offset), scalar(HordeAnimation, offset + 1),
        scalar(HordeAnimation, offset + 2));
    normal = vec3(scalar(HordeAnimation, offset + 3), scalar(HordeAnimation, offset + 4),
        scalar(HordeAnimation, offset + 5));
}

void main() {
    int ordinal = gl_InstanceID + int(RowColumns.w);
    int row = ordinal / int(RowColumns.z);
    int column = int(RowColumns.y) + ordinal % int(RowColumns.z);
    float station = RowColumns.x - float(row) * SpacingLength.x;
    if (station < SegmentRegions.x || station >= SegmentRegions.y || station < 0.0 || station >= SpacingLength.w) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        texCoord = UV0;
        lighting = vec4(0.0);
        sphericalDistance = 0.0;
        cylindricalDistance = 0.0;
        glowing = 0.0;
        return;
    }
    int cached = (MotionInfo.x + gl_InstanceID) * HORDE_MOTION_VALUES;
    vec3 point = vector3(HordeMotion, cached);
    vec2 heading = vec2(scalar(HordeMotion, cached + 5), scalar(HordeMotion, cached + 6));
    mat2 yaw = mat2(-heading.y, heading.x, -heading.x, -heading.y);
    vec3 position = Position;
    vec3 normal = Normal;
    int frameStart = int(scalar(HordeMotion, cached + 7));
    int vertex = gl_VertexID;
    if (FrameLod.z > 0.5) {
        vec3 center = (vector3(HordeAnimation, frameStart * 6)
        + vector3(HordeAnimation, (frameStart + 2) * 6)) * 0.5;
        vertex = hordeCardVertex(vertex, point, yaw, center);
    }
    animate(vertex, frameStart, position, normal);
    position.xz = yaw * position.xz;
    normal.xz = yaw * normal.xz;
    normal = length(normal) > 0.0001 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
    vec3 relative = position + point + OriginTime.xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(relative, 1.0);
    sphericalDistance = length(relative);
    cylindricalDistance = max(length(relative.xz), abs(relative.y));
    float packedLight = scalar(HordeMotion, cached + 4);
    vec2 light = packedLight < 0.0 ? vec2(-1.0) : vec2(mod(packedLight, 16.0), floor(packedLight / 16.0));
    lighting = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, vec4(1.0))
        * sample_lightmap(Sampler2, ivec2(light * 16.0));
    if (light.x < 0.0) {
        lighting.a = 0.0;
    }
    glowing = AnimationInfo.w;
    texCoord = UV0;
    if (FrameLod.z > 0.5) {
        // 片图集：UV编码(方向*2+角点)/12，动画帧从运动缓冲的帧基址取。
        int corner = vertex % 4;
        float face = float(vertex / 4);
        float cornerU = corner == 1 || corner == 2 ? 1.0 : 0.0;
        int frame = int(scalar(HordeMotion, cached + 7)) / int(AnimationInfo.x);
        texCoord = vec2((face + cornerU) / 6.0, (float(frame) + (corner >= 2 ? 1.0 : 0.0)) / AnimationInfo.y);
    }
}
