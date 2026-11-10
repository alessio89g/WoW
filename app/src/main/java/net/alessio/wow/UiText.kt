package net.alessio.wow

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalWoWResources = staticCompositionLocalOf<Resources> {
    error("WoW resources are unavailable")
}

@Composable
internal fun label(@StringRes id: Int, vararg args: Any): String =
    UiText(id, *args).resolve(LocalWoWResources.current)

internal class UiText(@StringRes val id: Int, vararg val args: Any) {
    fun resolve(resources: Resources): String =
        if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
}

internal class UiFailure(@StringRes id: Int, vararg args: Any) : IllegalArgumentException() {
    val text = UiText(id, *args)
}

internal fun Throwable?.toUiText(@StringRes fallback: Int): UiText =
    (this as? UiFailure)?.text ?: UiText(fallback)

internal fun requireText(condition: Boolean, @StringRes id: Int) {
    if (!condition) throw UiFailure(id)
}
