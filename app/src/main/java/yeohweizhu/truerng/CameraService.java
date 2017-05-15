package yeohweizhu.truerng;

import android.content.Context;
import android.os.Build;

/**
 * Created by yeohw on 5/15/2017.
 */

public class CameraService {
    private Context mContext;
    private ICamera mCamera;

    public CameraService(Context context, ICamera.PictureTakenCallBack callBack){
        mContext = context;

        //Instantiate Correct ICamera based on Version here
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            mCamera= CameraLollipop.createInstance(mContext,callBack);
        }
        else{
            mCamera=CameraPreLollipop.createInstance(mContext,callBack);

        }
    }

    public void takePicture(){
        mCamera.takePicture();
    }
}
