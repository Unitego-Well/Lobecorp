#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 NextPosition;
in vec3 Normal;

layout(std140) uniform HordeInstances {
    vec4 InstanceData[512];
};

uniform sampler2D Sampler2;

out vec2 texCoord;
out vec4 lighting;
out float sphericalDistance;
out float cylindricalDistance;
flat out float glowing;

void main() {
    vec4 location = InstanceData[gl_InstanceID * 2];
    vec4 state = InstanceData[gl_InstanceID * 2 + 1];
    vec3 position = mix(Position, NextPosition, state.z);
    vec3 normal = mix(Color.xyz * 2.0 - 1.0, Normal, state.z);
    normal = length(normal) > 0.0001 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
    mat2 rotation = mat2(state.x, -state.y, state.y, state.x);
    position.xz = rotation * position.xz;
    normal.xz = rotation * normal.xz;
    vec4 view = ModelViewMat * vec4(position + location.xyz, 1.0);
    gl_Position = ProjMat * view;
    sphericalDistance = length(view.xyz);
    cylindricalDistance = max(length(view.xz), abs(view.y));
    glowing = state.w >= 0.0 ? 1.0 : 0.0;
    int packedLight = int(state.w >= 0.0 ? state.w : -state.w - 1.0);
    ivec2 lightUv = ivec2(packedLight & 65535, packedLight >> 16) / 16;
    float shade = 0.6 + 0.4 * max(normal.y, 0.0);
    lighting = vec4(texelFetch(Sampler2, clamp(lightUv, ivec2(0), ivec2(15)), 0).rgb * shade, location.w);
    texCoord = UV0;
}
