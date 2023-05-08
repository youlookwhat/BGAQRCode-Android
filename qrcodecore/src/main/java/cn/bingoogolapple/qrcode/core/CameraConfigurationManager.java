package cn.bingoogolapple.qrcode.core;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.util.Collection;
import java.util.List;

final class CameraConfigurationManager {
    private final Context mContext;
    private Point mCameraResolution;
    private Point mPreviewResolution;
    /// 保存图片的时候用
    private int displayOrientation;
    private Camera.Parameters mParameters;

    CameraConfigurationManager(Context context) {
        mContext = context;
    }

    void initFromCameraParameters(Camera camera, SurfaceView surfaceView) {
        Point screenResolution = BGAQRCodeUtil.getScreenResolution(mContext);
        Point screenResolutionForCamera = new Point();
        screenResolutionForCamera.x = screenResolution.x;
        screenResolutionForCamera.y = screenResolution.y;

        if (BGAQRCodeUtil.isPortrait(mContext)) {
            screenResolutionForCamera.x = screenResolution.y;
            screenResolutionForCamera.y = screenResolution.x;
        }

//        mPreviewResolution = getPreviewResolution(camera.getParameters(), screenResolutionForCamera);
        initParameters(camera, surfaceView);

        if (BGAQRCodeUtil.isPortrait(mContext)) {
            mCameraResolution = new Point(mPreviewResolution.y, mPreviewResolution.x);
        } else {
            mCameraResolution = mPreviewResolution;
        }
    }

    private static boolean autoFocusAble(Camera camera) {
        List<String> supportedFocusModes = camera.getParameters().getSupportedFocusModes();
        String focusMode = findSettableValue(supportedFocusModes, Camera.Parameters.FOCUS_MODE_AUTO);
        return focusMode != null;
    }

    Point getCameraResolution() {
        return mCameraResolution;
    }


    //----------------处理预览和拍照时，图片尺寸问题---------↓↓↓↓↓↓----------------------------------------------
    private boolean isSupportFocus(String focusMode) {
        for (String mode : mParameters.getSupportedFocusModes()) {
            if (focusMode.equals(mode)) {
                return true;
            }
        }
        return false;
    }

    private void initParameters(final Camera camera, SurfaceView mSurfaceView) {
        mParameters = camera.getParameters();
        mParameters.setPreviewFormat(ImageFormat.NV21); //default
//        mParameters.getSupportedPreviewFormats();
//        mParameters.getSupportedPictureFormats();

        if (isSupportFocus(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (isSupportFocus(Camera.Parameters.FOCUS_MODE_AUTO)) {
            mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        List<Camera.Size> sizes1 = mParameters.getSupportedPreviewSizes();
        int[] result1 = getOptimalSize(sizes1, mSurfaceView.getWidth(), mSurfaceView.getHeight());
        mPreviewResolution = new Point(result1[0], result1[1]);
        this.mParameters.setPreviewSize(result1[0], result1[1]);
        List<Camera.Size> sizes2 = this.mParameters.getSupportedPictureSizes();
        int[] result2 = this.getOptimalSize(sizes2, mSurfaceView.getWidth(), mSurfaceView.getHeight());
        this.mParameters.setPictureSize(result2[0], result2[1]);
        camera.setParameters(this.mParameters);

//        camera.setParameters(mParameters);
    }

    private int[] getOptimalSize(List<Camera.Size> sizes, int currentWidth, int currentHeight) {
        int i = 1;
        int bestWidth = ((Camera.Size) sizes.get(0)).width;
        int bestHeight = ((Camera.Size) sizes.get(0)).height;

        for (float min = Math.abs((float) bestHeight / (float) bestWidth - (float) currentWidth / (float) currentHeight); i < sizes.size(); ++i) {
            float current = Math.abs((float) ((Camera.Size) sizes.get(i)).height / (float) ((Camera.Size) sizes.get(i)).width - (float) currentWidth / (float) currentHeight);
            if (current < min) {
                min = current;
                bestWidth = ((Camera.Size) sizes.get(i)).width;
                bestHeight = ((Camera.Size) sizes.get(i)).height;
            }
        }

        int[] result = new int[]{bestWidth, bestHeight};
//        DebugUtil.error("glcamera", bestWidth + "//" + bestHeight);
        return result;
    }
    //----------------处理预览和拍照时，图片尺寸问题----------↑↑↑↑↑↑---------------------------------------------

    void setDesiredCameraParameters(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(mPreviewResolution.x, mPreviewResolution.y);

        // https://github.com/googlesamples/android-vision/blob/master/visionSamples/barcode-reader/app/src/main/java/com/google/android/gms/samples/vision/barcodereader/ui/camera/CameraSource.java
        int[] previewFpsRange = selectPreviewFpsRange(camera, 60.0f);
        if (previewFpsRange != null) {
            parameters.setPreviewFpsRange(
                    previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                    previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
        }

        displayOrientation = getDisplayOrientation();
        camera.setDisplayOrientation(displayOrientation);
        camera.setParameters(parameters);
    }

    public int getOrientation() {
        return displayOrientation;
    }

    /**
     * Selects the most suitable preview frames per second range, given the desired frames per
     * second.
     *
     * @param camera            the camera to select a frames per second range from
     * @param desiredPreviewFps the desired frames per second for the camera preview frames
     * @return the selected preview frames per second range
     */
    private int[] selectPreviewFpsRange(Camera camera, float desiredPreviewFps) {
        // The camera API uses integers scaled by a factor of 1000 instead of floating-point frame
        // rates.
        int desiredPreviewFpsScaled = (int) (desiredPreviewFps * 1000.0f);

        // The method for selecting the best range is to minimize the sum of the differences between
        // the desired value and the upper and lower bounds of the range.  This may select a range
        // that the desired value is outside of, but this is often preferred.  For example, if the
        // desired frame rate is 29.97, the range (30, 30) is probably more desirable than the
        // range (15, 30).
        int[] selectedFpsRange = null;
        int minDiff = Integer.MAX_VALUE;
        List<int[]> previewFpsRangeList = camera.getParameters().getSupportedPreviewFpsRange();
        for (int[] range : previewFpsRangeList) {
            int deltaMin = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
            int deltaMax = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
            int diff = Math.abs(deltaMin) + Math.abs(deltaMax);
            if (diff < minDiff) {
                selectedFpsRange = range;
                minDiff = diff;
            }
        }
        return selectedFpsRange;
    }

    void openFlashlight(Camera camera) {
        doSetTorch(camera, true);
    }

    void closeFlashlight(Camera camera) {
        doSetTorch(camera, false);
    }

    private void doSetTorch(Camera camera, boolean newSetting) {
        Camera.Parameters parameters = camera.getParameters();
        String flashMode;
        /** 是否支持闪光灯 */
        if (newSetting) {
            flashMode = findSettableValue(parameters.getSupportedFlashModes(), Camera.Parameters.FLASH_MODE_TORCH, Camera.Parameters.FLASH_MODE_ON);
        } else {
            flashMode = findSettableValue(parameters.getSupportedFlashModes(), Camera.Parameters.FLASH_MODE_OFF);
        }
        if (flashMode != null) {
            parameters.setFlashMode(flashMode);
        }
        camera.setParameters(parameters);
    }

    private static String findSettableValue(Collection<String> supportedValues, String... desiredValues) {
        String result = null;
        if (supportedValues != null) {
            for (String desiredValue : desiredValues) {
                if (supportedValues.contains(desiredValue)) {
                    result = desiredValue;
                    break;
                }
            }
        }
        return result;
    }

    private int getDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return 0;
        }
        Display display = wm.getDefaultDisplay();

        int rotation = display.getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    private static Point getPreviewResolution(Camera.Parameters parameters, Point screenResolution) {
        Point previewResolution =
                findBestPreviewSizeValue(parameters.getSupportedPreviewSizes(), screenResolution);
        if (previewResolution == null) {
            previewResolution = new Point((screenResolution.x >> 3) << 3, (screenResolution.y >> 3) << 3);
        }
        return previewResolution;
    }

    private static Point findBestPreviewSizeValue(List<Camera.Size> supportSizeList, Point screenResolution) {
        int bestX = 0;
        int bestY = 0;
        int diff = Integer.MAX_VALUE;
        for (Camera.Size previewSize : supportSizeList) {

            int newX = previewSize.width;
            int newY = previewSize.height;

            int newDiff = Math.abs(newX - screenResolution.x) + Math.abs(newY - screenResolution.y);
            if (newDiff == 0) {
                bestX = newX;
                bestY = newY;
                break;
            } else if (newDiff < diff) {
                bestX = newX;
                bestY = newY;
                diff = newDiff;
            }

        }

        if (bestX > 0 && bestY > 0) {
            return new Point(bestX, bestY);
        }
        return null;
    }
}