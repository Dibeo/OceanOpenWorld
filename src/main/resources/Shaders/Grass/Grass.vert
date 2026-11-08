#import "Common/ShaderLib/GLSLCompat.glsllib"

// Une touffe = 2 quads croisés. Attributs :
//   inNormal    : normale du TERRAIN sous la touffe (éclairage cohérent avec le sol)
//   inColor     : rgb = teinte (variation / herbe sèche), a = phase aléatoire du vent
//   inTexCoord  : (u, v) dans le motif, v = 0 à la base, 1 au sommet
//   inTexCoord2 : x = hauteur de la touffe (m), y = variante de motif (0..3, atlas 2x2), z = Y de la base
//   inTexCoord3 : x = couleur de fleur (index de palette + variation), y = 1 si la touffe est fleurie
attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec4 inColor;
attribute vec2 inTexCoord;
attribute vec3 inTexCoord2;
attribute vec2 inTexCoord3;

uniform mat4 g_WorldMatrix;
uniform mat4 g_WorldViewProjectionMatrix;
uniform vec3 g_CameraPosition;
uniform vec2 m_WindDirection;
uniform float m_WindStrength;
uniform float m_Time;
uniform float m_FadeStart;
uniform float m_FadeEnd;

varying vec2 vUV;        // coordonnées dans l'atlas
varying float vT;        // hauteur locale sur le brin (0..1)
varying float vWave;     // inclinaison due au vent : module l'éclairage (ondes de lumière)
varying vec3 vTint;
varying vec2 vFlower;
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
    float baseY = inTexCoord2.z;
    local.y = baseY + (local.y - baseY) * fade;

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
    vec2 cell = vec2(mod(inTexCoord2.y, 2.0), floor(inTexCoord2.y / 2.0));
    vUV = (inTexCoord + cell) * 0.5;
    vT = v;
    vFlower = inTexCoord3;
    vWave = (lean + osc) * v;
    vTint = inColor.rgb;
    vNormal = normalize((g_WorldMatrix * vec4(inNormal, 0.0)).xyz);
    vWorldPos = (g_WorldMatrix * vec4(local, 1.0)).xyz;
}
