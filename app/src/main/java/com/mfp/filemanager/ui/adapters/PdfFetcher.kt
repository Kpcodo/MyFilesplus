package com.mfp.filemanager.ui.adapters

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        return withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null
            
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                
                if (renderer.pageCount > 0) {
                    page = renderer.openPage(0)
                    
                    val width = page.width
                    val height = page.height
                    
                    // Limit max size to avoid OOM, e.g., max 512px
                    val maxSize = 512f
                    val scale = if (width > height) maxSize / width else maxSize / height
                    val targetWidth = (width * scale).toInt()
                    val targetHeight = (height * scale).toInt()
                    
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    // White background for PDF
                    bitmap.eraseColor(Color.WHITE)
                    
                    // Render
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    DrawableResult(
                        drawable = BitmapDrawable(options.context.resources, bitmap),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                } else {
                     throw Exception("No pages in PDF")
                }
            } catch (e: Exception) {
                 e.printStackTrace()
                 throw e
            } finally {
                 try { page?.close() } catch(_: Exception){}
                 try { renderer?.close() } catch(_: Exception){}
                 try { pfd?.close() } catch(_: Exception){}
            }
        }
    }
    
    class Factory : Fetcher.Factory<File> {
         override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
             if (data.extension.equals("pdf", ignoreCase = true)) {
                 return PdfFetcher(data, options)
             }
             return null
         }
    }
}
