#import "Common/ShaderLib/GLSLCompat.glsllib"

// Nuages volumétriques : une couche plane [CloudBase, CloudBase + CloudThickness] traversée par
// ray-marching. La densité vient de la texture de bruit (Perlin, générée depuis la seed du monde),
// échantillonnée en 2D avec un décalage dépendant de l'altitude pour donner un pseudo-volume.
//
// Éclairage : Beer-Lambert vers le soleil, diffusion multiple approchée (3 octaves, Wrenninge),
// phase de Henyey-Greenstein à double lobe, ambiant dégradé avec la hauteur.

uniform vec3 g_CameraPosition;

uniform sampler2D m_NoiseMap;
uniform vec3  m_SunDirection;   // normalisée, vers le soleil
uniform vec3  m_SunColor;
uniform vec3  m_AmbientColor;
uniform vec3  m_HorizonColor;
uniform vec2  m_WindOffset;
uniform vec2  m_Shear;
uniform float m_CloudBase;
uniform float m_CloudThickness;
uniform float m_TileMeters;
uniform float m_MaxDistance;
uniform float m_Coverage;
uniform float m_Density;        // extinction maximale (1/m)
uniform float m_Erosion;
uniform int   m_Steps;
uniform int   m_LightSteps;

varying vec3 vWorldPos;

const float PI = 3.14159265359;
// Entiers : la texture reste sans couture quand on la répète.
const float SHAPE_MIN_LOD = 2.0;   // retire les 2 octaves les plus fins de la forme
const float DETAIL_MID_SCALE = 2.0;
const float DETAIL_A_SCALE = 5.0;
const float DETAIL_B_SCALE = 11.0;

// Détail fin : la couche est découpée en tranches d'altitude ; chaque tranche lit la texture à un
// décalage fixe différent, et on fond entre tranches voisines. Un simple cisaillement linéaire ne
// suffit pas : un rayon aligné avec lui suivrait la même colonne de bruit (traînées).
const int SLICES = 10;
const vec2 SLICE_OFFSET[11] = vec2[11](
    vec2(0.00, 0.00), vec2(0.41, 0.77), vec2(0.83, 0.29), vec2(0.19, 0.53),
    vec2(0.67, 0.91), vec2(0.52, 0.13), vec2(0.07, 0.64), vec2(0.94, 0.46),
    vec2(0.31, 0.08), vec2(0.76, 0.62), vec2(0.58, 0.35));
// Vers le soleil, l'extinction effective est réduite : la diffusion multiple laisse la lumière
// pénétrer bien plus loin que Beer-Lambert seul ne le prédit.
const float LIGHT_EXTINCTION = 0.7;

float gTexelMeters;   // taille d'un texel de la texture, en mètres

// Niveau de mipmap pour une empreinte de pixel donnée (mètres) : évite le scintillement au loin.
float lodFor(float footprint, float scale) {
    return max(log2(max(footprint * scale / gTexelMeters, 1.0)), 0.0);
}

// shift : décalage (mètres) appliqué sur toute l'épaisseur de la couche.
vec2 noiseUV(vec3 p, float h, vec2 shift) {
    return (p.xz + m_WindOffset + shift * h) / m_TileMeters;
}

// Lit un canal de détail avec fondu entre les deux tranches d'altitude voisines.
// scale : nombre de répétitions de la texture dans une tuile (entier : raccord sans couture).
float detailSample(vec3 p, float h, float scale, float lod, int slotShift, int channel) {
    float z = clamp(h, 0.0, 1.0) * float(SLICES);
    int i0 = int(floor(z));
    int i1 = min(i0 + 1, SLICES);
    float f = fract(z);
    f = f * f * (3.0 - 2.0 * f);
    vec2 base = (p.xz + m_WindOffset) / m_TileMeters * scale;
    vec4 s0 = textureLod(m_NoiseMap, base + SLICE_OFFSET[(i0 + slotShift) % 11], lod);
    vec4 s1 = textureLod(m_NoiseMap, base + SLICE_OFFSET[(i1 + slotShift) % 11], lod);
    return channel == 0 ? mix(s0.b, s1.b, f) : mix(s0.a, s1.a, f);
}

// Forme générale (0..1), sans érosion : sert aussi à l'ombrage vers le soleil.
float shapeDensity(vec3 p, float h, float lod) {
    // La forme est extrudée en hauteur : on en retire les fréquences fines (mipmap minimal),
    // qui sinon forment des aiguilles verticales. Le relief fin vient du détail par tranches.
    vec4 n = textureLod(m_NoiseMap, noiseUV(p, h, m_Shear), max(lod, SHAPE_MIN_LOD));
    float f = mix(n.r, n.g, 0.45);
    float threshold = mix(0.80, 0.32, m_Coverage);
    float core = smoothstep(threshold, threshold + 0.34, f);
    // Hauteur des sommets pilotée surtout par la météo (grande échelle) : dômes larges, pas de flèches.
    float tall = mix(n.g, f, 0.25);
    float maxH = mix(0.40, 1.0, smoothstep(threshold, threshold + 0.40, tall));
    float profile = smoothstep(0.0, 0.12, h) * (1.0 - smoothstep(maxH * 0.55, maxH, h));
    return core * profile;
}

// Érosion par le détail bosselé : creuse les bords, plus fortement vers le sommet.
float erode(float d, vec3 p, float h, float footprint) {
    // Une texture 2D est extrudée en colonnes verticales : vues de côté, ce sont des traînées
    // dirigées vers le zénith. Le détail est donc décorrélé d'une tranche d'altitude à l'autre.
    float m = detailSample(p, h, DETAIL_MID_SCALE, lodFor(footprint, DETAIL_MID_SCALE), 5, 0);
    float a = detailSample(p, h, DETAIL_A_SCALE, lodFor(footprint, DETAIL_A_SCALE), 0, 0);
    float b = detailSample(p, h, DETAIL_B_SCALE, lodFor(footprint, DETAIL_B_SCALE), 3, 1);
    float detail = smoothstep(0.15, 0.85, m * 0.45 + a * 0.35 + b * 0.20);   // renforce le contraste bosses / creux
    float e = (1.0 - detail) * m_Erosion * mix(0.6, 1.0, h);
    return clamp((d - e) / (1.0 - e), 0.0, 1.0);
}

float hg(float c, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * PI * pow(1.0 + g2 - 2.0 * g * c, 1.5));
}

// Profondeur optique entre p et le haut de la couche, dans la direction du soleil.
float lightOpticalDepth(vec3 p, float footprint) {
    float od = 0.0;
    float s = m_CloudThickness * 0.06;
    vec3 q = p;
    for (int i = 0; i < m_LightSteps; i++) {
        q += m_SunDirection * s;
        float h = (q.y - m_CloudBase) / m_CloudThickness;
        if (h > 1.0) break;
        od += shapeDensity(q, h, lodFor(footprint, 1.0)) * m_Density * LIGHT_EXTINCTION * s;
        s *= 1.6;
    }
    return od;
}

vec3 scatteredLight(float od, float cosT, float h) {
    float e = 0.0, a = 1.0, b = 1.0, c = 1.0;
    for (int i = 0; i < 3; i++) {
        float phase = mix(hg(cosT, -0.2 * c), hg(cosT, 0.6 * c), 0.7) * 4.0 * PI;
        e += b * exp(-od * a) * phase;
        a *= 0.25; b *= 0.5; c *= 0.5;
    }
    e /= 1.75;
    return m_SunColor * e + m_AmbientColor * mix(0.55, 1.0, h);
}

void main() {
    vec3 dir = normalize(vWorldPos - g_CameraPosition);
    // Dérivées calculées avant tout branchement (flux uniforme).
    float pixelAngle = max(length(dFdx(dir)), length(dFdy(dir)));
    gTexelMeters = m_TileMeters / float(textureSize(m_NoiseMap, 0).x);

    gl_FragColor = vec4(0.0);

    vec3 ro = g_CameraPosition;
    float top = m_CloudBase + m_CloudThickness;
    if (dir.y < 0.005 || ro.y > top) return;

    float tNear = max((m_CloudBase - ro.y) / dir.y, 0.0);
    float tFar = (top - ro.y) / dir.y;
    tFar = min(tFar, tNear + m_CloudThickness * 8.0);   // rasant : on borne la longueur marchée
    if (tNear >= m_MaxDistance || tFar <= tNear) return;
    tFar = min(tFar, m_MaxDistance);

    float dt = (tFar - tNear) / float(m_Steps);
    // Bruit de gradient entrelacé : casse les bandes sans aucun aléa non déterministe.
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float cosT = dot(dir, m_SunDirection);

    vec3 acc = vec3(0.0);
    float T = 1.0;
    for (int i = 0; i < m_Steps; i++) {
        float t = tNear + (float(i) + jitter) * dt;
        vec3 p = ro + dir * t;
        float h = (p.y - m_CloudBase) / m_CloudThickness;
        float footprint = max(t * pixelAngle, 1.0);

        float d = shapeDensity(p, h, lodFor(footprint, 1.0));
        if (d <= 0.0) continue;
        d = erode(d, p, h, footprint);
        if (d <= 0.001) continue;

        float a = 1.0 - exp(-d * m_Density * dt);
        vec3 light = scatteredLight(lightOpticalDepth(p, footprint), cosT, h);
        acc += T * a * light;
        T *= 1.0 - a;
        if (T < 0.02) break;
    }

    float alpha = 1.0 - T;
    if (alpha < 0.001) return;

    vec3 col = acc / alpha;                       // couleur non pré-multipliée
    col = vec3(1.0) - exp(-col * 1.2);            // compression douce des hautes lumières
    float haze = smoothstep(0.0, m_MaxDistance, tNear);
    col = mix(col, m_HorizonColor, haze * 0.85);  // perspective atmosphérique
    alpha *= 1.0 - smoothstep(0.7 * m_MaxDistance, m_MaxDistance, tNear);

    gl_FragColor = vec4(col * alpha, alpha);
}
