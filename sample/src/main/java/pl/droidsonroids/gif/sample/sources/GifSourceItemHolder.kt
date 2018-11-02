package pl.droidsonroids.gif.sample.sources

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pl.droidsonroids.gif.GifImageView
import pl.droidsonroids.gif.MultiCallback
import pl.droidsonroids.gif.sample.R

internal class GifSourceItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
	val gifImageViewOriginal: GifImageView = itemView.findViewById(R.id.image_original)
	val gifImageViewSampled: GifImageView = itemView.findViewById(R.id.image_subsampled)
	val descriptionTextView: TextView = itemView.findViewById(R.id.desc_tv)
	val multiCallback = MultiCallback(true)
}
