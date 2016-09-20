package pl.droidsonroids.gif.sample;

import android.support.v4.app.Fragment;

import com.squareup.leakcanary.RefWatcher;

public abstract class BaseFragment extends Fragment {
    @Override
    public void onDestroy() {
        super.onDestroy();
        RefWatcher refWatcher = ((MainActivity) getContext()).getRefWatcher();
        refWatcher.watch(this);
    }
}
