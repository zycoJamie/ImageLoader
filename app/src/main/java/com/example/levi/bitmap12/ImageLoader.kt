package com.example.levi.bitmap12

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import android.widget.Toast
import com.jakewharton.disklrucache.DiskLruCache
import java.io.*
import java.lang.RuntimeException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ImageLoader private constructor(context: Context) {
    private val TAG = ImageLoader::class.java.simpleName
    private val mContext: Context = context.applicationContext
    private val mMemoryCache: LruCache<String, Bitmap>
    private var mDiskLruCache: DiskLruCache? = null
    private var mIsDiskLruCacheCreated = false
    private val mImageResizer: ImageResizer = ImageResizer()


    companion object {
        fun build(context: Context): ImageLoader = ImageLoader(context)
        const val DISK_CACHE_SIZE = 1024 * 1024 * 50
        const val MESSAGE_POST_RESULT = 1
        val CPU_COUNT = Runtime.getRuntime().availableProcessors()
        val CORE_POOL_SIZE = CPU_COUNT + 1
        val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
        val KEEP_ALIVE = 10L

        private class LoaderResult(val imageView: ImageView, val url: String, val bitmap: Bitmap)

        private val sThreadFactory = object : ThreadFactory {
            private val mCount: AtomicInteger = AtomicInteger(1)
            override fun newThread(r: Runnable?): Thread {
                return Thread(r, "ImageLoader#" + mCount.getAndIncrement())
            }
        }
        val THREAD_POOL_EXECUTOR = ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
                TimeUnit.SECONDS, LinkedBlockingDeque(), sThreadFactory)
    }


    /**
     * 从异步线程中回到主线程
     * 给对应的imageView绑定bitmap
     * 但防止因为复用itemView，出现图片加载错位
     * 绑定imageView时，判断加载图片的url与imageView.tag中存储的url是否一致
     */
    private val mMainHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message?) {
            val result: LoaderResult = msg!!.obj as LoaderResult
            val imageView = result.imageView
            val url = imageView.getTag(R.id.image_uri)
            if (url == result.url) {
                Log.i(TAG, "set image in main handler")
                imageView.setImageBitmap(result.bitmap)
            } else {
                Log.i(TAG, "set imageView bitmap,but url has changed.ignore!")
            }
        }
    }


    /**
     * ImageLoader创建时，初始化LruCache，DiskLruCache
     */
    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        mMemoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String?, value: Bitmap?): Int {
                return value!!.rowBytes * value.height
            }
        }
        val diskCacheDir: File = getDiskCacheDir(mContext, "bitmap")
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs()
        }
        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE.toLong())
            mIsDiskLruCacheCreated = true
        }
    }

    /**
     * 加载bitmap
     */
    fun loadBitmap(url: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        var bitmap: Bitmap? = loadBitmapFromMemCache(url)
        if (bitmap != null) {
            Log.i(TAG, "load bitmap from memory cache url:$url")
            return bitmap
        }
        bitmap = loadBitmapFromDiskCache(url, reqWidth, reqHeight)
        if (bitmap != null) {
            Log.i(TAG, "load bitmap from disk cache url:$url")
            return bitmap
        }
        bitmap = loadBitmapFromHttp(url, reqWidth, reqHeight)
        if (bitmap != null) {
            Log.i(TAG, "load bitmap from http url:$url")
            return bitmap
        }
        if (!mIsDiskLruCacheCreated) {
            Log.i(TAG, "go directly memory")
            bitmap = downloadBitmapFromUrl(url)
        }
        return bitmap
    }

    /**
     * 将bitmap与imageView进行绑定
     */
    fun bindBitmap(url: String, imageView: ImageView) {
        bindBitmap(url, imageView, 0, 0)
    }

    fun bindBitmap(url: String, imageView: ImageView, reqWidth: Int, reqHeight: Int) {
        imageView.setTag(R.id.image_uri, url)
        var bitmap = loadBitmapFromMemCache(url)
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            return
        }
        val loadBitmapTask = Runnable {
            bitmap = loadBitmap(url, reqWidth, reqHeight)
            if (bitmap != null) {
                val loaderResult = LoaderResult(imageView, url, bitmap!!)
                mMainHandler.obtainMessage(MESSAGE_POST_RESULT, loaderResult).sendToTarget()
            }
        }

        THREAD_POOL_EXECUTOR.execute(loadBitmapTask)
    }

    /**
     * 得到磁盘缓存目录
     */
    private fun getDiskCacheDir(context: Context, uniqueName: String): File {
        val externalStorageAvailable = Environment.getExternalStorageState() == (Environment.MEDIA_MOUNTED)
        val cachePath: String
        if (externalStorageAvailable) {
            cachePath = context.externalCacheDir!!.path
        } else {
            cachePath = context.cacheDir.path
        }
        val dir = File(cachePath + File.separator + uniqueName)
        Log.i(TAG, "文件路径: " + dir.path)
        return dir
    }

    /**
     * 可用磁盘空间
     */
    @TargetApi(18)
    private fun getUsableSpace(path: File): Long {
        val statFs: StatFs = StatFs(path.path)
        val size: Long = statFs.blockSizeLong * statFs.availableBlocksLong
        Log.i(TAG, "可用磁盘空间:" + size.toString())
        return size
    }

    /**
     * 将Bitmap添加至内存缓存中
     */
    private fun addBitmap2MemoryCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap)
        }
    }

    private fun getBitmapFromMemCache(key: String): Bitmap? {
        return mMemoryCache.get(key)
    }

    /**
     * 从网络读取图片到磁盘
     */
    private fun loadBitmapFromHttp(url: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw RuntimeException("can not visit network from UI Thread")
        }
        if (mDiskLruCache == null) {
            return null
        }

        val key = hashKeyFormUrl(url)
        val editor: DiskLruCache.Editor? = mDiskLruCache!!.edit(key)
        if (editor != null) {
            val os: OutputStream = editor.newOutputStream(0)
            if (downloadUrlToStream(url, os)) {
                editor.commit()
            } else {
                editor.abort()
            }
            mDiskLruCache!!.flush()
        }

        return loadBitmapFromDiskCache(url, reqWidth, reqHeight)
    }

    /**
     * 读取URL内容到流中
     */
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
        return true
    }

    /**
     * 从磁盘缓存中加载bitmap到内存中
     */
    private fun loadBitmapFromDiskCache(url: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw RuntimeException("from UI Thread load bitmap,it's not recommended")
        }
        if (mDiskLruCache == null) {
            return null
        }
        var bitmap: Bitmap? = null
        val key = hashKeyFormUrl(url)
        val snapshot: DiskLruCache.Snapshot? = mDiskLruCache!!.get(key)
        if (snapshot != null) {
            val fis: FileInputStream = snapshot.getInputStream(0) as FileInputStream
            val fd = fis.fd
            bitmap = mImageResizer.decodeSampleBitmapFromFileDescriptor(fd, reqWidth, reqHeight)
            if (bitmap != null) {
                addBitmap2MemoryCache(key, bitmap)
            }
        }
        return bitmap
    }

    /**
     * 从内存缓存中取得bitmap
     */
    private fun loadBitmapFromMemCache(url: String): Bitmap? {
        val key = hashKeyFormUrl(url)
        return getBitmapFromMemCache(key)
    }

    /**
     * 如果没法缓存到磁盘，就直接从输入流中解析出bitmap
     */
    private fun downloadBitmapFromUrl(urlString: String): Bitmap? {
        var connection: HttpURLConnection? = null
        var bis: BufferedInputStream? = null
        val url = URL(urlString)
        val bitmap: Bitmap?
        try {
            connection = url.openConnection() as HttpURLConnection
            bis = BufferedInputStream(connection.inputStream)
            bitmap = BitmapFactory.decodeStream(bis)
        } finally {
            connection?.disconnect()
            bis?.close()
        }
        return bitmap
    }

    /**
     * 将图片url进行MD5编码
     */
    private fun hashKeyFormUrl(url: String): String {
        val messageDigest: MessageDigest = MessageDigest.getInstance("MD5")
        messageDigest.update(url.toByteArray())
        return bytesToHexString(messageDigest.digest())
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

}