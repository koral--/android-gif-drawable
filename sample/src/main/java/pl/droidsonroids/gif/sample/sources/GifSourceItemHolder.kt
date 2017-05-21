package pl.droidsonroids.gif.sample.sources

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.TextView
import pl.droidsonroids.gif.GifImageView
import pl.droidsonroids.gif.MultiCallback

internal class GifSourceItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
	val gifImageViewOriginal = itemView.findViewById(R.id.image_original) as GifImageView
	val gifImageViewSampled = itemView.findViewById(R.id.image_subsampled) as GifImageView
	val descriptionTextView = itemView.findViewById(R.id.desc_tv) as TextView
	val multiCallback = MultiCallback(true)
}
