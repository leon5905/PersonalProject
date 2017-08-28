package yeohweizhu.truerng;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.media.Image;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    //Static Constant
    private static final String TAG="MainActivity";

    //Request Code
    private static final int CAMERA_PERMISSION_REQUEST=1;

    //Service
    private CameraService mCameraService;
    private ICamera.PictureTakenCallBack mPictureTakenCallBack;

    //GUI
    private TextView mTextView;

    //Stopwatch
    private long startTime;


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) this.findViewById(R.id.sample_text);
        ((Button) this.findViewById(R.id.sample_button)).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startTime = System.currentTimeMillis();
                        mTextView.setText("\nStart Capturing Picture");
                        mCameraService.takePicture();
                    }
                }
        );

        mPictureTakenCallBack = new ICamera.PictureTakenCallBack(){
            @Override
            public void onPictureTaken(Image image) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                mTextView.append("\nEnd Capturing Picture : " + elapsedTime/1000.0);

                Bitmap bitmap=null;
                byte[] bytes=null;
                int imageFormat = image.getFormat();

                if (imageFormat!=ImageFormat.YUV_420_888){
                    throw new AssertionError();
                }

                if (imageFormat == ImageFormat.YUV_420_888) {

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

                    image.close();

                    int targetLength = u.length/2;
                    byte[] uTrimmed = new byte[targetLength];
                    byte[] vTrimmed = new byte[targetLength];
                    byte[] yTrimmed = new byte[uTrimmed.length*2]; //double the length of uTrimmed

                    mTextView.append("\nStart Preprocessing");
                    int trimmedIndex=0;

                    int j=0;
//                    for (int i=0;i<targetLength*2;i+=2){
//                        uTrimmed[trimmedIndex] = (byte) ( ( (u[i]&0b11110000) ^  ((u[i]<<4)&0b11110000) ) | ( ((u[i+1]>>4)&0b1111) ^  (u[i+1]&0b1111) ) );
//                        vTrimmed[trimmedIndex] = (byte) ( ( (v[i]&0b11110000) ^  ((v[i]<<4)&0b11110000) ) | ( ((v[i+1]>>4)&0b1111) ^  (v[i+1]&0b1111) ) );
//
//                        yTrimmed[trimmedIndex*2] = (byte) ( ( (y[j]&0b11110000) ^  ((y[j]<<4)&0b11110000) ) | ( ((y[j+1]>>4)&0b1111) ^  (y[j+1]&0b1111) ) );
//                        yTrimmed[trimmedIndex*2+1] = (byte) ( ( (y[j+2]&0b11110000) ^  ((y[j+2]<<4)&0b11110000) ) | ( ((y[j+3]>>4)&0b1111) ^  (y[j+3]&0b1111) ) );
//
//                        j+=4;
//                        ++trimmedIndex;
//                    }

                    for (int i=0;i<targetLength*2;i+=2){
                        uTrimmed[trimmedIndex] = (byte) ( ( u[i]<<4 )&0xF0 |  (u[i+1])&0x0F  );
                        vTrimmed[trimmedIndex] = (byte) ( ( ( v[i]<<4 )&0xF0 |  (v[i+1])&0x0F  ));
                        yTrimmed[trimmedIndex*2] = (byte) ( ( ( y[j]<<4 )&0xF0 |  (y[j+1])&0x0F  ));
                        yTrimmed[trimmedIndex*2+1] = (byte) ( ( ( y[j+2]<<4 )&0xF0 |  (y[j+3])&0x0F  ));

                        j+=4;
                        ++trimmedIndex;
                    }

                    elapsedTime = System.currentTimeMillis() - startTime;
                    mTextView.append("\nEnd Preprocssing: " + elapsedTime/1000.0);

                    byte[] byteArr = new byte[uTrimmed.length*4];
                    byte[] byteArrNoTrimmed = new byte[uTrimmed.length*4];

                    mTextView.append("\nStart Preprocssing 2:");
                    //Interlace UV like this, Y U Y V Y U Y V...
                    for (int i = 0; i < (uTrimmed.length*2); i++) {
                        if (i % 2 == 0) {
                            byteArr[i * 2] = yTrimmed[i];
                            byteArr[i * 2 + 1] = uTrimmed[i / 2];

                            byteArrNoTrimmed[i * 2] = y[i];
                            byteArrNoTrimmed[i * 2 + 1] = u[i / 2];
                        } else {
                            byteArr[i * 2] = yTrimmed[i];
                            byteArr[i * 2 + 1] = vTrimmed[i / 2];

                            byteArrNoTrimmed[i * 2] = y[i];
                            byteArrNoTrimmed[i * 2 + 1] = v[i / 2];
                        }
                    }
                    elapsedTime = System.currentTimeMillis() - startTime;
                    mTextView.append("\nEnd Preprocssing 2: " + elapsedTime/1000.0);

                    //Post Process
                    mTextView.append("\nStart Processing: ");
                    byte[] finalByteArr = ChaosTrueRNG.Postprocess(byteArr);
                    elapsedTime = System.currentTimeMillis() - startTime;
                    mTextView.append("\nEnd Postprocessing: " + elapsedTime/1000.0);

                    mTextView.append("\nStart Processing 2: ");
                    byte[] finalByteNoTrimmed = new byte[1];
                    finalByteNoTrimmed = ChaosTrueRNG.Postprocess(byteArrNoTrimmed);
                    elapsedTime = System.currentTimeMillis() - startTime;
                    mTextView.append("\nEnd Postprocessing 2: " + elapsedTime/1000.0);

                    //Save to File
                    FileOutputStream fos = null;
                    try {
                        fos = openFileOutput("abc", Context.MODE_PRIVATE);
                        fos.write(finalByteNoTrimmed);
                        fos.close();

                        fos = openFileOutput("abc_post", Context.MODE_PRIVATE);
                        fos.write(finalByteArr);
                        fos.close();
                        System.out.println("Save File Complete");
                        mTextView.append("\nSaved: ");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            //Burst Request
            public void onPictureTaken(Image[] imageArr) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                mTextView.append("\nEnd Capturing Picture : " + elapsedTime/1000.0);

                List<byte[]> yList = new ArrayList<>();
                List<byte[]> uList = new ArrayList<>();
                List<byte[]> vList = new ArrayList<>();

                for (Image image:imageArr){
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

                    yList.add(y);
                    uList.add(u);
                    vList.add(v);

                    image.close();
                }

                byte[] result = ChaosTrueRNG.Preprocess(yList,uList,vList);
                elapsedTime = System.currentTimeMillis() - startTime;
                mTextView.append("\nEnd PreProcess 1 : " + elapsedTime/1000.0);

                result = ChaosTrueRNG.PostprocessParallel(result);
                elapsedTime = System.currentTimeMillis() - startTime;
                mTextView.append("\nEnd PostProcess 1 : " + elapsedTime/1000.0);

//                byte[] noTrimResult = ChaosTrueRNG.PreprocessNoTrimmed(yList,uList,vList);
//                elapsedTime = System.currentTimeMillis() - startTime;
//                mTextView.append("\nEnd PreProcess 2 : " + elapsedTime/1000.0);
//
//                noTrimResult = ChaosTrueRNG.Postprocess(noTrimResult);
//                elapsedTime = System.currentTimeMillis() - startTime;
//                mTextView.append("\nEnd PostProcess 2 : " + elapsedTime/1000.0);

                //Save to File
                FileOutputStream fos = null;
                try {
//                    fos = openFileOutput("abc", Context.MODE_PRIVATE);
//                    fos.write(noTrimResult);
//                    fos.close();

                    fos = openFileOutput("abc_post", Context.MODE_PRIVATE);
                    fos.write(result);
                    fos.close();
                    System.out.println("Save File Complete");
                    mTextView.append("\nSaved: ");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            //For <Non lollipop / JPEG
            public void onPictureTaken(byte[] image, int imageFormat, int width, int height) {
                if (imageFormat==ImageFormat.JPEG){
                    Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
                }
            }
        };

        //Instantiate Service
        mCameraService = new CameraService(getApplicationContext(), mPictureTakenCallBack);

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

//        nanoSecondStartTime = System.nanoTime();
//        long takenTime = System.nanoTime() - nanoSecondStartTime;
//        System.out.println("Empty Time = " + takenTime);
//
//        nanoSecondStartTime = System.nanoTime();
//        this.stringFromJava();
//        takenTime = System.nanoTime() - nanoSecondStartTime;
//        System.out.println("Java Time = " + takenTime);
//
//        nanoSecondStartTime = System.nanoTime();
//        this.stringFromJNI();
//        takenTime = System.nanoTime() - nanoSecondStartTime;
//        System.out.println("JNI Time = " + takenTime);
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
