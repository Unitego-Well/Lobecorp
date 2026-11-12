#version 330
uniform sampler2D Sampler0;
uniform sampler2D LayerHistory;
uniform sampler2D LayerSceneDepth;
layout(std140) uniform HordeLayerParameters { ivec4 LayerViewport; ivec4 LayerSettings; };
in vec2 texCoord;
flat in uint layerUnit;
out vec4 fragColor;
uint decodeId(vec4 c) { uvec4 b=uvec4(round(c*255.0)); return b.x|(b.y<<8u)|(b.z<<16u)|(b.w<<24u); }
void main() {
 if(layerUnit==0u || texture(Sampler0,texCoord).a<0.1) discard;
 ivec2 pixel=ivec2(gl_FragCoord.xy);
 float scene=texelFetch(LayerSceneDepth,pixel,0).r;
 if(LayerSettings.y!=0 ? gl_FragCoord.z<scene : gl_FragCoord.z>scene) discard;
 for(int i=0;i<LayerSettings.x;i++) {
  ivec2 tile=ivec2(i%LayerViewport.z,i/LayerViewport.z)*LayerViewport.xy;
  if(decodeId(texelFetch(LayerHistory,tile+pixel,0))==layerUnit) discard;
 }
 fragColor=vec4(uvec4(layerUnit,layerUnit>>8u,layerUnit>>16u,layerUnit>>24u)&255u)/255.0;
}
