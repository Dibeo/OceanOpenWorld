#import "Common/ShaderLib/GLSLCompat.glsllib"

// inColor : rgb = teinte de l'écorce, a = occlusion ; inTexCoord : u = tour du tronc, v = longueur (m)
attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec2 inTexCoord;
attribute vec4 inColor;

uniform mat4 g_WorldMatrix;
uniform mat4 g_WorldViewProjectionMatrix;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec2 vUV;
varying vec4 vColor;

void main() {
    vWorldPos = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;
    vNormal = normalize((g_WorldMatrix * vec4(inNormal, 0.0)).xyz);
    vUV = inTexCoord;
    vColor = inColor;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
