package com.nova.agent.ui.brand

import com.nova.agent.ui.theme.NovaApertureTokens

/**
 * NOVA Pulse Aperture kimliğinin renderer-bağımsız sözleşmesi.
 *
 * Geometri, yollar ve zamanlama artık burada SABİT DEĞİL: ortak kaynaktan
 * (design/nova-tokens.json → [NovaApertureTokens]) gelir; web'deki
 * `ui/NovaMark.jsx` aynı sayıları okur. Bu nesne yalnız tarihsel adları
 * koruyan ince bir katmandır.
 *
 * Renkler bilinçli olarak burada yok: ikon aktif aksanın
 * `NovaAccent.aperture` setini kullanır, böylece her temada tema rengini alır.
 */
object NovaApertureSpec {
    const val componentSizeDp = NovaApertureTokens.componentSizeDp
    const val haloDiameterDp = NovaApertureTokens.haloDiameterDp
    const val haloDotCount = NovaApertureTokens.haloDotCount
    const val markRestSizeDp = NovaApertureTokens.markRestSizeDp
    const val markPeakSizeDp = NovaApertureTokens.markPeakSizeDp
    const val breathDurationMillis = NovaApertureTokens.breathDurationMillis
    const val corePulseDurationMillis = NovaApertureTokens.corePulseDurationMillis
    const val orbitDurationMillis = NovaApertureTokens.orbitDurationMillis
    const val shimmerDurationMillis = NovaApertureTokens.shimmerDurationMillis
    const val microTiltDegrees = NovaApertureTokens.microTiltDegrees

    const val bodyPathData = NovaApertureTokens.bodyPathData
    const val foldPathData = NovaApertureTokens.foldPathData
}
