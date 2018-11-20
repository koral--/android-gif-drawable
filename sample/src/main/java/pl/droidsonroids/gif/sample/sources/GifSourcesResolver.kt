package pl.droidsonroids.gif.sample.sources

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import pl.droidsonroids.gif.GifDrawableBuilder
import pl.droidsonroids.gif.sample.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

internal class GifSourcesResolver(context: Context) {
	private val resources = context.resources
	private val assetManager = resources.assets
	private val contentResolver = context.contentResolver
	private val fileForUri = context.getFileFromAssets("Animated-Flag-Uruguay.gif")
	private val file = context.getFileFromAssets("Animated-Flag-Virgin_Islands.gif")
	private val filePath = context.getFileFromAssets("Animated-Flag-Estonia.gif").path
	private val byteArray = assetManager.getBytesFromAssets("Animated-Flag-France.gif")
	private val byteBuffer: ByteBuffer

	init {
		val gifBytes = assetManager.getBytesFromAssets("Animated-Flag-Georgia.gif")
		byteBuffer = ByteBuffer.allocateDirect(gifBytes.size)
		byteBuffer.put(gifBytes)
	}

	fun bindSource(position: Int, builder: GifDrawableBuilder) {
		when (position) {
			0 //asset
			-> builder.from(assetManager, "Animated-Flag-Finland.gif")
			1 //resource
			-> builder.from(resources, R.drawable.anim_flag_england)
			2 //byte[]
			-> builder.from(byteArray)
			3 //FileDescriptor
			-> builder.from(assetManager.openFd("Animated-Flag-Greece.gif"))
			4 //file path
			-> builder.from(filePath)
			5 //File
			-> builder.from(file)
			6 //AssetFileDescriptor
			-> builder.from(resources.openRawResourceFd(R.raw.anim_flag_hungary))
			7 //ByteBuffer
			-> builder.from(byteBuffer)
			8 //Uri
			-> builder.from(contentResolver, Uri.parse("file:///" + fileForUri.absolutePath))
			9 //InputStream
			-> builder.from(assetManager.open("Animated-Flag-Delaware.gif", AssetManager.ACCESS_RANDOM))
			else
			-> throw IndexOutOfBoundsException("Invalid source index")
		}
	}
}

private fun Context.getFileFromAssets(filename: String): File {
	val file = File(cacheDir, filename)
	val assetFileDescriptor = resources.assets.openFd(filename)
	val buf = ByteArray(assetFileDescriptor.declaredLength.toInt())
	assetFileDescriptor.createInputStream().use { inputStream ->
		val bytesRead = inputStream.read(buf)
		if (bytesRead != buf.size) {
			throw IOException("Asset read failed")
		}
		FileOutputStream(file).use { outputStream ->
			outputStream.write(buf, 0, bytesRead)
		}
	}
	return file
}

private fun AssetManager.getBytesFromAssets(filename: String): ByteArray {
	val assetFileDescriptor = openFd(filename)
	assetFileDescriptor.createInputStream().use {
		val buf = ByteArray(assetFileDescriptor.declaredLength.toInt())
		if (it.read(buf) != buf.size) {
			throw IOException("Incorrect asset length")
		}
		return buf
	}
}
