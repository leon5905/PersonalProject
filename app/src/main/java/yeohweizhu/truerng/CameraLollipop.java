package yeohweizhu.truerng;

import android.app.Service;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    private CameraCharacteristics cameraCharacteristics;
    private int picWidth;
    private int picHeight;
    private CameraCaptureSession cameraCaptureSession;
    private TotalCaptureResult captureResult;
    private Image captureImage;
    private SurfaceTexture mDummyPreview = new SurfaceTexture(1);
    private Surface mDummySurface = new Surface(mDummyPreview);
    private int dummyCount;
    private int imageFormat;


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
            cameraCharacteristics = manager.getCameraCharacteristics(backFacingCameraId);

            Size outputSize = getMaximumOutputSizes(cameraCharacteristics);
            picWidth  = outputSize.getWidth(); //TODO change to desired resolution
            picHeight = outputSize.getHeight(); //TODO change to desired resolution

            imageFormat = getOutputFormat(manager.getCameraCharacteristics(backFacingCameraId));

            imageReader = ImageReader.newInstance(picWidth, picHeight, imageFormat, 4);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void takePicture() {
        dummyCount=0;
        captureResult=null;
        captureImage=null;
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
        outputSurfaces.add(mDummySurface);

        try {
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    createPreviewRequest();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createPreviewRequest() {
        try {
            CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(mDummySurface);

            // Focus
            requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

            // Orientation
            int rotation = ((WindowManager) (mContext.getSystemService(Service.WINDOW_SERVICE))).getDefaultDisplay().getRotation();
            requestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            cameraCaptureSession.setRepeatingRequest(requestBuilder.build(),  new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    if (dummyCount==24){
                        createCaptureRequest();
                    }
                    dummyCount++;
                }
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

            cameraCaptureSession.capture(requestBuilder.build(), new CameraCaptureSession.CaptureCallback(){
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, @NonNull TotalCaptureResult result) {
                    captureResult = result;
                    if (captureImage!=null && captureImage.getFormat()==ImageFormat.RAW_SENSOR){
//                        byte[] bytes = obtainDngByte(captureResult,captureImage);
                        mCallback.onPictureTaken(captureImage);
                        captureImage.close();
                    }
                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    //Once Capture Complete
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {

            captureImage = imageReader.acquireLatestImage();
            if (captureImage.getFormat()==ImageFormat.RAW_SENSOR){
                if (captureResult!=null) {
//                    byte[] bytes = obtainDngByte(captureResult,captureImage);
                    mCallback.onPictureTaken(captureImage);
                    captureImage.close();
                    captureImage=null;
                }
            }
            else{
                mCallback.onPictureTaken(captureImage);
                captureImage.close();
                captureImage=null;
            }

            //cameraDevice.close();
            cameraCaptureSession.close(); //TODO maybe 1 capture session no need close??
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private byte[] obtainDngByte(CaptureResult finalResult, Image finalImage){
        DngCreator dngCreator = new DngCreator(cameraCharacteristics, finalResult);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            dngCreator.writeImage(output, finalImage);
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] bytes = output.toByteArray();
        return bytes;
    }


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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static int getOutputFormat(CameraCharacteristics cameraCharacteristics) {
        //TODO remove this
        if (true)
            return ImageFormat.JPEG;

        StreamConfigurationMap streamConfigurationMap = cameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        int[] formatArrays = streamConfigurationMap.getOutputFormats();
        System.out.print("File Format Supported : ");
        for (int i:formatArrays){
            System.out.print(i+ " , ");
        }
        System.out.println();

        for (int i:formatArrays){
            if (i == ImageFormat.RAW_SENSOR){
                return i;
            }
        }

        return ImageFormat.YUV_420_888;
    }
}

