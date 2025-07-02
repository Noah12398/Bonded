package com.example.bonded.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.bonded.ui.theme.DarkBackground
import com.example.bonded.ui.theme.DarkButtonText
import com.example.bonded.ui.theme.DarkCard
import com.example.bonded.ui.theme.DarkHint
import com.example.bonded.ui.theme.DarkPrimary
import com.example.bonded.ui.theme.DarkText
import com.example.bonded.ui.theme.LightBackground
import com.example.bonded.ui.theme.LightButtonText
import com.example.bonded.ui.theme.LightCard
import com.example.bonded.ui.theme.LightHint
import com.example.bonded.ui.theme.LightPrimary
import com.example.bonded.ui.theme.LightText

data class CustomColors(
    val background: Color,
    val card: Color,
    val primary: Color,
    val text: Color,
    val hint: Color,
    val buttonText: Color
)

@Composable
fun appColors(): CustomColors {
    return if (isSystemInDarkTheme()) {
        CustomColors(
            background = DarkBackground,
            card = DarkCard,
            primary = DarkPrimary,
            text = DarkText,
            hint = DarkHint,
            buttonText = DarkButtonText
        )
    } else {
        CustomColors(
            background = LightBackground,
            card = LightCard,
            primary = LightPrimary,
            text = LightText,
            hint = LightHint,
            buttonText = LightButtonText
        )
    }
}
