package com.techshino.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.serenegiant.common.BaseFragment;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.UVCCameraTextureView;
import com.techshino.config.DualFaceConfig;
import com.techshino.facespoof.Algorithm;
import com.techshino.usbcameradual.R;
import com.techshino.utils.FileUtils;
import com.techshino.utils.Logs;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * 双模人脸检活
 * <p>
 * Created by wangzhi on 2017/8/7.
 */
public class DualCameraFragment extends BaseFragment {

  private static final boolean DEBUG = true;  // FIXME set false when production
  private static final String TAG = DualCameraFragment.class.getSimpleName();
  private final static Object mSync = new Object();

  private static String DEFAULT_CONFIG = "<param>\n"
      + "<imgWidth>640</imgWidth>\n"//
      + "<imgHeight>480</imgHeight>\n"//
      + "<imgCompress>85</imgCompress>\n"//
      + "<NirCount>3</NirCount>\n"
      + "<isActived>2</isActived>\n"
      + "<timeOut>30</timeOut>\n"
      + "<liveThreshold>0.7</liveThreshold>\n"
      + "<pidL>2208</pidL>\n"
      + "<pidR>2207</pidR>\n"
      + "</param>";

  private static final float[] BANDWIDTH_FACTORS = {0.5f, 0.5f};
  private static final int TECH_VID = 0x735F;

  // for accessing USB and USB camera
  private USBMonitor mUSBMonitor;

  private TextView mHintTv;
  private ImageView mFace1Img;
  private ImageView mFace2Img;

  private UVCCamera mUVCCameraR;
  private UVCCameraTextureView mUVCCameraViewR;
  private Surface mRightPreviewSurface;

  private UVCCamera mUVCCameraL;
  private UVCCameraTextureView mUVCCameraViewL;
  private Surface mLeftPreviewSurface;

  private boolean isDetecting = false;
  private DualFaceConfig mDualFaceConfig;
  private long mDetectTime;
  private boolean isLive = false;
  private UsbControlBlock mLeftControlBlock;
  private UsbControlBlock mRightControlBlock;

  Algorithm mAlgorithm;
  byte[] mTempJpgBytes = null;
  String mEditParams;

  int index = 0;
  int leftSuc = -1;
  int rightSuc = -1;
  boolean leftFrame = false;
  boolean rightFrame = false;

  CameraCallback mCameraCallback;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mAlgorithm = Algorithm.getInstance(getActivity());
    mEditParams = DEFAULT_CONFIG;

    // 初始化检活配置
    mDualFaceConfig = new DualFaceConfig();
    boolean isOk = mDualFaceConfig.parseXML(mEditParams);
//    UVCCamera.DEFAULT_PREVIEW_WIDTH = 1920;
//    UVCCamera.DEFAULT_PREVIEW_HEIGHT = 1080;

    mTempJpgBytes = new byte[UVCCamera.DEFAULT_PREVIEW_WIDTH * UVCCamera.DEFAULT_PREVIEW_HEIGHT * 3];
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.fragment_dual_camera, container, false);

    mUVCCameraViewL = (UVCCameraTextureView) rootView.findViewById(R.id.camera_view_L);
    mUVCCameraViewL.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);
    mUVCCameraViewL.setOnClickListener(mOnClickListener);

    mUVCCameraViewR = (UVCCameraTextureView) rootView.findViewById(R.id.camera_view_R);
    mUVCCameraViewR.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);
    mUVCCameraViewR.setOnClickListener(mOnClickListener);

    mHintTv = (TextView) rootView.findViewById(R.id.hintTv);
    mFace1Img = (ImageView) rootView.findViewById(R.id.face1Img);
    mFace2Img = (ImageView) rootView.findViewById(R.id.face2Img);
    rootView.findViewById(R.id.flipImg).setOnClickListener(mOnClickListener);

    mUSBMonitor = new USBMonitor(getActivity(), mOnDeviceConnectListener);

    return rootView;
  }

  @Override
  public void onStart() {
    super.onStart();
    mUSBMonitor.register();
    if (mUVCCameraViewR != null)
      mUVCCameraViewR.onResume();
    if (mUVCCameraViewL != null)
      mUVCCameraViewL.onResume();
  }

  @Override
  public void onStop() {
    if (mUVCCameraViewR != null)
      mUVCCameraViewR.onPause();
    if (mUVCCameraViewL != null)
      mUVCCameraViewL.onPause();
    mUSBMonitor.unregister();
    super.onStop();
  }

  @Override
  public void onDestroy() {
    stopCamera();
    if (mUSBMonitor != null) {
      mUSBMonitor.destroy();
      mUSBMonitor = null;
    }
    mUVCCameraViewR = null;
    mUVCCameraViewL = null;
    super.onDestroy();
  }

  public void startCamera() {
    if (mUSBMonitor == null)
      return;
    List<UsbDevice> devices = mUSBMonitor.getDeviceList();
    if (devices == null)
      return;
    for (UsbDevice device : devices) {
      if (device.getProductId() == mDualFaceConfig.getPidL()) {
        if (mLeftControlBlock == null) {
          mUSBMonitor.requestPermission(device);
        } else {
          openCameraDevice(device, mLeftControlBlock);
        }
      }

      if (device.getProductId() == mDualFaceConfig.getPidR()) {
        if (mRightControlBlock == null) {
          mUSBMonitor.requestPermission(device);
        } else {
          openCameraDevice(device, mRightControlBlock);
        }
      }
    }
  }

  public void stopCamera() {
    mFace1Img.setImageBitmap(null);
    mFace2Img.setImageBitmap(null);
    synchronized (mSync) {
      if (mUVCCameraL != null) {
        try {
          mUVCCameraL.setStatusCallback(null);
          mUVCCameraL.setButtonCallback(null);
          mUVCCameraL.close();
          mUVCCameraL.destroy();
        } catch (final Exception e) {
          //
        }
        mUVCCameraL = null;
        leftFrame = false;
        leftSuc = -1;
      }
      if (mLeftPreviewSurface != null) {
        mLeftPreviewSurface.release();
        mLeftPreviewSurface = null;
      }
    }

    synchronized (mSync) {
      if (mUVCCameraR != null) {
        try {
          mUVCCameraR.setStatusCallback(null);
          mUVCCameraR.setButtonCallback(null);
          mUVCCameraR.close();
          mUVCCameraR.destroy();
        } catch (final Exception e) {
          //
        }
        mUVCCameraR = null;
        rightFrame = false;
        rightSuc = -1;
      }
      if (mRightPreviewSurface != null) {
        mRightPreviewSurface.release();
        mRightPreviewSurface = null;
      }
    }
  }

  public void startDetect() {
    mFace1Img.setImageBitmap(null);
    mFace2Img.setImageBitmap(null);
    isLive = false;
    isDetecting = true;
    isRigthOK = false;
    isLeftOK = false;
    index = 0;
    mDetectTime = System.currentTimeMillis();
    mHintTv.setText("开始检测...");
  }

  public void stopDetect() {
    mFace1Img.setImageBitmap(null);
    mFace2Img.setImageBitmap(null);
    isDetecting = false;
    mHintTv.setText("停止检测...");
  }

  private void openCameraDevice(UsbDevice device, final UsbControlBlock ctrlBlock) {
    Log.i(TAG, "openCameraDevice... pid:" + device.getProductId());
    if (device.getVendorId() == TECH_VID
        && device.getProductId() == mDualFaceConfig.getPidL()) {
      if (mUVCCameraL == null) {
        new Thread(() -> {
          final UVCCamera camera = new UVCCamera();
          camera.open(ctrlBlock);
          if (mLeftControlBlock == null)
            mLeftControlBlock = ctrlBlock;

          Log.d(TAG, "camera.open:" + ctrlBlock);
          try {
            camera.setPreviewSize(
                UVCCamera.DEFAULT_PREVIEW_WIDTH,
                UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                UVCCamera.FRAME_FORMAT_MJPEG, BANDWIDTH_FACTORS[0]);
          } catch (final IllegalArgumentException e) {
            // fallback to YUV mode
            try {
              camera.setPreviewSize(
                  UVCCamera.DEFAULT_PREVIEW_WIDTH,
                  UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                  UVCCamera.DEFAULT_PREVIEW_MODE);
            } catch (final IllegalArgumentException e1) {
              camera.destroy();
              return;
            }
          }

          final SurfaceTexture st = mUVCCameraViewL.getSurfaceTexture();
          if (st != null) {
            mLeftPreviewSurface = new Surface(st);
            camera.setPreviewDisplay(mLeftPreviewSurface);
            camera.setFrameCallback(leftCallback,
                UVCCamera.PIXEL_FORMAT_YUV420SP/*
                 * UVCCamera.
                 * PIXEL_FORMAT_NV21
                 */);
            leftFrame = true;
            camera.startPreview();
          }
          synchronized (mSync) {
            mUVCCameraL = camera;
          }
        }).start();
      }
    }
    if (device.getVendorId() == TECH_VID
        && device.getProductId() == mDualFaceConfig.getPidR()) {
      if (mUVCCameraR == null) {
        new Thread(() -> {
          final UVCCamera camera = new UVCCamera();
          camera.open(ctrlBlock);
          if (mRightControlBlock == null) {
            mRightControlBlock = ctrlBlock;
          }

          Log.d(TAG, "camera.open:" + ctrlBlock);
          try {
            camera.setPreviewSize(
                UVCCamera.DEFAULT_PREVIEW_WIDTH,
                UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                UVCCamera.FRAME_FORMAT_MJPEG, BANDWIDTH_FACTORS[1]);
          } catch (final IllegalArgumentException e) {
            // fallback to YUV mode
            try {
              camera.setPreviewSize(
                  UVCCamera.DEFAULT_PREVIEW_WIDTH,
                  UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                  UVCCamera.DEFAULT_PREVIEW_MODE);
            } catch (final IllegalArgumentException e1) {
              camera.destroy();
              return;
            }
          }

          final SurfaceTexture st = mUVCCameraViewR.getSurfaceTexture();
          if (st != null) {
            mRightPreviewSurface = new Surface(st);
            camera.setPreviewDisplay(mRightPreviewSurface);
            camera.setFrameCallback(rightCallback,
                UVCCamera.PIXEL_FORMAT_YUV420SP/*
                 * UVCCamera.
                 * PIXEL_FORMAT_NV21
                 */);
            rightFrame = true;
            camera.startPreview();
          }
          synchronized (mSync) {
            mUVCCameraR = camera;
//              callOnOpen(3000);
          }
        }).start();
      }
    }
  }

  private void callOnOpen(long timeout) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
//        mHintTv.setText("打开次数：" + (++index) + " 左边：" + leftSuc + " 右边：" + rightSuc);
//        stopCamera();
//        callOnClose();

        if (mCameraCallback == null) {
          return;
        }
        if (mUVCCameraL == null || mUVCCameraR == null) {
          return;
        }

        if (leftSuc == 0 && rightSuc == 0) {
          mCameraCallback.onPreviewSuccess();
        } else {
          mCameraCallback.onPreviewError();
        }
      }
    }, timeout);
  }

  private void callOnClose() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
//        startCamera();
      }
    }, 1500);
  }

  public void startSetting() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    LinearLayout linearLayout = (LinearLayout) getActivity().getLayoutInflater().inflate(
        R.layout.dialog_layout_params, null);
    final EditText editText = (EditText) linearLayout
        .findViewById(R.id.edtParamsInfo);
    editText.setText(mEditParams);
    editText.setFocusable(true);
    editText.setFocusableInTouchMode(true);
    editText.requestFocus();
    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.showSoftInput(editText, InputMethodManager.RESULT_SHOWN);
    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,
        InputMethodManager.HIDE_IMPLICIT_ONLY);

    builder.setView(linearLayout);
    builder.setTitle("参数设置");
    builder.setPositiveButton("确定", (arg0, arg1) -> {
      mEditParams = editText.getText().toString();
      boolean isOk = mDualFaceConfig.parseXML(mEditParams);
      Logs.i(TAG, mDualFaceConfig.toString());
      if (!isOk) {
        Toast.makeText(getActivity(), mDualFaceConfig.getMessage(), Toast.LENGTH_SHORT).show();
      }
      InputMethodManager imm1 = (InputMethodManager) getActivity()
          .getSystemService(Context.INPUT_METHOD_SERVICE);
      imm1.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    });
    builder.setNegativeButton("取消", (arg0, arg1) -> {
      InputMethodManager imm12 = (InputMethodManager) getActivity()
          .getSystemService(Context.INPUT_METHOD_SERVICE);
      imm12.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    });
    builder.show();
  }

  private final View.OnClickListener mOnClickListener = view -> {
    switch (view.getId()) {
      case R.id.flipImg:
        UVCCameraTextureView.isMirror = UVCCameraTextureView.isMirror ? false : true;
        break;
      default:
        break;
    }
  };

  private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
    @Override
    public void onAttach(final UsbDevice device) {
//      Logs.v(TAG, "onAttach:" + device);
      mUSBMonitor.requestPermission(device);
    }

    @Override
    public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
//      Logs.v(TAG, "onConnect:" + device);
      openCameraDevice(device, ctrlBlock);
    }

    @Override
    public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
//      if (DEBUG) Log.v(TAG, "onDisconnect:" + device);

    }

    @Override
    public void onDettach(final UsbDevice device) {
      Toast.makeText(getActivity(), "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
      if (device.getProductId() == mDualFaceConfig.getPidL()) {
        mLeftControlBlock = null;
      }
      if (device.getProductId() == mDualFaceConfig.getPidR()) {
        mRightControlBlock = null;
      }
    }

    @Override
    public void onCancel(final UsbDevice device) {
      if (DEBUG) Log.v(TAG, "onCancel:");
    }
  };

  int width = 640;
  int height = 480;


  /**
   * 近红外检测回调
   */
  IFrameCallback leftCallback = new IFrameCallback() {

    @Override
    public void onFrame(ByteBuffer frame) {
      if (leftFrame) {
        leftSuc++;
        leftFrame = false;
      }
      byte[] yuv = null;
      if (frame.limit() > 0) {
        yuv = new byte[frame.limit()];
        frame.get(yuv);
//        mYuvBytesR = yuv;
      }

      if (yuv == null) {
        return;
      }
      if (!isDetecting) {
        return;
      }

      long start = System.currentTimeMillis();
      byte[] rgb24 = new byte[width * height * 3];

      mAlgorithm.RgbFromYuv420SP(rgb24, yuv, width, height);
      Logs.d(TAG, "Yuv转换时间：" + (System.currentTimeMillis() - start) + "ms");

      //saveRgb24ToDisk(rgb24);

      float[] score = new float[1];
      double[] feature = new double[20];
      int[] faceRect = new int[4];
      int status = mAlgorithm.nir(rgb24, 0, width, height, faceRect, score, feature);
      Logs.i(TAG, "x:" + faceRect[0] + " y:" + faceRect[1] + " w:" + faceRect[2] + " h:" + faceRect[3]);
      Logs.i(TAG, "x:" + feature[0] + " y:" + feature[1] + " w:" + feature[2] + " h:" + feature[3] + " score:" + feature[4]);
      Logs.d(TAG, "检活时间：" + (System.currentTimeMillis() - start) + "ms");
      Logs.d(TAG, "status:" + status + " score:" + score[0]);


      if (score[0] > mDualFaceConfig.getThreshold()) {
        index++;
      }

      if (mDualFaceConfig.getIsActived() == 0) {
        index = mDualFaceConfig.getNirCount();
        isLive = true;
      } else if (mDualFaceConfig.getIsActived() == 1) {
        isLive = true;
      }

      if (System.currentTimeMillis() - mDetectTime > mDualFaceConfig.getTimeout()) {
        isDetecting = false;
        runOnUiThread(() -> mHintTv.setText("检测超时，请重新检测..."), 0);
      }

      if (isLeftOK && isRigthOK) {
        isDetecting = false;
      }

      if (index >= mDualFaceConfig.getNirCount() && isLive && !isLeftOK) {
        isLeftOK = true;
        String filePath = getActivity().getExternalCacheDir() + "/TempFace0.jpg";

        int jpgSize = mAlgorithm.Rgb2Jpg(mTempJpgBytes, rgb24, width, height, mDualFaceConfig.getImgCompress());
        Logs.d(TAG, "rgb to jpg时间：" + (System.currentTimeMillis() - start) + "ms");
        byte[] jpgBytes = new byte[jpgSize];
        System.arraycopy(mTempJpgBytes, 0, jpgBytes, 0, jpgSize);

        final Bitmap bitmap = BitmapFactory.decodeByteArray(jpgBytes, 0, jpgSize);
        runOnUiThread(() -> {
          mFace1Img.setImageBitmap(bitmap);
          mHintTv.setText("检活成功...");
        }, 0);
        FileUtils.writeFile(filePath, jpgBytes);
      }
    }
  };

  int faceNum = 0;

  private void saveRgb24ToDisk(byte[] rgb24) {
    String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "faceNir" + (faceNum++) + ".jpg";

    int jpgSize = mAlgorithm.Rgb2Jpg(mTempJpgBytes, rgb24, width, height, 100);
    byte[] jpgBytes = new byte[jpgSize];
    System.arraycopy(mTempJpgBytes, 0, jpgBytes, 0, jpgSize);

    FileUtils.writeFile(filePath, jpgBytes);
  }

  boolean isLeftOK = false;
  boolean isRigthOK = false;

  /**
   * 可见光检测回调
   */
  IFrameCallback rightCallback = new IFrameCallback() {

    @Override
    public void onFrame(final ByteBuffer frame) {
      if (rightFrame) {
        rightSuc++;
        rightFrame = false;
      }
      byte[] yuv = null;
      if (frame.limit() > 0) {
        yuv = new byte[frame.limit()];
        frame.get(yuv);
//        mYuvBytesR = yuv;
      }

      if (yuv == null) {
        return;
      }
      if (!isDetecting) {
        return;
      }

      long start = System.currentTimeMillis();
      byte[] rgb24 = new byte[width * height * 3];

      mAlgorithm.RgbFromYuv420SP(rgb24, yuv, width, height);
      Logs.d(TAG, "Yuv转换时间：" + (System.currentTimeMillis() - start) + "ms");

      if (mDualFaceConfig.getIsActived() == 2) {
        int status = mAlgorithm.colorSimple(rgb24, width, height, new int[4], new int[0], new double[20]);
        if (status == 0)
          isLive = true;
      } else if (mDualFaceConfig.getIsActived() == 3) {
        int status = mAlgorithm.colorNormal(rgb24, width, height, new int[4], new float[0], new double[20]);
        if (status == 0)
          isLive = true;
      }

      if (isLeftOK && isRigthOK) {
        isDetecting = false;
      }

      if (index >= mDualFaceConfig.getNirCount() && isLive && !isRigthOK) {
        isRigthOK = true;
        String filePath = getActivity().getExternalCacheDir() + "/TempFace1.jpg";

        int jpgSize = mAlgorithm.Rgb2Jpg(mTempJpgBytes, rgb24, width, height, mDualFaceConfig.getImgCompress());
        Logs.d(TAG, "rgb to jpg时间：" + (System.currentTimeMillis() - start) + "ms");
        byte[] jpgBytes = new byte[jpgSize];
        System.arraycopy(mTempJpgBytes, 0, jpgBytes, 0, jpgSize);

        final Bitmap bitmap = BitmapFactory.decodeByteArray(jpgBytes, 0, jpgSize);
        runOnUiThread(() -> mFace2Img.setImageBitmap(bitmap), 0);
        FileUtils.writeFile(filePath, jpgBytes);
      }
    }
  };

  public void setCameraCallback(CameraCallback cameraCallback) {
    mCameraCallback = cameraCallback;
  }

  public interface CameraCallback {

    void onPreviewError();

    void onPreviewSuccess();
  }
}
