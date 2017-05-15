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
import android.support.annotation.RequiresApi;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by yeohw on 5/15/2017.
 */

public class CameraLollipop implements ICamera {
    private static final String TAG ="CameraLollipop";

    private static final int DEFAULT_WIDHT = 1280;
    private static final int DEFAULT_HEIGHT = 720;

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
    public static CameraLollipop createInstance(Context context, ICamera.PictureTakenCallBack callback){
        return new CameraLollipop(context,callback);
    }

    private CameraLollipop() throws IllegalAccessException{
        throw new IllegalAccessException();
    };

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

            imageReader = ImageReader.newInstance(picWidth, picHeight, ImageFormat.JPEG, 2);
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
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            manager.openCamera(cameraId, cameraStateCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        catch (SecurityException e){
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice device) {
            cameraDevice = device;
            createCameraCaptureSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {}

        @Override
        public void onError(CameraDevice cameraDevice, int error) {}
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCameraCaptureSession() {
        List<Surface> outputSurfaces = new LinkedList<>();
        outputSurfaces.add(imageReader.getSurface());

        try {

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    createCaptureRequest();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {}
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
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

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
            Image image = imageReader.acquireLatestImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            //TODO try to get pixel
            mCallback.onPictureTaken(bytes);

            image.close();
        }
    };


    //Get Maximum Supported Camera Sensor Capture Resolution
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static Size getMaximumOutputSizes(CameraCharacteristics cameraCharacteristics) {
        StreamConfigurationMap streamConfigurationMap = cameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] availableResolutionsArray;
        availableResolutionsArray = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
        List<Size> availableResolutions = Arrays.asList(availableResolutionsArray);

        //Return largest resolution
        return availableResolutions.get(availableResolutionsArray.length-1);
    }


}

