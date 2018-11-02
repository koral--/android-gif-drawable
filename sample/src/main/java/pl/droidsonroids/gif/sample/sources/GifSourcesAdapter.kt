package pl.droidsonroids.gif.sample.sources

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifDrawableBuilder
import pl.droidsonroids.gif.sample.R

internal class GifSourcesAdapter(context: Context) : RecyclerView.Adapter<GifSourceItemHolder>() {

	private val resolver = GifSourcesResolver(context)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifSourceItemHolder {
		val itemView = LayoutInflater.from(parent.context).inflate(R.layout.source_item, parent, false)
		return GifSourceItemHolder(itemView)
	}

	override fun onBindViewHolder(holder: GifSourceItemHolder, adapterPosition: Int) {
		val descriptions = holder.itemView.resources.getStringArray(R.array.sources)
		val index = adapterPosition % descriptions.size

		val existingOriginalDrawable = holder.gifImageViewOriginal.drawable as GifDrawable?
		val existingSampledDrawable = holder.gifImageViewSampled.drawable as GifDrawable?
		val builder = GifDrawableBuilder().with(existingOriginalDrawable)
		resolver.bindSource(index, builder)
		val fullSizeDrawable = builder.build()
		holder.gifImageViewOriginal.setImageDrawable(fullSizeDrawable)
		holder.gifImageViewOriginal.setOnClickListener {
			when {
				fullSizeDrawable.isPlaying -> fullSizeDrawable.stop()
				else -> fullSizeDrawable.start()
			}
		}

		builder.with(existingSampledDrawable).sampleSize(3)
		resolver.bindSource(index, builder)
		val subsampledDrawable = builder.build()
		val stringBuilder = SpannableStringBuilder(descriptions[index] + '\ufffc')
		stringBuilder.setSpan(ImageSpan(subsampledDrawable), stringBuilder.length - 1, stringBuilder.length, 0)
		holder.descriptionTextView.text = stringBuilder
		holder.gifImageViewSampled.setImageDrawable(subsampledDrawable)
		subsampledDrawable.callback = holder.multiCallback
		holder.multiCallback.addView(holder.gifImageViewSampled)
		holder.multiCallback.addView(holder.descriptionTextView)
	}

	override fun getItemCount() = Int.MAX_VALUE

}
