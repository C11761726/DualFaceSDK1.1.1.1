package com.techshino.app;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;
import com.techshino.facespoof.Algorithm;
import com.techshino.usbcameradual.BuildConfig;
import com.techshino.utils.Logs;

/**
 * Created by wangzhi on 2017/8/2.
 */
public class App extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    if (LeakCanary.isInAnalyzerProcess(this)) {
      // This process is dedicated to LeakCanary for heap analysis.
      // You should not init your app in this process.
      return;
    }
    LeakCanary.install(this);
    // Normal app init code...

    Algorithm.getInstance(this);
    Logs.setsIsLogEnabled(BuildConfig.DEBUG);
  }
}
