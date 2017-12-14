package com.blogspot.techzealous.imageviewscroll;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ImageViewScroll extends View {

	private static final String TAG = "ImageViewScroll";
	private static final String LOG_MSG_NULL_CONTEXT = "Using ImageViewScroll with null Context";
	
	private WeakReference<Context> mWeakCtx;
	private Uri mUri_resources_image;
	private boolean mShouldFitOnScreen;
	private String mImageFilePath;
	
	private Handler mHandler;
	private ExecutorService mExecutorService;
	private Future<?> mFuture;
	private Runnable mRunnableInvalidateForRegion;
	private ImageViewScrollListener mListener;
	private ScaleGestureDetector mScaleDetector;
	
	/* Size of the image */
	private int mWidthImage;
	private int mHeightImage;
	
	/* Size that this view takes on the screen (actual frame) */
	private int mWidthView;
	private int mHeightView;
	
	private int mWidthThreshold;
	private int mHeightThreshold;
	
	/* Size of this view sampled with mSampleSize */
	private int mWidthViewSampled;
	private int mHeightViewSampled;
	
	private int mSampleSize;
	private boolean mShouldDrawRegion;
	private float mScaleFactor;
	
	/* Rectangle this view takes on the screen (actual frame) */
	private Rect mRectView;
	/* Rectangle this view takes on the screen sampled with mSampleSize */
//	private Rect mRectViewSampled;
	
	/* Position and size of the part of the image that is visible (full size) */
	private Rect mRectScroll;
	/* Position and size of the part of the image that is visible sampled with mSampleSize */
	private Rect mRectScrollSampled;
	
	/* Rectangles used to draw the image */
	/* Rectangle source coordinates from the image */
	private Rect mRectSrc;
	/* Rectangle destination coordinates to which to draw the mRectSrc */
	private Rect mRectDest;
	
	/* Sampled with mSampleSize, mRectSrc and mRectDest */
	private Rect mRectSrcSampled;
	private Rect mRectDestSampled;
	
	/* Size of the Bitmap */
	private Rect mRectBitmap;
	/* Size of the mBitmapRegion */
	private Rect mRectBitmapRegion;
	
	/* Sampled bitmap of the whole image. */
	private Bitmap mBitmapSampled;
	/* Sampled bitmap of the visible part of the image. */
	private Bitmap mBitmapRegion;
	
	private Paint mPaint;
	private Paint mPaintRectScrollSampled;
	
	private float mDownX;
	private float mDownY;
	private float mDeltaX;
	private float mDeltaY;
	
	public ImageViewScroll(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		
		TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.ImageViewScroll, 0, 0);
		try {
			String imageName = array.getString(R.styleable.ImageViewScroll_image_name);
			mShouldFitOnScreen = array.getBoolean(R.styleable.ImageViewScroll_fit_on_screen, false);
			if(imageName != null) {mUri_resources_image = Uri.parse("android.resource://" + context.getPackageName() + "/drawable/" + imageName);}
		} finally {
			array.recycle();
		}
		
		initView(context);
	}

	public ImageViewScroll(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.ImageViewScroll, 0, 0);
		try {
			String imageName = array.getString(R.styleable.ImageViewScroll_image_name);
			mShouldFitOnScreen = array.getBoolean(R.styleable.ImageViewScroll_fit_on_screen, false);
			if(imageName != null) {mUri_resources_image = Uri.parse("android.resource://" + context.getPackageName() + "/drawable/" + imageName);}
		} finally {
			array.recycle();
		}
		
		initView(context);
	}

	public ImageViewScroll(Context context) {
		super(context);
		initView(context);
	}

	public void setListener(ImageViewScrollListener aListener) {
	    mListener = aListener;
    }
	
	public void setImage(String aFilePath, boolean aShouldFitOnScreen)
	{
		mUri_resources_image = null;
		mImageFilePath = aFilePath;
		mShouldFitOnScreen = aShouldFitOnScreen;
		setupSize(new Runnable() {
			@Override
			public void run() {loadFromFile(mImageFilePath, mShouldFitOnScreen);}
		});
	}
	
	public void setImageResource(String aImageName, final boolean aShouldFitOnScreen)
	{
		Context context = mWeakCtx.get();
		if(context == null) {
			Log.w(TAG, LOG_MSG_NULL_CONTEXT);
			return;
		}
		
		mUri_resources_image = Uri.parse("android.resource://" + context.getPackageName() + "/drawable/" + aImageName);
		mImageFilePath = null;
		mShouldFitOnScreen = aShouldFitOnScreen;
		setupSize(new Runnable() {
			@Override
			public void run() {loadImageFromResource(mWeakCtx, mUri_resources_image, mShouldFitOnScreen);}
		});
	}
	
	private void initView(Context aCtx)
	{
		mWeakCtx = new WeakReference<Context>(aCtx);
		mHandler = new Handler();
		mExecutorService = Executors.newSingleThreadExecutor();
		mPaint = new Paint();
		mPaintRectScrollSampled = new Paint();
		mPaintRectScrollSampled.setColor(Color.RED);
		mPaintRectScrollSampled.setAlpha(140);
		
		Display display = ((WindowManager) aCtx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		Point pointSize = new Point();
		display.getSize(pointSize);
		mWidthView = pointSize.x;
		mHeightView = pointSize.y;
		
		mScaleDetector = new ScaleGestureDetector(aCtx, new OnScaleGestureListener() {
			@Override
			public void onScaleEnd(ScaleGestureDetector detector) {
				//Log.i(TAG, "onScaleEnd");
				loadView(mWeakCtx);
			}
			
			@Override
			public boolean onScaleBegin(ScaleGestureDetector detector) {
				//Log.i(TAG, "onScaleBegin");
				return true;
			}
			
			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				//Log.i(TAG, "onScale");
				mScaleFactor = detector.getScaleFactor();
				mScaleFactor = Math.max(0.95f, Math.min(mScaleFactor, 1.05f));
				Log.i(TAG, "onScale, mScaleFactor=" + mScaleFactor);
				
				float width = (float)mRectScroll.width() / mScaleFactor;
				float height = (float)mRectScroll.height() / mScaleFactor;
				if(width < mWidthView) {width = mWidthView;}
				if(height < mHeightView) {height = mHeightView;}
				mRectScroll.set(mRectScroll.left, mRectScroll.top, mRectScroll.left + (int)width, mRectScroll.top + (int)height);
				calculateRectSizes();
				
				invalidate();
				return true;
			}
		});
		
		mRunnableInvalidateForRegion = new Runnable() {
			@Override
			public void run() {
				mShouldDrawRegion = true;
				invalidate();
                if(mListener != null) {
                    mListener.onRegionReady();
                }
			}
		};
		
		setupSize(new Runnable() {
			@Override
			public void run() {
				if(mUri_resources_image != null) {loadImageFromResource(mWeakCtx, mUri_resources_image, false);}
			}
		});
	}
	
	 @Override
	 public boolean performClick()
	 {
		 super.performClick();
		 return true;
	 }
	
	@Override
    public boolean onTouchEvent(MotionEvent event) 
	{
		mScaleDetector.onTouchEvent(event);
		
    	switch (event.getAction()) {
    		case MotionEvent.ACTION_DOWN:
    			mDownX = event.getX();
    			mDownY = event.getY();
    			break;
    		case MotionEvent.ACTION_MOVE:
    			float curX = event.getX();
    			float curY = event.getY();
    			mDeltaX = (int)(curX - mDownX);
    			mDeltaY = (int)(curY - mDownY);
    			mDownX = curX;
    			mDownY = curY;
    			mRectScroll.offset(-(int)mDeltaX, -(int)mDeltaY);
    			
    			calculateRectSizes();
    			
    			loadView(mWeakCtx);
    			invalidate();
    			break;
    		case MotionEvent.ACTION_UP:
    			performClick();
    			break;
    	}
    	return true;
    }
	
	private void loadImageFromResource(final WeakReference<Context> aWeakCtx, final Uri aUri, final boolean aShouldFitOnScreen)
	{
		if(mFuture != null ) {mFuture.cancel(true);}
		mFuture = mExecutorService.submit(new Runnable() {
			@Override
			public void run() {
				Context ctx = aWeakCtx.get();
				if(ctx == null) {
					Log.w(TAG, LOG_MSG_NULL_CONTEXT);
					return;
				}
				
				/* Load the scaled down picture to use when scrolling and while the full resolution region of the image is loading. */
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				try {
					BitmapFactory.decodeStream(ctx.getContentResolver().openInputStream(aUri), null, options);
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
					return;
				}
				mWidthImage = options.outWidth;
				mHeightImage = options.outHeight;
				
				options.inJustDecodeBounds = false;
				mSampleSize = calculateSampleSizePowerOfTwo(mWidthImage, mHeightImage, mWidthView, mHeightView, true);
				options.inSampleSize = mSampleSize;
				
				mWidthViewSampled = mWidthView / mSampleSize;
				mHeightViewSampled = mHeightView / mSampleSize;
				mWidthThreshold = mWidthView * mSampleSize;
				mHeightThreshold = mHeightView * mSampleSize;
				
				InputStream inStream = null;
				try {
					inStream = new BufferedInputStream(ctx.getContentResolver().openInputStream(aUri));
					mBitmapSampled = BitmapFactory.decodeStream(inStream, null, options);
					
					if(Thread.currentThread().isInterrupted()) {
						mBitmapSampled.recycle();
						return;
					}
				} catch (FileNotFoundException e3) {
					e3.printStackTrace();
					return;
				} finally {
					if(inStream != null) {try {inStream.close();} catch (IOException e) {e.printStackTrace();}}
				}
				
				mWidthViewSampled = mWidthView / mSampleSize;
				mHeightViewSampled = mHeightView / mSampleSize;
				mRectView = new Rect(0, 0, mWidthView, mHeightView);
				mRectScroll = new Rect(0, 0, mWidthView, mHeightView);
				mRectScrollSampled = new Rect(0, 0, mWidthViewSampled, mHeightViewSampled);
				mRectBitmap = new Rect(0, 0, mWidthImage, mHeightImage);
				mRectBitmapRegion = new Rect(0, 0, mWidthView, mHeightView);
				
				mRectSrc = new Rect(mRectScroll);
				mRectDest = new Rect(mRectView);
				mRectSrcSampled = new Rect(mRectScroll.left / mSampleSize, mRectScroll.top / mSampleSize, mRectScroll.right / mSampleSize, mRectScroll.bottom / mSampleSize);
				mRectDestSampled = new Rect(mRectView.left / mSampleSize, mRectView.top / mSampleSize, mRectView.right / mSampleSize, mRectView.bottom / mSampleSize);
				
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						invalidate();
					}
				});
				
				/* Load region with full resolution */
				loadView(mWeakCtx);
			}
		});
	}
	
	private void loadFromFile(final String aFilePath, final boolean aShouldFitOnScreen)
	{
		if(mFuture != null ) {mFuture.cancel(true);}
		mFuture = mExecutorService.submit(new Runnable() {
			@Override
			public void run() {
				
				/* Load the scaled down picture to use when scrolling and while the full resolution region of the image is loading. */
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeFile(aFilePath, options);
				
				mWidthImage = options.outWidth;
				mHeightImage = options.outHeight;
				mSampleSize = calculateSampleSizePowerOfTwo(mWidthImage, mHeightImage, mWidthView, mHeightView, true);
				
				options.inJustDecodeBounds = false;
				options.inSampleSize = mSampleSize;
				
				File file = new File(aFilePath);
				InputStream inStream = null;
				try {
					inStream = new FileInputStream(file);
					mBitmapSampled = BitmapFactory.decodeStream(inStream, null, options);
					
					if(Thread.currentThread().isInterrupted()) {
						mBitmapSampled.recycle();
						return;
					}				
				} catch (FileNotFoundException e3) {
					e3.printStackTrace();
				} finally {
					if(inStream != null) {try {inStream.close();} catch (IOException e) {e.printStackTrace();}}
				}
				
				mWidthViewSampled = mWidthView / mSampleSize;
				mHeightViewSampled = mHeightView / mSampleSize;
				mWidthThreshold = mWidthView * mSampleSize;
				mHeightThreshold = mHeightView * mSampleSize;
				
				mWidthViewSampled = mWidthView / mSampleSize;
				mHeightViewSampled = mHeightView / mSampleSize;
				mRectView = new Rect(0, 0, mWidthView, mHeightView);
				mRectScroll = new Rect(0, 0, mWidthView, mHeightView);
				mRectScrollSampled = new Rect(0, 0, mWidthViewSampled, mHeightViewSampled);
				mRectBitmap = new Rect(0, 0, mWidthImage, mHeightImage);
				mRectBitmapRegion = new Rect(0, 0, mWidthView, mHeightView);
				
				mRectSrc = new Rect(mRectScroll);
				mRectDest = new Rect(mRectView);
				mRectSrcSampled = new Rect(mRectScroll.left / mSampleSize, mRectScroll.top / mSampleSize, mRectScroll.right / mSampleSize, mRectScroll.bottom / mSampleSize);
				mRectDestSampled = new Rect(mRectView.left / mSampleSize, mRectView.top / mSampleSize, mRectView.right / mSampleSize, mRectView.bottom / mSampleSize);
				
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						invalidate();
					}
				});
				
				/* Load region with full resolution */
				loadView(mWeakCtx);			
			}
		});	
	}
	
	@Override
	protected void onDraw(Canvas canvas) 
    {
		if(mBitmapSampled == null) {return;}
		
		if(mShouldDrawRegion) {
    		canvas.drawBitmap(mBitmapRegion, mRectBitmapRegion, mRectDest, mPaint);
    	} else {
    		canvas.drawBitmap(mBitmapSampled, mRectSrcSampled, mRectDest, mPaint);
    	}
    	
		//if we want to see the mRectScroll on the sampled down version of the image.
//    	canvas.drawBitmap(mBitmapSampled, 0, 0, mPaint);
//    	canvas.drawRect(mRectScrollSampled, mPaintRectScrollSampled);
		//if we want to see how the mRectDest scales down when the image is smaller than the view size
//    	canvas.drawRect(mRectDest, mPaintRectScrollSampled);
	}
	
	private void loadView(final WeakReference<Context> aWeakCtx)
	{
	    if(mListener != null) {mListener.onRegionPrepare();}
		mShouldDrawRegion = false;
		if(mFuture != null) {mFuture.cancel(true);}
		mFuture = mExecutorService.submit(new Runnable() {
			@Override
			public void run() {
				Context ctx = aWeakCtx.get();
				if(ctx == null) {
					Log.w(TAG, LOG_MSG_NULL_CONTEXT);
					return;
				}
				
				if(mUri_resources_image != null) {
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inJustDecodeBounds = false;
					calculateRectSizes();
					int inSampleSize = calculateSampleSizePowerOfTwo(mRectScroll.width(), mRectScroll.height(), mWidthView, mHeightView, true);
					options.inSampleSize = inSampleSize;
					
					InputStream inStream = null;
					try {						
						if(mUri_resources_image != null) {
							inStream = new BufferedInputStream(ctx.getContentResolver().openInputStream(mUri_resources_image));
						} else if(mImageFilePath != null) {
							File file = new File(mImageFilePath);
							inStream = new FileInputStream(file);
						} else {
							return;
						}
						
						BitmapRegionDecoder brd = BitmapRegionDecoder.newInstance(inStream, false);
						
						mBitmapRegion = brd.decodeRegion(mRectSrc, options);
						mRectBitmapRegion.set(0, 0, mBitmapRegion.getWidth(), mBitmapRegion.getHeight());
												
						if(Thread.currentThread().isInterrupted()) {
							mBitmapRegion.recycle();
							return;
						}
						mHandler.post(mRunnableInvalidateForRegion);
						
					} catch (FileNotFoundException e3) {
						e3.printStackTrace();
					} catch (IOException e3) {
						e3.printStackTrace();
					} catch (Exception ex) {
						ex.printStackTrace();
					} finally {
						if(inStream != null) {try {inStream.close();} catch (IOException e) {e.printStackTrace();}}
					}
				}
			}
		});
	}

	private int calculateSampleSize(int sourceWidth, int sourceHeight, int reqWidth, int reqHeight) {
		int sampleSize = 1;
		if (sourceHeight > reqHeight || sourceWidth > reqWidth) {
		        
			/* Calculate ratios of height and width to requested height and width (this rounds up)*/
			int heightRatio = (int)Math.ceil((double) sourceHeight / (double) reqHeight);
			int widthRatio = (int)Math.ceil((double) sourceWidth / (double) reqWidth);

			/* Choose the higher ratio as sampleSize value */
			if(heightRatio > widthRatio) {sampleSize = heightRatio;} else {sampleSize = widthRatio;}
		}
		return sampleSize;
	}
	
	private int calculateSampleSizePowerOfTwo(int sourceWidth, int sourceHeight, int reqWidth, int reqHeight, boolean aRoundUp) {
		int sampleSize = calculateSampleSize(sourceWidth, sourceHeight, reqWidth, reqHeight);
		
		if(sampleSize == 1) {return 1;}
		
		while((sampleSize & (sampleSize - 1)) != 0) {
			if(aRoundUp) {sampleSize++;} else {sampleSize--;}
		}
		return sampleSize;
	}
	
	private void calculateRectSizes()
	{
		int left = mRectScroll.left;
		int top = mRectScroll.top;
		if(left < 0) {left = 0;}
		if(top < 0) {top = 0;}
		int right = left + mRectScroll.width();
		int bottom = top + mRectScroll.height();
		
		if(mRectScroll.width() > mWidthImage) {
			left = 0;
			right = left + mRectScroll.width();
		} else if(mRectScroll.right > mWidthImage) {
			left = mWidthImage - mRectScroll.width();
			right = left + mRectScroll.width();
		}
		
		if(mRectScroll.height() > mHeightImage) {
			top = 0;
			bottom = top + mRectScroll.height();
		} else if(mRectScroll.bottom > mHeightImage) {
			top = mHeightImage - mRectScroll.height();
			bottom = top + mRectScroll.height();
		}
		
		if(right > mWidthThreshold) {right = mWidthThreshold;}
		if(bottom > mHeightThreshold) {bottom = mHeightThreshold;}
		
		mRectScroll.set(left, top, right, bottom);
		
		int leftSampled = left / mSampleSize;
		int topSampled = top / mSampleSize;
		int rightSampled = right / mSampleSize;
		int bottomSampled = bottom / mSampleSize;
		mRectScrollSampled.set(leftSampled, topSampled, rightSampled, bottomSampled);
		
		/* If the image has been scrolled smaller than the view size, get the rectangle of the image and set the rectangle of the view in which to draw it to be proportional to it. */
		mRectSrc.set(mRectScroll);
		mRectSrc.intersect(mRectBitmap);
		int widthDest = mRectView.width();
		int heightDest = mRectView.height();
		if(mRectScroll.width() > mWidthImage) {
			float ratio = (float)mRectScroll.width() / (float)mRectSrc.width();
			widthDest = (int)((float)mRectView.width() / ratio);
		}
		if(mRectScroll.height() > mHeightImage) {
			float ratio = (float)mRectScroll.height() / (float)mRectSrc.height();
			heightDest = (int)((float)mRectView.height() / ratio);
		}
		mRectDest.set(0, 0, widthDest, heightDest);
		
		mRectSrcSampled.set(mRectSrc.left / mSampleSize, mRectSrc.top / mSampleSize, mRectSrc.right / mSampleSize, mRectSrc.bottom / mSampleSize);
		mRectDestSampled.set(mRectDest.left / mSampleSize, mRectDest.top / mSampleSize, mRectDest.right / mSampleSize, mRectDest.bottom / mSampleSize);
	}
	
	private void setupSize(final Runnable aRunnable)
	{
		this.post(new Runnable() {
			@Override
			public void run() {
				mWidthView = ImageViewScroll.this.getWidth();
				mHeightView = ImageViewScroll.this.getHeight();
				
				if(aRunnable != null) {aRunnable.run();}
			}
		});
	}
}
