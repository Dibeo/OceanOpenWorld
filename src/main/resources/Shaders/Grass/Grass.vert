#import "Common/ShaderLib/GLSLCompat.glsllib"

// Each clump consists of two crossed quads. Attributes:
//   inNormal    : terrain normal below the clump (keeps lighting consistent with the ground)
//   inColor     : rgb = tint (variation / dry grass), a = random wind phase
//   inTexCoord  : (u, v) in the pattern, with v = 0 at the base and 1 at the tip
//   inTexCoord2 : x = clump height (m), y = pattern variant (0..3, 2x2 atlas), z = base Y
//   inTexCoord3 : x = flower palette index and variation, y = 1 if the clump has flowers
// Generates wind-bent grass cross-quads and passes atlas/flower coordinates to Grass.frag.
// Vertex attribute layouts are supplied by TerrainChunk; uniforms are declared in Grass.j3md.
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

varying vec2 vUV;        // atlas coordinates
varying float vT;        // local blade height in [0, 1]
varying float vWave;     // wind lean, used to modulate lighting waves
varying vec3 vTint;
varying vec2 vFlower;
varying vec3 vNormal;
varying vec3 vWorldPos;

void main() {
    vec3 local = inPosition;
    float v = inTexCoord.y;
    float height = inTexCoord2.x;
    vec3 world = (g_WorldMatrix * vec4(local, 1.0)).xyz;

    // Fade with distance by flattening each clump to zero area, avoiding a sudden pop.
    float dist = distance(world.xz, g_CameraPosition.xz);
    float fade = 1.0 - smoothstep(m_FadeStart, m_FadeEnd, dist);
    float baseY = inTexCoord2.z;
    local.y = baseY + (local.y - baseY) * fade;

    // Wind combines average downwind lean, gust fronts moving across the field, and a
    // per-clump oscillation with a random phase.
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
    local.y -= bend * bend * 0.6 / max(height, 0.1);   // approximately preserve blade length

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
