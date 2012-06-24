package edu.cmu.nishith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.objdetect.CascadeClassifier;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

class FdView extends SampleCvViewBase {
    private static final String TAG = "FdView";
    private Mat                 mRgba;
    private Mat                 mGray;

    private int                 filter = 5;
    private int 				tick   = 0;
    Timer revertTimer = new Timer();
    int orignalRingMode                = 0;
    
    Context local_context;
    private int counter = 1;

    class revertTick extends TimerTask {
    	@Override
    	public void run() {
    		tick = 5;
    	}	
    }
    
    private CascadeClassifier   mCascade;

    public FdView(Context context) {
        super(context);

        local_context = context;
        revertTimer = new Timer();
        
        try {
        	// Get the cascade file provided by OpenCV and load it into the system.
            InputStream is = context.getResources().openRawResource(R.raw.whiteboard);
            File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "lbpcascade_whiteboard.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

          //Get the cascade classifier object which will be used later for object recognition. 
            mCascade = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (mCascade.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                mCascade = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());

            cascadeFile.delete();
            cascadeDir.delete();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder _holder, int format, int width, int height) {
        super.surfaceChanged(_holder, format, width, height);

        synchronized (this) {
            // initialize Mats before usage
            mGray = new Mat();
            mRgba = new Mat();
        }
    }

    @Override
    protected Bitmap processFrame(VideoCapture capture) {
    	// The detection works on a gray scale image. So, we extract a gray scale image for
    	// object detection and an RGBA image which will be displayed back to the user with 
    	// the object inside a green square.
        capture.retrieve(mRgba, Highgui.CV_CAP_ANDROID_COLOR_FRAME_RGBA);
        capture.retrieve(mGray, Highgui.CV_CAP_ANDROID_GREY_FRAME);

        boolean saveit = false;
        
        if (mCascade != null) {
            int height = mGray.rows();
            int faceSize = Math.round(height * FdActivity.minFaceSize);
            List<Rect> faces = new LinkedList<Rect>();
            // Send the image to detectMultiScale which will do the detection
         	// The image co-ordinates where the object was detected will be filled in the 
         	// faces array.
            mCascade.detectMultiScale(mGray, faces, 1.1, 2, 2
                    , new Size(faceSize, faceSize));

          //For each detection we increment the tick counter
            if (!faces.isEmpty()) {
            	tick++;
            	if (tick > filter) {
            		tick = 0;
            		// Set saveit to true so that the bitmap is saved before returning
            		saveit = true;
            		// Set the tick to a high number so that we don't save a lot of copies of the same image
            		// tick will be set to a normal value once the timer runs out. This way will can have
            		// a predictable time gap between consecutive images.
            		tick = 10000;
            		// Start a timer to revert the tick back to 5.
            		revertTimer.cancel();
            		revertTimer = new Timer();
            		TimerTask revert = new revertTick();
            		revertTimer.schedule(revert, 5000);
            	}
            }
            
            // Draw the rectangles around the objects detected.
            for (Rect r : faces)
                Core.rectangle(mRgba, r.tl(), r.br(), new Scalar(0, 255, 0, 255), 3);
        }

        Bitmap bmp = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);

        if (Utils.matToBitmap(mRgba, bmp)){
        	if(saveit == true){
        		try {
        			Time now = new Time();
        			now.setToNow();
        			Long offset = now.gmtoff;
        			String stamp = offset.toString() ;
        			String filename = "Whiteboard" + stamp + "-" + counter;
        			++counter;
        			File path = Environment.getExternalStoragePublicDirectory(
        		            Environment.DIRECTORY_PICTURES);
        			File image = new File(path, filename);
        			FileOutputStream out = new FileOutputStream(image);
        			bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
        			saveit = false;
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
        	}
            return bmp;        	
        }

        bmp.recycle();
        return null;
    }

    @Override
    public void run() {
        super.run();

        synchronized (this) {
            // Explicitly deallocate Mats
            if (mRgba != null)
                mRgba.release();
            if (mGray != null)
                mGray.release();

            mRgba = null;
            mGray = null;
        }
    }
}

