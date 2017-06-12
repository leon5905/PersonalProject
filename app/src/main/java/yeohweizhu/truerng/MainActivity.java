package yeohweizhu.truerng;

import android.Manifest;
import android.app.Application;
import android.content.Context;
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
import java.io.OutputStreamWriter;
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

    //Stopwatch
    private long nanoSecondStartTime;

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
            public void onPictureTaken(byte[] byteArr) {
                long elapsedTime = System.currentTimeMillis() - nanoSecondStartTime;
                System.out.println("DNG Nanosecond Taken: " + elapsedTime);

                FileOutputStream fos = null;
                try {
                    fos = openFileOutput("abc",  Context.MODE_PRIVATE);
                    fos.write(byteArr);
                    fos.close();
                    System.out.println("Save File Complete");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onPictureTaken(Image image) {
                long elapsedTime = System.currentTimeMillis() - nanoSecondStartTime;
                System.out.println("Raw Nanosecond Taken 1: " + elapsedTime);

                Bitmap bitmap=null;
                byte[] bytes=null;
                int imageFormat = image.getFormat();

                if ( imageFormat == ImageFormat.JPEG){
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    //Save pixel value to file
                    int[] pixels = new int[bitmap.getWidth()*bitmap.getHeight()];
                    bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());

                    byte[] byteArr= new byte[pixels.length*3];
                    for(int i=0;i<pixels.length;i++){
                        byteArr[i*3] =  (byte) ((pixels[i]>>16) & 0xFF);
                        byteArr[i*3+1] = (byte) ((pixels[i]>>8) & 0xFF);
                        byteArr[i*3+2]= (byte) ((pixels[i]) & 0xFF);
                    }

                }
                else if (imageFormat == ImageFormat.YUV_420_888) {
                    //bitmap = ImageFormatConverter.YUV_420_888_toRGB(image,image.getWidth(),image.getHeight(),getApplicationContext());
                    //Rotate YUV to correct orientation
                    //bitmap = ImageFormatConverter.RotateBitmap(bitmap,90);
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    byte[] y = new byte[buffer.remaining()];
                    buffer.get(y);

                    buffer = planes[1].getBuffer();
                    byte[] u = new byte[buffer.remaining()];
                    buffer.get(u);

                    buffer = planes[2].getBuffer();
                    byte[] v = new byte[buffer.remaining()];
                    buffer.get(v);


//                    byte[] byteArr = new byte[y.length * 2];
//
//                    System.out.println("Y Length : " + y.length);
//                    System.out.println("U Length : " + u.length);
//                    System.out.println("V Length : " + v.length);
//                    for (int i = 0; i < (y.length) - 2; i++) {
//                        if (i % 2 == 0) {
//                            byteArr[i * 2] = y[i];
//                            byteArr[i * 2 + 1] = u[i / 2];
//                        } else {
//                            byteArr[i * 2] = y[i];
//                            byteArr[i * 2 + 1] = v[i / 2];
//                        }
//                    }
//                    byteArr[y.length * 2 - 4] = y[y.length - 2];
//                    byteArr[y.length * 2 - 3] = u[u.length / 2];
//
//                    byteArr[y.length * 2 - 2] = y[y.length - 1];
//                    byteArr[y.length * 2 - 1] = v[v.length / 2];


                    byte[] uTrimmed = new byte[u.length*7/8];
                    byte[] vTrimmed = new byte[v.length*7/8];

                    //Generate 7 byte out of 8 byte
                    //Removing 7th bit
                    int trimmedIndex=0;
                    for (int i=0;i<u.length;){
                        if (u.length-i<8)
                            break;

                        int range=7;
                        int offset=1;
                        long bit64=0;
                        long bit64_v=0;

                        for (int j=0;j<8;j++){
                            //System.out.println(Integer.toBinaryString((int) u[i]) + " asd " + Long.toBinaryString(((long) (u[i]&0b10000000 | (u[i]<<2>>>1)) ) << (range*8+offset)));
                            bit64 |= (  ((long) (u[i]&0b10000000 | ((u[i]&0b00111111) <<1) ) ) << (range*8+offset) ) ;
//                            System.out.println("asd: " + String.format("%8s", Integer.toBinaryString(u[i] & 0xFF)).replace(' ', '0'));

                            bit64_v |= (  ((long) (v[i]&0b10000000 | ((v[i]&0b00111111) <<1) ) ) << (range*8+offset) ) ;
                            range--;
                            offset++;
                            i++;
                        }

//                        System.out.println("58 bit asd: " + Long.toBinaryString(bit64) );

                        for (int byteNum=7;byteNum>0;byteNum--){
                            uTrimmed[trimmedIndex]= (byte) ( (bit64>> ( 8*(byteNum) ))&0xff );
                            //System.out.println("Byte asd: ? " + uTrimmed[trimmedIndex] + " ?? " +  String.format("%8s", Integer.toBinaryString(uTrimmed[trimmedIndex]  & 0xFF)).replace(' ', '0'));

                            vTrimmed[trimmedIndex]= (byte) ((bit64_v>> ( 8*(byteNum) ))&0xff);

                            trimmedIndex++;
                        }
                    }

                    byte[] byteArr = new byte[uTrimmed.length*4];

                    for (int i = 0; i < (uTrimmed.length*2); i++) {
                        if (i % 2 == 0) {
                            byteArr[i * 2] = y[i];
                            byteArr[i * 2 + 1] = uTrimmed[i / 2];
                        } else {
                            byteArr[i * 2] = y[i];
                            byteArr[i * 2 + 1] = vTrimmed[i / 2];
                        }
                    }

                    FileOutputStream fos = null;
                    try {
                        fos = openFileOutput("abc", Context.MODE_PRIVATE);
                        fos.write(byteArr);
                        fos.close();
                        System.out.println("Save File Complete");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

//                    for (int i=0;i<byteArr.length;){
//                        if (byteArr[i]>0)
//                            System.out.print(byteArr[i++] + " ");
//                        else
//                            i++;
//                        i++;
//                        //System.out.print(byteArr[i++] + " ");
//                        if (byteArr[i]>0)
//                            System.out.print(byteArr[i++] + " ");
//                        else
//                            i++;
//                        i++;
//                        System.out.println();
//                    }

//                    for (int i=0;i<byteArr.length;){
//                        //System.out.print(byteArr[i++] + " ");
//                        i++;
//                        if (byteArr[i]<80 && byteArr[i]>-80)
//                            System.out.print(byteArr[i++] + " ");
//                        else
//                            i++;
//                        //System.out.print(byteArr[i++] + " ");
//                        i++;
//                        if (byteArr[i]<80 && byteArr[i]>-80)
//                            System.out.print(byteArr[i++] + " ");
//                        else
//                            i++;
//                        System.out.println();
//                    }
                }
                else if (imageFormat == ImageFormat.RAW_SENSOR){
                    //Do nothing
                }

                //TODO try to get pixel
                if (bytes!=null)
                    System.out.println("Byte Array Length : " + bytes.length);

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
                        nanoSecondStartTime = System.currentTimeMillis();
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
//        ChaosMap.TestFixedMap();

        nanoSecondStartTime = System.nanoTime();
        long takenTime = System.nanoTime() - nanoSecondStartTime;
        System.out.println("Empty Time = " + takenTime);

        nanoSecondStartTime = System.nanoTime();
        this.stringFromJava();
        takenTime = System.nanoTime() - nanoSecondStartTime;
        System.out.println("Java Time = " + takenTime);

        nanoSecondStartTime = System.nanoTime();
        this.stringFromJNI();
        takenTime = System.nanoTime() - nanoSecondStartTime;
        System.out.println("JNI Time = " + takenTime);

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
    public String stringFromJava(){
        String str=  "Hello from Java";
        return str;
    }
}
