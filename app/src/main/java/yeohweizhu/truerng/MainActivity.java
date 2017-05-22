package yeohweizhu.truerng;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    //Static Constant
    private static final String TAG="MainActivity";

    //Request Code
    private static final int CAMERA_PERMISSION_REQUEST=1;

    //Service
    private CameraService mCameraService;
    private ICamera.PictureTakenCallBack mPictureTakenCallBack;

    //GUI
    private ImageView sampleImageView;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPictureTakenCallBack = new ICamera.PictureTakenCallBack(){
            @Override
            public void onPictureTaken(byte[] imageData, int imageFormat) {
                Bitmap bitmap=null;

                if ( imageFormat == ImageFormat.JPEG){
                    bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                }
                else if (imageFormat == ImageFormat.YUV_420_888){
//                    R = Y + 1.402 (Cr-128)
//                    G = Y - 0.34414 (Cb-128) - 0.71414 (Cr-128)
//                    B = Y + 1.772 (Cb-128)
                }
                else if (imageFormat == ImageFormat.RAW_SENSOR){

                }



                sampleImageView.setImageBitmap(bitmap);
            }
        };

        //Instantiate Service
        mCameraService = new CameraService(getApplicationContext(), mPictureTakenCallBack);

        //Setup GUI Reference
        sampleImageView = (ImageView) this.findViewById(R.id.sample_image);
        ((Button) this.findViewById(R.id.sample_button)).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mCameraService.takePicture();
                    }
                }
        );

        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA);
        //Ask for permission if not granted
        if (permissionCheck == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }

        // Example of a call to a native method
        // TextView tv = (TextView) findViewById(R.id.sample_text);
        // tv.setText(stringFromJNI());
    }

    @Override
    protected void onStart(){
        super.onStart();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSION_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted
                    //Great
                } else {
                    // permission denied
                    this.finish();
                }
                return;
            }
            //Might have other permission as well
        }
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
