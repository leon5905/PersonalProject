package yeohweizhu.truerng;

import android.graphics.ImageFormat;

/**
 * Created by yeohw on 5/15/2017.
 */

public interface ICamera {
    void takePicture();
    public interface PictureTakenCallBack{
        //format as in android.graphics ImageFormat int classifier
        //byte[] data can be null
        void onPictureTaken(byte[] data,byte[] R,byte[] G,byte[] B, int format);
    }
}
