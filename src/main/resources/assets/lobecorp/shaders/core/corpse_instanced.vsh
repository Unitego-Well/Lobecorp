#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#moj_import <lobecorp:corpse_data.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
uniform sampler2D Sampler2;
out vec2 texCoord;
out vec4 lighting;
out float sphericalDistance;
out float cylindricalDistance;
flat out float glowing;
flat out vec4 corpseColor;

void main() {
    int instance = CorpseInfo.x + gl_InstanceID;
    vec3 relative = corpsePosition(Position, instance);
    vec3 normal = normalize(corpseDirection(Normal, instance));
    corpseColor = corpseTint(instance);
    uint light = rawWord(CorpseInstances, instance * 8 + 4);
    lighting = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, corpseColor)
    * sample_lightmap(Sampler2, ivec2(int(light & 65535u), int(light >> 16u)));
    gl_Position = ProjMat * ModelViewMat * vec4(relative, 1.0);
    sphericalDistance = length(relative);
    cylindricalDistance = max(length(relative.xz), abs(relative.y));
    glowing = float(CorpseInfo.y);
    texCoord = UV0;
}
