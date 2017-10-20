package yeohweizhu.truerng;

import android.app.Service;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * For API > 5.0 || API > Level 21
 * Created by yeohw on 5/15/2017.
 */

class CameraLollipop implements ICamera {
    private static final String TAG ="CameraLollipop";

    private long nanoSecondStartTime;

    //CameraPreLollipop callback
    private ICamera.PictureTakenCallBack mCallback;
    private Context mContext;

    //Internal
//    private List<ImageReader> imageReaderList;
    private int mRequiredImageNumber = 50; //Burst Number = 300 / 2
    private int mCurrentImageCount=0;
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
    private byte[][][] mCaptureImagePlaneList;
    private SurfaceTexture mDummyPreview = new SurfaceTexture(1);
    private Surface mDummySurface = new Surface(mDummyPreview);
    private int dummyCount;
    private int imageFormat;

    private long startTimer;

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
            picWidth  = outputSize.getWidth();
            picHeight = outputSize.getHeight();

            imageFormat = getOutputFormat(manager.getCameraCharacteristics(backFacingCameraId));



            imageReader= ImageReader.newInstance(picWidth, picHeight, imageFormat, 4);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

//            int burstImageNumber = 1;
//            ImageReader reader;
//            for (int i=0;i<burstImageNumber;++i){
//                reader= ImageReader.newInstance(picWidth, picHeight, imageFormat, 4);
//                reader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
//                imageReaderList.add(reader);
//            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void takePicture() {
        dummyCount=0;
        mCurrentImageCount=0;
        captureResult=null;
        captureImage=null;
        mCaptureImagePlaneList = new byte[mRequiredImageNumber][3][];
        startTimer=System.currentTimeMillis();
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
//        for (ImageReader reader:imageReaderList){
//            outputSurfaces.add(reader.getSurface());
//        }
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
                        createBurstCaptureRequest();
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
//            requestBuilder.addTarget(imageReaderList.get(0).getSurface());
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createBurstCaptureRequest(){
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

            List<CaptureRequest> captureRequestList = new ArrayList<>();

            for (int i = 0; i< mRequiredImageNumber; ++i){
                captureRequestList.add(requestBuilder.build());
            }

            cameraCaptureSession.captureBurst(captureRequestList, null, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //Once Capture Complete
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            System.out.println("Time Taken : " + ((System.currentTimeMillis()-startTimer)/1000.0) );
            startTimer= System.currentTimeMillis();

            captureImage = imageReader.acquireNextImage();
            Image.Plane[] planeArray = captureImage.getPlanes();

            ByteBuffer buffer = planeArray[0].getBuffer();
            byte[] y = new byte[buffer.remaining()];
            buffer.get(y);

            buffer = planeArray[1].getBuffer();
            byte[] u = new byte[buffer.remaining()];
            buffer.get(u);

            buffer = planeArray[2].getBuffer();
            byte[] v = new byte[buffer.remaining()];
            buffer.get(v);

            mCaptureImagePlaneList[mCurrentImageCount][0] = y;
            mCaptureImagePlaneList[mCurrentImageCount][1] = u;
            mCaptureImagePlaneList[mCurrentImageCount][2] = v;
            ++mCurrentImageCount;
            captureImage.close(); //Allow more image to be captured
            captureImage = null;

            if (mCurrentImageCount== mRequiredImageNumber){
                cameraDevice.close();
                mCallback.onPictureTaken(mCaptureImagePlaneList);
                cameraCaptureSession.close();
            }
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
                int size1Value = size1.getHeight()*size1.getWidth();
                int size2Value = size2.getHeight()*size2.getWidth();

                //In case they are equal, -1 will still be return, not so much of a deal in this case.
                if (size1Value>size2Value){
                    return 1;
                }
                else{
                    return -1;
                }
            }
        });

        ListIterator<Size> ltr = availableResolutions.listIterator();
        System.out.print("Resolution Available : " );
        while(ltr.hasNext()){
            System.out.print(ltr.next() +" ");
        }
        System.out.println("Maximum Resolution: H - " + maxRes.getHeight() + " , W - " +maxRes.getWidth());

        return maxRes;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static int getOutputFormat(CameraCharacteristics cameraCharacteristics) {
        if (true) //Android promise to always support YUV_420_888
            return ImageFormat.YUV_420_888;

        StreamConfigurationMap streamConfigurationMap = cameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        int[] formatArrays = streamConfigurationMap.getOutputFormats();

        for (int i:formatArrays){
            if (i == ImageFormat.YUV_420_888){
                return i;
            }
        }

        return ImageFormat.JPEG;
    }
}

