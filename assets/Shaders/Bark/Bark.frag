#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_NoiseMap;
uniform vec3 g_CameraPosition;
uniform vec3 m_SunDirection;   // direction des rayons (comme LightingManager)
uniform vec4 m_SunColor;
uniform vec4 m_AmbientColor;
uniform float m_BumpStrength;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec2 vUV;
varying vec4 vColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 tex = texture(m_NoiseMap, vUV).rgb;                 // r sillons, g ton, b marques
    vec3 fine = texture(m_NoiseMap, vUV * vec2(2.3, 3.1)).rgb;

    // Relief des sillons (dérivées d'écran, sans tangentes).
    float height = tex.r * 0.75 + fine.r * 0.25;
    vec3 dpdx = dFdx(vWorldPos), dpdy = dFdy(vWorldPos);
    vec3 r1 = cross(dpdy, N), r2 = cross(N, dpdx);
    float det = dot(dpdx, r1);
    vec3 grad = sign(det) * (dFdx(height) * r1 + dFdy(height) * r2);
    N = normalize(abs(det) * N - m_BumpStrength * 0.05 * grad);

    // Les creux des sillons sont plus sombres ; l'écorce blanche (bouleau) porte des marques noires.
    float grooves = mix(0.45, 1.0, smoothstep(0.25, 0.7, tex.r));
    float luminance = dot(vColor.rgb, vec3(0.333));
    float birch = smoothstep(0.25, 0.40, luminance);
    float marks = mix(1.0, mix(1.0, 0.12, tex.b), birch);
    grooves = mix(grooves, mix(0.8, 1.0, grooves), birch);   // sillons beaucoup plus discrets sur le bouleau
    vec3 albedo = vColor.rgb * (0.75 + 0.5 * tex.g) * grooves * marks;

    vec3 L = normalize(-m_SunDirection);
    vec3 V = normalize(g_CameraPosition - vWorldPos);
    float diffuse = max(dot(N, L), 0.0);
    vec3 ambient = m_AmbientColor.rgb * mix(0.55, 1.0, N.y * 0.5 + 0.5);
    float ao = vColor.a * mix(0.7, 1.0, grooves);
    vec3 light = ambient * ao + m_SunColor.rgb * diffuse * mix(0.5, 1.0, vColor.a);
    gl_FragColor = vec4(albedo * light, 1.0);
}
