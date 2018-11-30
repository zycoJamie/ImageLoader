package com.example.levi.bitmap12

import android.Manifest
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.widget.Toast
import com.jakewharton.disklrucache.DiskLruCache
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private val diskCache: DiskLruCache by lazy {
        val dir: File = File(externalCacheDir, "bitmap")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        DiskLruCache.open(dir, 1, 1, DISK_CACHE_SIZE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermission()
        iv_bitmap.setImageBitmap(decodeSampleBitmapFromResource(resources, R.mipmap.test, 100, 100))

        GlobalScope.launch {
            launch {
                cacheFind()
            }
            diskLruCache()
        }
    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
        }
    }

    @TargetApi(28)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 0) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
            }
        }
    }

    /**
     * bitmap高效加载
     */
    companion object {

        val DISK_CACHE_SIZE: Long = 1024 * 1024 * 50

        fun decodeSampleBitmapFromResource(res: Resources, resId: Int, reqWidth: Int, reqHeight: Int): Bitmap {
            val option: BitmapFactory.Options = BitmapFactory.Options()
            option.inJustDecodeBounds = true
            BitmapFactory.decodeResource(res, resId, option)
            option.inSampleSize = calculateInSampleSize(option, reqWidth, reqHeight)
            option.inJustDecodeBounds = false
            return BitmapFactory.decodeResource(res, resId, option)
        }

        private fun calculateInSampleSize(option: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val outWidth = option.outWidth
            val outHeight = option.outHeight
            var sampleSize: Int = 1
            if (outWidth > reqWidth || outHeight > reqHeight) {
                val halfWidth = outWidth / 2
                val halfHeight = outHeight / 2
                while (halfWidth / sampleSize >= reqWidth && halfHeight / sampleSize >= reqHeight) {
                    sampleSize *= 2
                }
            }
            return sampleSize
        }
    }

    //DiskLruCache的创建和缓存添加

    /**
     * 将图片url进行MD5编码
     */
    private fun hashKeyFormUrl(url: String): String {
        val messageDigest: MessageDigest = MessageDigest.getInstance("MD5")
        messageDigest.update(url.toByteArray())
        val cacheKey: String = bytesToHexString(messageDigest.digest())
        return cacheKey
    }

    /**
     * 字节数组转16进制字符串
     */
    private fun bytesToHexString(bytes: ByteArray): String {
        val stringBuffer = StringBuffer()
        for (i in 0 until bytes.size) {
            val hex: String = (0xFF and bytes[i].toInt()).toString(16)
            if (hex.length == 1) {
                stringBuffer.append('0')
            }
            stringBuffer.append(hex)
        }
        return stringBuffer.toString()
    }


    private fun diskLruCache() {
        val url = "http://www.wanandroid.com/resources/image/pc/logo.png"
        val key = hashKeyFormUrl(url)
        val editor: DiskLruCache.Editor = diskCache.edit(key)
        val os: OutputStream = editor.newOutputStream(0)
        if (downloadUrlToStream(url, os)) {
            editor.commit()
        } else {
            editor.abort()
        }
        diskCache.flush()
    }

    private fun downloadUrlToStream(urlString: String, os: OutputStream): Boolean {
        val urlConnection: HttpURLConnection
        val bos = BufferedOutputStream(os)
        val bis: BufferedInputStream

        val url = URL(urlString)
        urlConnection = url.openConnection() as HttpURLConnection
        bis = BufferedInputStream(urlConnection.inputStream)
        var b = 0

        try {
            while ({ b = bis.read();b }() != -1) {
                bos.write(b)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            urlConnection.disconnect()
            bos.close()
            bis.close()
        }
        runOnUiThread {
            Toast.makeText(this@MainActivity, "图片缓存完成", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun decodeSampleBitmapFromFileDescriptor(fd: FileDescriptor, reqWidth: Int, reqHeight: Int): Bitmap {
        val option: BitmapFactory.Options = BitmapFactory.Options()
        option.inJustDecodeBounds = true
        BitmapFactory.decodeFileDescriptor(fd, null, option)
        val sampleSize = calculateInSampleSize(option, reqWidth, reqHeight)
        option.inSampleSize = sampleSize
        option.inJustDecodeBounds = false
        return BitmapFactory.decodeFileDescriptor(fd, null, option)
    }

    /**
     * DisLruCache缓存获取
     */
    private fun cacheFind() {
        val bitmap: Bitmap
        val urlString = "http://www.wanandroid.com/resources/image/pc/logo.png"
        val key: String = hashKeyFormUrl(urlString)
        val snapShot: DiskLruCache.Snapshot = diskCache.get(key)
        if (snapShot != null) {
            val fis: FileInputStream = snapShot.getInputStream(0) as FileInputStream
            bitmap = decodeSampleBitmapFromFileDescriptor(fis.fd, 200, 200)
            iv_bitmap_2.setImageBitmap(bitmap)
        }
    }

}
