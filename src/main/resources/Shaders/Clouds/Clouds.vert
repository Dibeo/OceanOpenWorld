#import "Common/ShaderLib/GLSLCompat.glsllib"

// Supplies world position for view-ray reconstruction; keep its varying interface paired with
// Clouds.frag. The dome is a screen-space support surface, not the cloud layer's geometry.
uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;

attribute vec3 inPosition;

varying vec3 vWorldPos;

void main() {
    // The dome is only a screen-space support surface. Reconstruct the view ray in world space
    // from fragment position minus camera position; this also works for water-reflection cameras,
    // whose position differs from the dome center.
    vWorldPos = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
