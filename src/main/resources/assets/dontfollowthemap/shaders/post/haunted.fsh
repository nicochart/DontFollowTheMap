#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// Herobrine is near: the picture turns into an old, worn film that glitches now and then.

uniform sampler2D InSampler;
// one pixel: how strongly Herobrine is felt right now (client/HauntedScreen), fading in and out
uniform sampler2D StrengthSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform HauntedConfig {
    float Strength;
};

layout(location = 0) out vec4 fragColor;

// hashes without sine: steady on every GPU, whatever their input
float hash(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    // one picture per game tick, like a film running at 20 frames per second
    float frame = mod(floor(GameTime * 24000.0), 997.0);
    vec2 uv = texCoord;

    // the film shakes a little
    uv.y += (hash(frame + 0.5) - 0.5) * 0.003;

    // now and then, for a few frames, bands of the picture slide sideways and the colors drift apart
    float burst = floor(frame / 3.0);
    float glitch = step(0.86, hash(burst + 101.0));
    float band = floor(uv.y * 18.0 + hash(burst + 7.0) * 7.0);
    uv.x += glitch * step(0.55, hash(vec2(band, burst))) * (hash(vec2(burst, band + 13.0)) - 0.5) * 0.08;
    float split = 0.0015 + glitch * 0.008;

    vec3 picture = texture(InSampler, texCoord).rgb;
    vec3 color = vec3(
        texture(InSampler, uv + vec2(split, 0.0)).r,
        texture(InSampler, uv).g,
        texture(InSampler, uv - vec2(split, 0.0)).b
    );

    // faded colors, warm like old film
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, luma * vec3(1.08, 0.96, 0.78), 0.7);

    // the light flickers
    color *= 0.88 + 0.12 * hash(frame + 211.0);

    // grain
    color += (hash(floor(texCoord * InSize) + vec2(frame * 13.0, frame * 7.0)) - 0.5) * 0.14;

    // scratches running down the film
    for (int i = 0; i < 3; i++) {
        float seed = floor(frame / 5.0) * 3.0 + float(i);
        float visible = step(0.55, hash(seed + 401.0));
        float gap = abs(texCoord.x - hash(seed + 601.0)) * InSize.x;
        color = mix(color, vec3(0.92, 0.88, 0.80), visible * (1.0 - smoothstep(0.0, 1.5, gap)) * 0.45);
    }

    // specks of dust: small round blots, the same size whatever the window's size
    vec2 cellPos = texCoord * vec2(160.0, 90.0);
    vec2 cell = floor(cellPos);
    float radius = 0.15 + 0.25 * hash(cell + vec2(frame, frame * 5.0));
    float speck = step(0.9988, hash(cell + vec2(frame * 3.0, frame))) * (1.0 - smoothstep(radius * 0.6, radius, length(fract(cellPos) - 0.5)));
    color = mix(color, vec3(0.05), speck * 0.8);

    // scanlines
    color *= 0.95 + 0.05 * sin(texCoord.y * InSize.y * 3.14159);

    // dark edges
    color *= mix(0.35, 1.0, smoothstep(0.8, 0.2, length(texCoord - 0.5)));

    float strength = Strength * texture(StrengthSampler, vec2(0.5)).r;
    fragColor = vec4(mix(picture, clamp(color, 0.0, 1.0), strength), 1.0);
}
