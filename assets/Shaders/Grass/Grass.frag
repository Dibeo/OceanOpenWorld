#import "Common/ShaderLib/GLSLCompat.glsllib"

// Shades grass using the terrain normal, two aligned atlas textures, per-clump tint, and the
// shared sun. Material uniforms are declared in MatDefs/Grass/Grass.j3md.
uniform sampler2D m_ColorMap;
uniform sampler2D m_FlowerMap;
uniform vec3 g_CameraPosition;
uniform vec3 m_SunDirection;   // light-ray direction from LightingManager; the sun is opposite
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

// Linear-space blade gradient: dark, saturated base to a lighter yellow tip.
const vec3 BASE_COLOR = vec3(0.022, 0.068, 0.010);
const vec3 TIP_COLOR  = vec3(0.165, 0.340, 0.048);

// Flower palette in linear albedo: white, yellow, pink, purple, red, and blue.
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
    // Apply the flower head only to clumps marked as flowering (vFlower.y = 1).
    vec2 head = texture(m_FlowerMap, vUV).rg * step(0.5, vFlower.y);
    texel.a = max(texel.a, head.x);

    // Mipmap alpha becomes thinner; compensate so distant grass does not disappear.
    vec2 dx = dFdx(vUV * TEXTURE_SIZE), dy = dFdy(vUV * TEXTURE_SIZE);
    float mip = max(0.0, 0.5 * log2(max(max(dot(dx, dx), dot(dy, dy)), 1.0)));
    float alpha = texel.a * (1.0 + 0.45 * mip);
    if (alpha < 0.5) discard;

    vec3 N = normalize(vNormal);
    vec3 L = normalize(-m_SunDirection);                    // toward the sun
    vec3 V = normalize(g_CameraPosition - vWorldPos);       // toward the camera
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
    // Wind-leaning blades create a breathing effect as bands of light and shadow move across the field.
    albedo *= 1.0 + 0.40 * clamp(vWave, -0.25, 0.6) * (1.0 - isFlower);

    // Occlusion: the base of each clump receives little light.
    float occlusion = mix(0.22, 1.0, smoothstep(0.0, 0.65, t));
    occlusion = mix(occlusion, 1.0, isFlower);   // flower heads at the end of stalks stay lit
    // Wrapped diffuse lighting uses the terrain normal because a blade is not an opaque plane.
    float diffuse = max(0.0, (dot(N, L) + 0.35) / 1.35);
    // Translucency lets light pass through blades facing the sun, especially near their tips.
    float transmission = pow(clamp(dot(-V, L), 0.0, 1.0), 3.0) * t;
    vec3 translucency = mix(vec3(0.95, 1.0, 0.30), vec3(1.0), isFlower) * transmission * mix(0.65, 1.1, isFlower);
    // Add a subtle sheen.
    float sheen = pow(max(dot(N, normalize(L + V)), 0.0), 24.0) * 0.06 * t;

    vec3 light = m_AmbientColor.rgb * occlusion
               + m_SunColor.rgb * (diffuse * mix(0.45, 1.0, occlusion) + translucency + sheen);
    gl_FragColor = vec4(albedo * light, 1.0);
}
