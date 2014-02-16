package pl.droidsonroids.gif.sample;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

public class MainGifActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main_gif);
    }

    public void onGibCradleClick(View view) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.cradle_src_url))));
        }
        catch (ActivityNotFoundException anfe)
        {
            Toast.makeText(this,R.string.anfe_web,Toast.LENGTH_LONG).show();
        }
    }
}
