package pl.droidsonroids.gif.sample;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;

import pl.droidsonroids.gif.sample.sources.GifSourcesFragment;

class MainPagerAdapter extends FragmentStatePagerAdapter {
	private final String[] mPageTitles;
	private final Fragment[] mPages;

	MainPagerAdapter(FragmentActivity act) {
		super(act.getSupportFragmentManager());
		mPageTitles = act.getResources().getStringArray(R.array.pages);
		mPages = new Fragment[]{
				new GifSourcesFragment(),
				new GifTextViewFragment(),
				new GifTextureFragment(),
				new ImageSpanFragment(),
				new AnimationControlFragment(),
				new HttpFragment(),
				new TexturePlaceholderFragment(),
				new GifTexImage2DFragment(),
				new AnimatedSelectorFragment(),
				new AboutFragment()
		};
	}

	@Override
	public Fragment getItem(int position) {
		return mPages[position];
	}

	@Override
	public int getCount() {
		return mPageTitles.length;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		return mPageTitles[position];
	}
}
