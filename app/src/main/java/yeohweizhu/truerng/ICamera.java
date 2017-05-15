package yeohweizhu.truerng;

/**
 * Created by yeohw on 5/15/2017.
 */

public interface ICamera {
    void takePicture();
    public interface PictureTakenCallBack{
        void onPictureTaken(byte[] rawData);
    }
}
