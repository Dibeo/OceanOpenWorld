#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_ColorMap;
uniform sampler2D m_FlowerMap;
uniform vec3 g_CameraPosition;
uniform vec3 m_SunDirection;   // direction des rayons (comme LightingManager) : le soleil est à l'opposé
uniform vec4 m_SunColor;
uniform vec4 m_AmbientColor;

varying vec2 vUV;
varying float vT;
varying float vWave;
varying vec3 vTint;
varying vec2 vFlower;
varying vec3 vNormal;
varying vec3 vWorldPos;

const float TEXTURE_SIZE = 256.0;   // atlas 2x2 de motifs de 128 px

// Dégradé du brin (espace linéaire) : base sombre et saturée, pointe plus claire et jaune.
const vec3 BASE_COLOR = vec3(0.022, 0.068, 0.010);
const vec3 TIP_COLOR  = vec3(0.165, 0.340, 0.048);

// Palette des fleurs (albédo linéaire) : blanc, jaune, rose, violet, rouge, bleu.
const vec3 FLOWER_PALETTE[6] = vec3[6](
    vec3(0.62, 0.60, 0.54),
    vec3(0.78, 0.50, 0.02),
    vec3(0.72, 0.13, 0.30),
    vec3(0.24, 0.07, 0.52),
    vec3(0.70, 0.03, 0.03),
    vec3(0.07, 0.15, 0.68));
const vec3 FLOWER_CENTER = vec3(0.60, 0.33, 0.01);

void main() {
    vec4 texel = texture(m_ColorMap, vUV);
    // Tête de fleur : seulement sur les touffes fleuries (vFlower.y = 1).
    vec2 head = texture(m_FlowerMap, vUV).rg * step(0.5, vFlower.y);
    texel.a = max(texel.a, head.x);

    // L'alpha des mipmaps s'amincit : on le compense pour que l'herbe lointaine ne disparaisse pas.
    vec2 dx = dFdx(vUV * TEXTURE_SIZE), dy = dFdy(vUV * TEXTURE_SIZE);
    float mip = max(0.0, 0.5 * log2(max(max(dot(dx, dx), dot(dy, dy)), 1.0)));
    float alpha = texel.a * (1.0 + 0.45 * mip);
    if (alpha < 0.5) discard;

    vec3 N = normalize(vNormal);
    vec3 L = normalize(-m_SunDirection);                    // vers le soleil
    vec3 V = normalize(g_CameraPosition - vWorldPos);       // vers la caméra
    float t = clamp(vT, 0.0, 1.0);

    vec3 albedo = mix(BASE_COLOR, TIP_COLOR, pow(t, 0.8)) * texel.rgb * vTint;
    float petal = smoothstep(0.35, 0.65, head.x);
    float isFlower = petal;
    if (petal > 0.0) {
        int paletteIndex = int(clamp(floor(vFlower.x), 0.0, 5.0));
        float brightness = 0.85 + 0.30 * fract(vFlower.x);
        vec3 flowerColor = mix(FLOWER_PALETTE[paletteIndex] * brightness, FLOWER_CENTER, smoothstep(0.35, 0.65, head.y));
        albedo = mix(albedo, flowerColor, petal);
    }
    // Quand le vent incline les brins vers ou contre la lumière, le champ « respire » :
    // ondes claires et sombres qui le traversent.
    albedo *= 1.0 + 0.40 * clamp(vWave, -0.25, 0.6) * (1.0 - isFlower);

    // Occlusion : le pied des touffes reçoit peu de lumière.
    float occlusion = mix(0.22, 1.0, smoothstep(0.0, 0.65, t));
    occlusion = mix(occlusion, 1.0, isFlower);   // une fleur au bout de sa tige est toujours éclairée
    // Diffus « enveloppant » (un brin n'est pas un plan opaque) sur la normale du terrain.
    float diffuse = max(0.0, (dot(N, L) + 0.35) / 1.35);
    // Translucidité : face au soleil, la lumière traverse les brins (surtout vers la pointe).
    float transmission = pow(clamp(dot(-V, L), 0.0, 1.0), 3.0) * t;
    vec3 translucency = mix(vec3(0.95, 1.0, 0.30), vec3(1.0), isFlower) * transmission * mix(0.65, 1.1, isFlower);
    // Léger lustre.
    float sheen = pow(max(dot(N, normalize(L + V)), 0.0), 24.0) * 0.06 * t;

    vec3 light = m_AmbientColor.rgb * occlusion
               + m_SunColor.rgb * (diffuse * mix(0.45, 1.0, occlusion) + translucency + sheen);
    gl_FragColor = vec4(albedo * light, 1.0);
}
