package yeohweizhu.truerng;

import android.graphics.ImageFormat;
import android.media.Image;

/**
 * Created by yeohw on 5/15/2017.
 */

public interface ICamera {
    void takePicture();
    public interface PictureTakenCallBack{
        //format as in android.graphics ImageFormat int classifier
        //byte[] data can be null
        void onPictureTaken(byte[] byteArr);
        void onPictureTaken(Image image);
        void onPictureTaken(byte[] image, int imageFormat, int width,int height);
    }
}
