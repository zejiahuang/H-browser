package com.h.browser.ui;

/**
 * 四种高级材质（参考 Mafari 的材质分层，参数独立标定）：
 * 柔光玻璃（轻折射 + 边缘光）、磨砂（只模糊、无折射）、
 * 液态玻璃（强折射 + 色散 + 高饱和）、纯色（不采样背景）。
 */
public final class GlassPreset {

    public static final int SOFT = 0;
    public static final int FROST = 1;
    public static final int LIQUID = 2;
    public static final int SOLID = 3;

    public final int style;
    public final String label;
    /** 是否走 RuntimeShader 折射。 */
    public final boolean shader;
    /** 是否需要窗口快照（纯色不需要）。 */
    public final boolean capture;
    public final float edgeDp;
    public final float shiftDp;
    public final float saturation;
    public final float veil;
    /** 色散强度（0 关闭）。 */
    public final float chromatic;
    /** 边缘高光强度。 */
    public final float gloss;
    /** 模糊降采样除数：半径 / divisor。 */
    public final int blurDivisor;
    public final int tintDay;
    public final int tintNight;
    public final int menuDay;
    public final int menuNight;
    public final int shadowAlpha;
    public final float shadowSpreadDp;
    public final float shadowSigmaDp;
    public final int fillDay;
    public final int fillNight;

    private GlassPreset(int style, String label, boolean shader, boolean capture,
                        float edgeDp, float shiftDp, float saturation, float veil,
                        float chromatic, float gloss, int blurDivisor,
                        int tintDay, int tintNight, int menuDay, int menuNight,
                        int shadowAlpha, float shadowSpreadDp, float shadowSigmaDp,
                        int fillDay, int fillNight) {
        this.style = style;
        this.label = label;
        this.shader = shader;
        this.capture = capture;
        this.edgeDp = edgeDp;
        this.shiftDp = shiftDp;
        this.saturation = saturation;
        this.veil = veil;
        this.chromatic = chromatic;
        this.gloss = gloss;
        this.blurDivisor = blurDivisor;
        this.tintDay = tintDay;
        this.tintNight = tintNight;
        this.menuDay = menuDay;
        this.menuNight = menuNight;
        this.shadowAlpha = shadowAlpha;
        this.shadowSpreadDp = shadowSpreadDp;
        this.shadowSigmaDp = shadowSigmaDp;
        this.fillDay = fillDay;
        this.fillNight = fillNight;
    }

    public static GlassPreset of(int style) {
        switch (style) {
            case FROST:
                return FROST_PRESET;
            case LIQUID:
                return LIQUID_PRESET;
            case SOLID:
                return SOLID_PRESET;
            case SOFT:
            default:
                return SOFT_PRESET;
        }
    }

    public static String labelOf(int style) {
        return of(style).label;
    }

    // 日间 tint 按 R93 彩色页实测约 0.7 白雾，夜间约 0.8 深色雾
    private static final GlassPreset SOFT_PRESET = new GlassPreset(
            SOFT, "柔光玻璃", true, true,
            26f, 6.5f, 1.14f, 0.03f,
            0f, 0.8f, 1,
            150, 145, 182, 208,
            104, 38f, 18f,
            0xFFF2F7F7, 0xFF1B2223);

    private static final GlassPreset FROST_PRESET = new GlassPreset(
            FROST, "磨砂", false, true,
            20f, 0f, 1.04f, 0.12f,
            0f, 0f, 1,
            150, 146, 186, 208,
            96, 36f, 16f,
            0xFFF2F7F7, 0xFF1B2223);

    private static final GlassPreset LIQUID_PRESET = new GlassPreset(
            LIQUID, "液态玻璃", true, true,
            34f, 13f, 1.34f, 0.03f,
            1.6f, 1.2f, 1,
            140, 135, 176, 200,
            112, 42f, 20f,
            0xFFF2F7F7, 0xFF1B2223);

    private static final GlassPreset SOLID_PRESET = new GlassPreset(
            SOLID, "纯色", false, false,
            0f, 0f, 1f, 0f,
            0f, 0.4f, 0,
            0, 0, 0, 0,
            58, 34f, 13f,
            0xFFEFEFEF, 0xFF1B2223);
}
