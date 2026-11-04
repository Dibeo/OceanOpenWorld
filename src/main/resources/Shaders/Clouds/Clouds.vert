#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;

attribute vec3 inPosition;

varying vec3 vWorldPos;

void main() {
    // Le dôme ne sert que de support d'écran : le rayon de vue est reconstruit en monde
    // (position du fragment - position de la caméra), ce qui reste exact pour la caméra de
    // reflet de l'eau, dont la position diffère de celle du centre du dôme.
    vWorldPos = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
