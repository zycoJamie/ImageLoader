package com.example.levi.bitmap12

import android.content.res.Resources

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.FileDescriptor

class ImageResizer {

    val TAG: String = ImageResizer::class.java.simpleName

    fun decodeSampleBitmapFromResource(res: Resources, resId: Int, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options: BitmapFactory.Options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeResource(res, resId, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(res, resId, options)
    }

    fun decodeSampleBitmapFromFileDescriptor(fd: FileDescriptor, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options: BitmapFactory.Options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFileDescriptor(fd, null, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFileDescriptor(fd, null, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth == 0 || reqHeight == 0) {
            return 1
        }
        val outWidth: Int = options.outWidth / 2
        val outHeight: Int = options.outHeight / 2
        var inSampleSize = 1
        if (outWidth > reqWidth || outHeight > reqHeight) {
            Log.i(TAG, "(outWidth / inSampleSize) $outWidth/$inSampleSize")
            Log.i(TAG, "(outHeight / inSampleSize) $outHeight/$inSampleSize")
            while ((outWidth / inSampleSize) >= reqWidth && (outHeight / inSampleSize) >= reqHeight) {
                Log.i(TAG, "inSampleSize:$inSampleSize")
                inSampleSize *= 2
            }
        }
        Log.i(TAG, "inSampleSize: $inSampleSize")
        return inSampleSize
    }

}