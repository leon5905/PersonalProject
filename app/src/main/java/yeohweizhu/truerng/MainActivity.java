package yeohweizhu.truerng;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.Image;
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
            public void onPictureTaken(Image image) {
                Bitmap bitmap=null;
                byte[] bytes=null;
                int imageFormat = image.getFormat();

                if ( imageFormat == ImageFormat.JPEG){
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }
                else if (imageFormat == ImageFormat.YUV_420_888){
                    bitmap = ImageFormatConverter.YUV_420_888_toRGB(image,image.getWidth(),image.getHeight(),getApplicationContext());
                    bitmap = ImageFormatConverter.RotateBitmap(bitmap,90);
                }
                else if (imageFormat == ImageFormat.RAW_SENSOR){
//                    System.out.println("Tracking:" + width + " " +  height + "  " + imageData.length );
//                    bitmap = Bitmap.createBitmap(width, height,Bitmap.Config.ARGB_8888);
//                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(imageData));

//                    byte [] source = imageData; //Comes from somewhere...
//                    byte [] Bits = new byte[source.length*4]; //That's where the ARGB array goes.
//                    int i;
//                    for(i=0;i<source.length;i++)
//                    {
//                        Bits[i*4] =
//                        Bits[i*4+1] =
//                         Bits[i*4+2] = ~source[i]; //Invert the source bits
//                        Bits[i*4+3] = -1;//0xff, that's the alpha.
//                    }
//
//                    //Now put these nice ARGB pixels into a Bitmap object
//                    Bitmap bm = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888);
//                    bm.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));
                }

                //TODO try to get pixel
                if (bytes!=null)
                    System.out.println("Byte Array Length : " + bytes.length);

                for (int i=0;i<50;i++){
                    for (int y=0;y<50;y++){
                        int pixel = bitmap.getPixel(i,y);
                        int red = Color.red(pixel);
                        int green = Color.green(pixel);
                        int blue = Color.blue(pixel);
                        System.out.print(red + " , ");
                    }
                    System.out.println();
                }

                sampleImageView.setImageBitmap(bitmap);
            }

            @Override
            public void onPictureTaken(byte[] image, int imageFormat, int width, int height) {
                if (imageFormat==ImageFormat.JPEG){
                    Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
                    sampleImageView.setImageBitmap(bitmap);
                }
                else if (imageFormat==ImageFormat.RAW_SENSOR){

                }


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
//        ChaosMap.Test();
//        ChaosMap.TestFixed();
        ChaosMap.TestFixedMap();
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
