package com.example.levi.bitmap12

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView

class ImageAdapter(val context: Context) : BaseAdapter() {

    private val mImageLoader: ImageLoader = ImageLoader.build(context)

    companion object {
        var isIdle = false
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder: ViewHolder?
        val view: View?
        if (convertView == null) {
            view = LayoutInflater.from(parent!!.context).inflate(R.layout.item_grid, parent, false)
            holder = ViewHolder(view.findViewById(R.id.image))
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }
        val imageView = holder.imageView
        if (isIdle) {
            imageView.tag = getItem(position)
            mImageLoader.bindBitmap(getItem(position) as String, imageView)
        }
        return view!!
    }

    override fun getItem(position: Int): Any = mUrlList[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = mUrlList.size

    class ViewHolder(val imageView: ImageView)

    private val mUrlList = arrayListOf(
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg",
            "http://p1.4499.cn/pic/UploadPic/2013-6/24/2013062417364170657.jpg"
    )
}