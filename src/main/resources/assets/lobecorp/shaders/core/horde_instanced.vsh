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

#ifdef HORDE_IMPOSTOR
layout(std140) uniform HordeBillboard {
    vec4 SpriteBounds;
    vec4 CellParams;
};
#endif

out vec2 texCoord;
out vec4 lighting;
out float sphericalDistance;
out float cylindricalDistance;
flat out float glowing;

void main() {
    vec4 location = InstanceData[gl_InstanceID * 2];
    vec4 state = InstanceData[gl_InstanceID * 2 + 1];
#ifdef HORDE_IMPOSTOR
    vec2 toCamera = -location.xz;
    float horizontal = max(length(toCamera), 0.0001);
    vec2 localCamera = mat2(state.x, state.y, -state.y, state.x) * toCamera;
    int view = 1;
    bool mirrored = false;
    if (-location.y > horizontal) {
        view = 0;
    } else if (abs(localCamera.x) > abs(localCamera.y)) {
        view = 2;
        mirrored = localCamera.x < 0.0;
    } else if (localCamera.y < 0.0) {
        view = 3;
    }
    vec3 position;
    if (view == 0) {
        position = vec3(Position.x * SpriteBounds.x * 2.0, (SpriteBounds.y + SpriteBounds.z) * 0.5,
            (Position.y - 0.5) * SpriteBounds.x * 2.0);
        position.xz = mat2(state.x, -state.y, state.y, state.x) * position.xz;
    } else {
        vec2 right = vec2(toCamera.y, -toCamera.x) / horizontal;
        position = vec3(right.x * Position.x * SpriteBounds.x * 2.0,
            mix(SpriteBounds.y, SpriteBounds.z, Position.y), right.y * Position.x * SpriteBounds.x * 2.0);
    }
    vec3 normal = vec3(0.0, 1.0, 0.0);
    vec2 uv = vec2(mirrored ? 1.0 - UV0.x : UV0.x, UV0.y);
    float frame = floor(fract(state.z) * SpriteBounds.w);
    texCoord = (vec2(float(view), frame) * CellParams.x + vec2(2.0)
        + uv * (CellParams.x - 4.0)) / CellParams.yz;
#else
    vec3 position = mix(Position, NextPosition, state.z);
    vec3 normal = mix(Color.xyz * 2.0 - 1.0, Normal, state.z);
    normal = length(normal) > 0.0001 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
    mat2 rotation = mat2(state.x, -state.y, state.y, state.x);
    position.xz = rotation * position.xz;
    normal.xz = rotation * normal.xz;
    texCoord = UV0;
#endif
    vec4 viewPosition = ModelViewMat * vec4(position + location.xyz, 1.0);
    gl_Position = ProjMat * viewPosition;
    sphericalDistance = length(viewPosition.xyz);
    cylindricalDistance = max(length(viewPosition.xz), abs(viewPosition.y));
    glowing = state.w >= 0.0 ? 1.0 : 0.0;
    int packedLight = int(state.w >= 0.0 ? state.w : -state.w - 1.0);
    ivec2 lightUv = ivec2(packedLight & 65535, packedLight >> 16) / 16;
    float shade = 0.6 + 0.4 * max(normal.y, 0.0);
    lighting = vec4(texelFetch(Sampler2, clamp(lightUv, ivec2(0), ivec2(15)), 0).rgb * shade, location.w);
}
