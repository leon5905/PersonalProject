package yeohweizhu.truerng;

import android.app.Service;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * For API < 5.0 || API < Level 21
 * Created by yeohw on 5/15/2017.
 */

class CameraLollipop implements ICamera {
    private static final String TAG ="CameraLollipop";

//    private static final int DEFAULT_WIDHT = 1280;
//    private static final int DEFAULT_HEIGHT = 720;

    private long nanoSecondStartTime;

    //CameraPreLollipop callback
    private ICamera.PictureTakenCallBack mCallback;
    private Context mContext;

    //Internal
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static CameraLollipop createInstance(Context context, ICamera.PictureTakenCallBack callback){
        return new CameraLollipop(context,callback);
    }

    private CameraLollipop() throws IllegalAccessException{
        throw new IllegalAccessException();
    };

    //Constructor
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private CameraLollipop(Context context, ICamera.PictureTakenCallBack callback) {
        mContext = context;
        mCallback = callback;

        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

        try {
            String[] cameraIdList = manager.getCameraIdList();

            String backFacingCameraId = cameraIdList[0];
            for (String cameraId : cameraIdList){
                if (manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK){
                    backFacingCameraId = cameraId;
                    break;
                }
            }

            cameraId= backFacingCameraId;

            Size outputSize = getMaximumOutputSizes(manager.getCameraCharacteristics(backFacingCameraId));
            int picWidth  = outputSize.getWidth(); //TODO change to desired resolution
            int picHeight = outputSize.getHeight(); //TODO change to desired resolution

            imageReader = ImageReader.newInstance(picWidth, picHeight, ImageFormat.RAW_SENSOR, 2);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void takePicture() {
        openCamera();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void openCamera() {
        nanoSecondStartTime = System.nanoTime();

        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            manager.openCamera(cameraId, cameraStateCallback, backgroundHandler);

        } catch (CameraAccessException | SecurityException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            cameraDevice = device;
            createCameraCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {}

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {}
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCameraCaptureSession() {
        List<Surface> outputSurfaces = new LinkedList<>();
        outputSurfaces.add(imageReader.getSurface());

        try {
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    createCaptureRequest();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCaptureRequest() {
        try {
            CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            requestBuilder.addTarget(imageReader.getSurface());

            // Focus
            requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
//            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
//            requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO);

            // Orientation
            int rotation = ((WindowManager) (mContext.getSystemService(Service.WINDOW_SERVICE))).getDefaultDisplay().getRotation();
            requestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            cameraCaptureSession.capture(requestBuilder.build(), null, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    //Once Capture Complete
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            long elapsedTime = System.nanoTime() - nanoSecondStartTime;
            System.out.println("Nanosecond Taken: " + elapsedTime);

            Image image = imageReader.acquireLatestImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            //TODO try to get pixel
            System.out.println("Byte Array Length : " + bytes.length);

            mCallback.onPictureTaken(bytes, ImageFormat.JPEG); //TODO change image format

            image.close();
            //cameraDevice.close();
            cameraCaptureSession.close(); //TODO maybe 1 capture session no need close??
        }
    };


    //Get Maximum Supported Camera Sensor Capture Resolution
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static Size getMaximumOutputSizes(CameraCharacteristics cameraCharacteristics) {
        StreamConfigurationMap streamConfigurationMap = cameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        int[] formatArrays = streamConfigurationMap.getOutputFormats();
        System.out.print("File Format Supported : ");
        for (int i:formatArrays){
            System.out.print(i+ " , ");
        }
        System.out.println();

        Size[] availableResolutionsArray;
        availableResolutionsArray = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
        List<Size> availableResolutions = Arrays.asList(availableResolutionsArray);

        Size maxRes  = Collections.max(availableResolutions,new Comparator<Size>(){

            @Override
            public int compare(Size size1, Size size2) {
                int size1Value = size1.getHeight()+ size1.getWidth();
                int size2Value = size2.getHeight()+size2.getWidth();

                //In case they are equal, -1 will still be return, not so much of a deal in this case.
                if (size1Value>size2Value){
                    return 1;
                }
                else{
                    return -1;
                }
            }
        });

        System.out.println("Maximum Resolution: H - " + maxRes.getHeight() + " , W - " +maxRes.getWidth());

        return maxRes;
    }


}

