package com.veplayer.app.ui.brand

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.veplayer.app.brand.BrandBus
import java.io.File

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    height: Dp,
) {
    val brand by BrandBus.state.collectAsState()
    val path = brand.logoPath
    if (path.isBlank()) return
    val bitmap =
        remember(path) {
            val f = File(path)
            if (!f.isFile) null else BitmapFactory.decodeFile(f.absolutePath)
        }
    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = brand.displayName.ifBlank { "Logo" },
            modifier = modifier.height(height),
            contentScale = ContentScale.Fit,
        )
    }
}
