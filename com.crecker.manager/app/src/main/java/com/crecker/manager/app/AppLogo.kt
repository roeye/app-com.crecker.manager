package com.crecker.manager.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crecker.manager.R

/**
 * אייקון האפליקציה לשימוש בתוך ה-UI.
 * painterResource לא תומך ב-adaptive-icon, לכן מרכיבים את שתי השכבות (רקע + חזית) ידנית.
 */
@Composable
fun AppLogo(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f))) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
        // החזית כוללת שוליים של adaptive icon (האריחים תופסים ~37%) – מגדילים כדי שימלאו את הלוגו
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.6f))
    }
}

/** כותרת עם הלוגו לפני הטקסט – ל-TopAppBar. */
@Composable
fun LogoTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppLogo()
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}
