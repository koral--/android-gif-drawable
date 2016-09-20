package pl.droidsonroids.gif.sample;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import pl.droidsonroids.gif.GifTextureView;

public class GifTextureFragment extends BaseFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.texture, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            final GifTextureView gifTextureView = (GifTextureView) view.findViewById(R.id.gifTextureView);
            if (!gifTextureView.isHardwareAccelerated()) {
                view.findViewById(R.id.text_textureview_stub).setVisibility(View.VISIBLE);
            }
        }
    }
}
