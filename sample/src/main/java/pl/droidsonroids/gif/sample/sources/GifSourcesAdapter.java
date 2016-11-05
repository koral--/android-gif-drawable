package pl.droidsonroids.gif.sample.sources;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifDrawableBuilder;
import pl.droidsonroids.gif.sample.R;

class GifSourcesAdapter extends RecyclerView.Adapter<GifSourceItemHolder> {

	private final GifSourcesResolver mGifSourcesResolver;

	GifSourcesAdapter(final Context context) {
		mGifSourcesResolver = new GifSourcesResolver(context);
	}

	@Override
	public GifSourceItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		final View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.source_item, parent, false);
		return new GifSourceItemHolder(itemView);
	}

	@Override
	public void onBindViewHolder(final GifSourceItemHolder holder, int position) {
		final String[] descriptions = holder.itemView.getResources().getStringArray(R.array.sources);
		position %= descriptions.length;

		final GifDrawable existingOriginalDrawable = (GifDrawable) holder.gifImageViewOriginal.getDrawable();
		final GifDrawable existingSampledDrawable = (GifDrawable) holder.gifImageViewSampled.getDrawable();
		final GifDrawableBuilder builder = new GifDrawableBuilder().with(existingOriginalDrawable);
		try {
			mGifSourcesResolver.bindSource(position, builder);
			final GifDrawable fullSizeDrawable = builder.build();
			holder.gifImageViewOriginal.setImageDrawable(fullSizeDrawable);
			holder.gifImageViewOriginal.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (fullSizeDrawable.isPlaying())
						fullSizeDrawable.stop();
					else
						fullSizeDrawable.start();
				}
			});

			builder.with(existingSampledDrawable).sampleSize(3);
			mGifSourcesResolver.bindSource(position, builder);
			final GifDrawable subsampledDrawable = builder.build();
			final SpannableStringBuilder stringBuilder = new SpannableStringBuilder(descriptions[position] + '\ufffc');
			stringBuilder.setSpan(new ImageSpan(subsampledDrawable), stringBuilder.length() - 1, stringBuilder.length(), 0);
			holder.descriptionTextView.setText(stringBuilder);
			holder.gifImageViewSampled.setImageDrawable(subsampledDrawable);
			subsampledDrawable.setCallback(holder.multiCallback);
			holder.multiCallback.addView(holder.gifImageViewSampled);
			holder.multiCallback.addView(holder.descriptionTextView);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public int getItemCount() {
		return Integer.MAX_VALUE;
	}

}
