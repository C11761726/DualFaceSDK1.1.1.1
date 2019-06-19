package com.techshino.usbcameradual;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

  TextView mHintTv;
  int index = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    findViewById(R.id.startbtn).setOnClickListener(v -> startActivityForResult(new Intent(MainActivity.this,DualCameraActivity.class),11));
    mHintTv = (TextView) findViewById(R.id.hintTv);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (resultCode == RESULT_OK) {
      mHintTv.postDelayed(new Runnable() {
        @Override
        public void run() {
          startActivityForResult(new Intent(MainActivity.this,DualCameraActivity.class),11);
        }
      },1500);
      mHintTv.setText("执行次数：" + (++index));
    }
  }
}
