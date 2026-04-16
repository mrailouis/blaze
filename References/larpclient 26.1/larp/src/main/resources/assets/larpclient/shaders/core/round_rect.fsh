#version 150

layout(std140) uniform u {
    vec4 u_Rect;
    vec4 u_Radii;
    vec4 u_Color;
    vec4 u_Color2;
    vec4 u_ColorShadow;
    vec2 u_GradientDir;
    float u_EdgeSoftness;
    float u_ShadowSoftness;
    float u_BorderWidth;
};

#define u_rectCenter u_Rect.xy
#define u_rectSize u_Rect.zw

in vec2 f_Position;
out vec4 fragColor;

float roundedBoxSDF(vec2 centerPosition, vec2 size, vec4 radius) {
    radius.xy = (centerPosition.x > 0.0) ? radius.xy : radius.zw;
    radius.x = (centerPosition.y > 0.0) ? radius.x : radius.y;
    vec2 q = abs(centerPosition) - size + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
}

void main() {
    vec2 halfSize = u_rectSize / 2.0;
    float dist = roundedBoxSDF(f_Position - u_rectCenter, halfSize, u_Radii);
    float outerAlpha = 1.0 - smoothstep(0.0, u_EdgeSoftness, dist);

    if (u_BorderWidth > 0.0) {
        float innerDist = roundedBoxSDF(
            f_Position - u_rectCenter,
            halfSize - u_BorderWidth,
            max(u_Radii - u_BorderWidth, vec4(0.0))
        );
        float innerAlpha = 1.0 - smoothstep(0.0, u_EdgeSoftness, innerDist);
        fragColor = vec4(u_Color.rgb, u_Color.a * (outerAlpha - innerAlpha));
    } else {
        vec2 uv = (f_Position - u_rectCenter) / u_rectSize;
        float gradientStrength = clamp(dot(uv, u_GradientDir) + 0.5, 0.0, 1.0);
        vec4 gradientColor = mix(u_Color, u_Color2, gradientStrength);
        float shadowAlpha = 1.0 - smoothstep(-u_ShadowSoftness, u_ShadowSoftness, dist);
        vec4 resolvedShadow = mix(
            vec4(0.0),
            vec4(u_ColorShadow.rgb, u_ColorShadow.a * shadowAlpha),
            shadowAlpha
        );

        fragColor = mix(resolvedShadow, gradientColor, outerAlpha);
    }
}
