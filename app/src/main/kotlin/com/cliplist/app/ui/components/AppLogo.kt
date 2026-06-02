package com.cliplist.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.cliplist.app.R

/**
 * The app's launcher icon, rendered in-app. `painterResource` cannot load an adaptive icon
 * (mipmap/ic_launcher is an `<adaptive-icon>`, not a vector or raster) and throws
 * IllegalArgumentException at runtime, so we rasterize the drawable to a bitmap once and show that.
 * Clip with CircleShape at the call site for a circular logo.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val image = remember {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
        val px = 256
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    }
    Image(bitmap = image, contentDescription = contentDescription, modifier = modifier)
}
