package com.techshino.usbcameradual;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.techshino.fragment.DualCameraFragment;
import com.techshino.utils.Logs;

/**
 * 双模人脸检活
 * <p>
 * Created by wangzhi on 2017/8/2.
 */
public final class DualCameraActivity extends AppCompatActivity {

  private static final String TAG = DualCameraActivity.class.getSimpleName();

  private DualCameraFragment mDualCameraFragment;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_dual_camera);

    mDualCameraFragment = (DualCameraFragment) getFragmentManager().findFragmentById(R.id.dualFaceFragment);

    findViewById(R.id.startCameraBtn).setOnClickListener(mOnClickListener);
    findViewById(R.id.stopCameraBtn).setOnClickListener(mOnClickListener);
    findViewById(R.id.startDetectBtn).setOnClickListener(mOnClickListener);
    findViewById(R.id.stopDetectBtn).setOnClickListener(mOnClickListener);
    findViewById(R.id.exitBtn).setOnClickListener(mOnClickListener);
    findViewById(R.id.settingBtn).setOnClickListener(mOnClickListener);

    mDualCameraFragment.setCameraCallback(new MyCameraCallback());
  }

  private final View.OnClickListener mOnClickListener = view -> {
    switch (view.getId()) {
      case R.id.startCameraBtn:
        startCamera();
        break;
      case R.id.stopCameraBtn:
        stopCamera();
        break;
      case R.id.startDetectBtn:
        startDetect();
        break;
      case R.id.stopDetectBtn:
        stopDetect();
        break;
      case R.id.settingBtn:
        startSetting();
        break;
      case R.id.exitBtn:
        finish();
        break;
      default:
        break;
    }
  };

  private void startCamera() {
    mDualCameraFragment.startCamera();
  }

  private void stopCamera() {
    mDualCameraFragment.stopCamera();
  }

  private void startDetect() {
    mDualCameraFragment.startDetect();
  }

  private void stopDetect() {
    mDualCameraFragment.stopDetect();
  }

  private void startSetting() {
    mDualCameraFragment.startSetting();
  }

  private class MyCameraCallback implements DualCameraFragment.CameraCallback {

    @Override
    public void onPreviewError() {
      Logs.e(TAG, "预览失败");
    }

    @Override
    public void onPreviewSuccess() {
      Logs.d(TAG, "预览成功");
    }
  }
}

