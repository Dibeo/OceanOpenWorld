#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_ColorMap;
uniform vec3 g_CameraPosition;
uniform vec3 m_SunDirection;   // direction des rayons (comme LightingManager)
uniform vec4 m_SunColor;
uniform vec4 m_AmbientColor;
uniform float m_AlphaDiscardThreshold;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec2 vUV;
varying vec4 vColor;

const float TEXTURE_SIZE = 256.0;   // atlas 2x2 de 128 px

void main() {
    vec4 texel = texture(m_ColorMap, vUV);

    // L'alpha des mipmaps s'amincit : on le compense pour que le feuillage lointain ne s'évapore pas.
    vec2 dx = dFdx(vUV * TEXTURE_SIZE), dy = dFdy(vUV * TEXTURE_SIZE);
    float mip = max(0.0, 0.5 * log2(max(max(dot(dx, dx), dot(dy, dy)), 1.0)));
    float alpha = texel.a * (1.0 + 0.5 * mip);
    if (alpha < m_AlphaDiscardThreshold) discard;

    // Face tournée vers la caméra (cartes à double face).
    vec3 N = normalize(vNormal);
    vec3 V = normalize(g_CameraPosition - vWorldPos);
    if (dot(N, V) < -0.35) N = -N;

    vec3 albedo = vColor.rgb * texel.rgb;
    vec3 L = normalize(-m_SunDirection);
    float diffuse = max((dot(N, L) + 0.4) / 1.4, 0.0);
    // Translucidité : face au soleil, la lumière traverse les feuilles (jaune-vert vif).
    float transmission = pow(clamp(dot(-V, L), 0.0, 1.0), 2.5);
    vec3 translucency = vec3(0.85, 1.0, 0.30) * transmission * 0.9;
    vec3 ambient = m_AmbientColor.rgb * mix(0.60, 1.0, N.y * 0.5 + 0.5);

    float ao = vColor.a;
    vec3 light = ambient * ao + m_SunColor.rgb * (diffuse * mix(0.35, 1.0, ao) + translucency * ao);
    gl_FragColor = vec4(albedo * light, 1.0);
}
