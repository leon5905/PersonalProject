package yeohweizhu.truerng;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by yeohw on 5/15/2017.
 */

public class CameraPreLollipop implements ICamera {
    private static final String TAG ="CameraPreLollipop";

    //CameraPreLollipop callback
    private ICamera.PictureTakenCallBack mCallback;
    private Context mContext;

    //Internal Use of old api callback
    private Camera.PictureCallback mInternalCallback;

    public static CameraPreLollipop createInstance(Context context, ICamera.PictureTakenCallBack callback){
        return new CameraPreLollipop(context,callback);
    }

    private CameraPreLollipop() throws IllegalAccessException{
        throw new IllegalAccessException();
    };

    private CameraPreLollipop(Context context, ICamera.PictureTakenCallBack callback) {
        mContext = context;
        mCallback = callback;

        mInternalCallback = new Camera.PictureCallback() {
            public void onPictureTaken(byte[] data, Camera camera) {
                Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length);
                Log.d(TAG, String.valueOf(camera.getParameters().getPictureFormat()));

                camera.stopPreview();
                camera.release();
                camera = null;

                mCallback.onPictureTaken(data,null,null,null, ImageFormat.JPEG);
            }
        };
    }

    @Override
    public void takePicture() {
        SurfaceView surface = new SurfaceView(mContext);
        Camera camera = Camera.open();
        try {
            camera.setPreviewDisplay(surface.getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
        camera.startPreview();
        camera.takePicture(null,null,mInternalCallback);
    }
}
