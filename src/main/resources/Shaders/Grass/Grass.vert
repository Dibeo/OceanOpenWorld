#import "Common/ShaderLib/GLSLCompat.glsllib"

// Une touffe = 2 quads croisés. Attributs :
//   inNormal    : normale du TERRAIN sous la touffe (éclairage cohérent avec le sol)
//   inColor     : rgb = teinte (variation / herbe sèche), a = phase aléatoire du vent
//   inTexCoord  : (u, v), v = 0 à la base, 1 au sommet
//   inTexCoord2 : x = hauteur de la touffe (mètres)
attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec4 inColor;
attribute vec2 inTexCoord;
attribute vec2 inTexCoord2;

uniform mat4 g_WorldMatrix;
uniform mat4 g_WorldViewProjectionMatrix;
uniform vec3 g_CameraPosition;
uniform vec2 m_WindDirection;
uniform float m_WindStrength;
uniform float m_Time;
uniform float m_FadeStart;
uniform float m_FadeEnd;

varying vec2 vUV;
varying vec3 vTint;
varying vec3 vNormal;
varying vec3 vWorldPos;

void main() {
    vec3 local = inPosition;
    float v = inTexCoord.y;
    float height = inTexCoord2.x;
    vec3 world = (g_WorldMatrix * vec4(local, 1.0)).xyz;

    // Fondu en distance : la touffe s'aplatit jusqu'à une surface nulle (pas d'apparition brutale).
    float dist = distance(world.xz, g_CameraPosition.xz);
    float fade = 1.0 - smoothstep(m_FadeStart, m_FadeEnd, dist);
    float baseY = local.y - v * height;
    local.y = baseY + v * height * fade;

    // Vent : inclinaison moyenne sous le vent, modulée par des fronts de rafale qui traversent
    // le champ, plus une oscillation propre à chaque touffe (phase aléatoire).
    vec2 wd = m_WindDirection;
    float wl = length(wd);
    wd = wl > 1e-4 ? wd / wl : vec2(1.0, 0.0);
    vec2 side = vec2(-wd.y, wd.x);
    float phase = inColor.a * 6.2831853;
    float travel = dot(world.xz, wd);
    float gust = 0.5 + 0.5 * sin(travel * 0.045 - m_Time * (0.8 + 0.5 * m_WindStrength));
    float sway = sin(m_Time * 1.7 + travel * 0.35 + phase);
    float flutter = sin(m_Time * 5.3 + phase * 3.0 + world.x * 1.3);
    float lean = (0.04 + 0.35 * m_WindStrength) * (0.45 + 0.55 * gust);
    float osc = (sway + 0.35 * flutter) * (0.03 + 0.12 * m_WindStrength);
    // Base rigide, pointe souple.
    float bend = (lean + osc) * pow(v, 1.6) * height * fade;
    local.xz += wd * bend + side * (osc * 0.35 * pow(v, 1.6) * height * fade);
    local.y -= bend * bend * 0.6 / max(height, 0.1);   // la longueur du brin se conserve à peu près

    gl_Position = g_WorldViewProjectionMatrix * vec4(local, 1.0);
    vUV = inTexCoord;
    vTint = inColor.rgb;
    vNormal = normalize((g_WorldMatrix * vec4(inNormal, 0.0)).xyz);
    vWorldPos = (g_WorldMatrix * vec4(local, 1.0)).xyz;
}
