#import "Common/ShaderLib/GLSLCompat.glsllib"

// inColor : rgb = teinte, a = occlusion ; inTexCoord : UV dans l'atlas ;
// inTexCoord2 : x = phase aléatoire de la carte, y = souplesse (0 près de la branche, 1 en bout)
attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec2 inTexCoord;
attribute vec4 inColor;
attribute vec2 inTexCoord2;

uniform mat4 g_WorldMatrix;
uniform mat4 g_WorldViewProjectionMatrix;
uniform vec2 m_WindDirection;
uniform float m_WindStrength;
uniform float m_Time;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec2 vUV;
varying vec4 vColor;

void main() {
    vec3 local = inPosition;
    vec3 world = (g_WorldMatrix * vec4(local, 1.0)).xyz;

    // Vent : rafales qui traversent la forêt + oscillation propre à chaque carte.
    vec2 wd = m_WindDirection;
    float wl = length(wd);
    wd = wl > 1e-4 ? wd / wl : vec2(1.0, 0.0);
    float phase = inTexCoord2.x * 6.2831853;
    float travel = dot(world.xz, wd);
    float gust = 0.5 + 0.5 * sin(travel * 0.035 - m_Time * (0.7 + 0.4 * m_WindStrength));
    float sway = sin(m_Time * 1.3 + travel * 0.20 + phase);
    float flutter = sin(m_Time * 4.7 + phase * 3.0 + world.y * 0.9);
    float amount = (0.03 + 0.20 * m_WindStrength) * (0.45 + 0.55 * gust) + (0.02 + 0.06 * m_WindStrength) * (sway + 0.35 * flutter);
    float flex = inTexCoord2.y;
    local.xz += wd * amount * flex * 1.2;
    local.y += flutter * 0.015 * flex * (0.5 + m_WindStrength);

    vWorldPos = (g_WorldMatrix * vec4(local, 1.0)).xyz;
    vNormal = normalize((g_WorldMatrix * vec4(inNormal, 0.0)).xyz);
    vUV = inTexCoord;
    vColor = inColor;
    gl_Position = g_WorldViewProjectionMatrix * vec4(local, 1.0);
}
