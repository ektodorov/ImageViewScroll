package com.blogspot.techzealous.imageviewscroll;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {
	
	//private static final String TAG = "MainActivity";
    private ImageViewScroll mImageViewScroll;
    private ProgressBar mProgressBar;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mImageViewScroll = (ImageViewScroll)findViewById(R.id.imageViewScrollMain);
        mProgressBar = (ProgressBar)findViewById(R.id.progressBar);

        final WeakReference<MainActivity> weakThis = new WeakReference<MainActivity>(MainActivity.this);
		mImageViewScroll.setListener(new ImageViewScrollListener() {
            @Override
            public void onRegionPrepare() {
                MainActivity strongThis = weakThis.get();
                if(strongThis == null) {return;}

                strongThis.mProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onRegionReady() {
                MainActivity strongThis = weakThis.get();
                if(strongThis == null) {return;}

                strongThis.mProgressBar.setVisibility(View.INVISIBLE);
            }
        });
	}
}
