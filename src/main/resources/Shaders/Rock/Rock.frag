#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_NoiseMap;
uniform vec3 g_CameraPosition;
uniform vec3 m_SunDirection;   // direction des rayons (comme LightingManager)
uniform vec4 m_SunColor;
uniform vec4 m_AmbientColor;
uniform float m_TextureScale;
uniform float m_BumpStrength;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec4 vColor;

// Échantillonnage triplanaire : pas d'UV sur les rochers, pas d'étirement sur les faces inclinées.
vec3 triplanar(vec3 p, vec3 weights) {
    vec3 x = texture(m_NoiseMap, p.zy).rgb;
    vec3 y = texture(m_NoiseMap, p.xz).rgb;
    vec3 z = texture(m_NoiseMap, p.xy).rgb;
    return x * weights.x + y * weights.y + z * weights.z;
}

void main() {
    vec3 N = normalize(vNormal);
    vec3 weights = pow(abs(N), vec3(4.0));
    weights /= (weights.x + weights.y + weights.z);

    vec3 p = vWorldPos / m_TextureScale;
    vec3 tex = triplanar(p, weights);                       // r taches, g grain, b fissures
    vec3 texFine = triplanar(p * 3.7, weights);             // même texture à une autre échelle

    // Relief : hauteur = fissures + grain, dérivée écran (sans tangentes).
    float height = tex.b * 0.6 + texFine.g * 0.25 + texFine.b * 0.35;
    vec3 dpdx = dFdx(vWorldPos), dpdy = dFdy(vWorldPos);
    vec3 r1 = cross(dpdy, N), r2 = cross(N, dpdx);
    float det = dot(dpdx, r1);
    vec3 grad = sign(det) * (dFdx(height) * r1 + dFdy(height) * r2);
    N = normalize(abs(det) * N - m_BumpStrength * 0.04 * grad);

    // Albédo : teinte du sommet modulée par taches, grain et fissures (qui assombrissent).
    float tone = 0.72 + 0.56 * tex.r;
    float grain = 0.88 + 0.24 * texFine.g;
    float cracks = mix(0.55, 1.0, smoothstep(0.30, 0.62, tex.b));
    vec3 albedo = vColor.rgb * tone * grain * cracks;

    vec3 L = normalize(-m_SunDirection);
    vec3 V = normalize(g_CameraPosition - vWorldPos);
    float diffuse = max(dot(N, L), 0.0);
    // Ambiant hémisphérique : le dessus voit le ciel, le dessous renvoie le sol.
    vec3 ambient = m_AmbientColor.rgb * mix(0.55, 1.0, N.y * 0.5 + 0.5);
    float spec = pow(max(dot(N, normalize(L + V)), 0.0), 18.0) * 0.035;

    float ao = vColor.a * mix(0.65, 1.0, cracks);
    vec3 light = ambient * ao + m_SunColor.rgb * (diffuse * mix(0.55, 1.0, vColor.a) + spec);
    gl_FragColor = vec4(albedo * light, 1.0);
}
