#import "Common/ShaderLib/GLSLCompat.glsllib"

// inColor : rgb = tint (rock, base soil, moss), a = ambient occlusion
// Passes world-space vertex data and vertex tint/occlusion to Rock.frag. TerrainChunk supplies
// these position, normal, and color attributes.
attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec4 inColor;

uniform mat4 g_WorldMatrix;
uniform mat4 g_WorldViewProjectionMatrix;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec4 vColor;

void main() {
    vWorldPos = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;
    vNormal = normalize((g_WorldMatrix * vec4(inNormal, 0.0)).xyz);
    vColor = inColor;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
