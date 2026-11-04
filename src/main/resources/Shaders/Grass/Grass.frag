#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_ColorMap;
uniform vec3 g_CameraPosition;
uniform vec3 m_SunDirection;   // direction des rayons (comme LightingManager) : le soleil est à l'opposé
uniform vec4 m_SunColor;
uniform vec4 m_AmbientColor;

varying vec2 vUV;
varying vec3 vTint;
varying vec3 vNormal;
varying vec3 vWorldPos;

const float TEXTURE_SIZE = 128.0;

// Dégradé du brin (espace linéaire) : base sombre et saturée, pointe plus claire et jaune.
const vec3 BASE_COLOR = vec3(0.022, 0.068, 0.010);
const vec3 TIP_COLOR  = vec3(0.165, 0.340, 0.048);

void main() {
    vec4 texel = texture(m_ColorMap, vUV);

    // L'alpha des mipmaps s'amincit : on le compense pour que l'herbe lointaine ne disparaisse pas.
    vec2 dx = dFdx(vUV * TEXTURE_SIZE), dy = dFdy(vUV * TEXTURE_SIZE);
    float mip = max(0.0, 0.5 * log2(max(max(dot(dx, dx), dot(dy, dy)), 1.0)));
    float alpha = texel.a * (1.0 + 0.45 * mip);
    if (alpha < 0.5) discard;

    vec3 N = normalize(vNormal);
    vec3 L = normalize(-m_SunDirection);                    // vers le soleil
    vec3 V = normalize(g_CameraPosition - vWorldPos);       // vers la caméra
    float t = clamp(vUV.y, 0.0, 1.0);

    vec3 albedo = mix(BASE_COLOR, TIP_COLOR, pow(t, 0.8)) * texel.rgb * vTint;

    // Occlusion : le pied des touffes reçoit peu de lumière.
    float occlusion = mix(0.22, 1.0, smoothstep(0.0, 0.65, t));
    // Diffus « enveloppant » (un brin n'est pas un plan opaque) sur la normale du terrain.
    float diffuse = max(0.0, (dot(N, L) + 0.35) / 1.35);
    // Translucidité : face au soleil, la lumière traverse les brins (surtout vers la pointe).
    float transmission = pow(clamp(dot(-V, L), 0.0, 1.0), 3.0) * t;
    vec3 translucency = vec3(0.95, 1.0, 0.30) * transmission * 0.65;
    // Léger lustre.
    float sheen = pow(max(dot(N, normalize(L + V)), 0.0), 24.0) * 0.06 * t;

    vec3 light = m_AmbientColor.rgb * occlusion
               + m_SunColor.rgb * (diffuse * mix(0.45, 1.0, occlusion) + translucency + sheen);
    gl_FragColor = vec4(albedo * light, 1.0);
}
