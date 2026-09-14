package io.github.immaghzbad.aetherst.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun CountryFlag(
    countryCode: String,
    modifier: Modifier = Modifier,
    fallback: String = "🌐",
    fontSize: TextUnit = 16.sp
) {
    if (countryCode.length != 2) {
        Text(text = fallback, fontSize = fontSize)
        return
    }
    val flagPath = "flags/${countryCode.lowercase()}.svg"
    val exists = remember(flagPath) {
        runCatching {
            Thread.currentThread().contextClassLoader?.getResource(flagPath) != null
        }.getOrDefault(false)
    }
    if (exists) {
        Image(
            painter = painterResource(flagPath),
            contentDescription = countryCode,
            modifier = modifier
        )
    } else {
        Text(text = fallback, fontSize = fontSize)
    }
}