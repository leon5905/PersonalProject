package yeohweizhu.truerng;

import android.graphics.ImageFormat;

/**
 * Created by yeohw on 5/15/2017.
 */

public interface ICamera {
    void takePicture();
    public interface PictureTakenCallBack{
        //format as in android.graphics ImageFormat int classifier
        void onPictureTaken(byte[] imageData, int format);
    }
}
