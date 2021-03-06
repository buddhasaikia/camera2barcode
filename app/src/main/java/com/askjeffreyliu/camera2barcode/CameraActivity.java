/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.askjeffreyliu.camera2barcode;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.askjeffreyliu.camera2barcode.camera.CameraSource;
import com.askjeffreyliu.camera2barcode.camera.CameraSourcePreview;
import com.askjeffreyliu.camera2barcode.camera.GraphicOverlay;
import com.askjeffreyliu.camera2barcode.pager.PlaceholderFragment;
import com.askjeffreyliu.camera2barcode.pager.SectionsPagerAdapter;
import com.askjeffreyliu.camera2barcode.utils.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import com.google.zxing.pdf417.PDF417Reader;
import com.rd.PageIndicatorView;
import com.rd.animation.type.AnimationType;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.util.ArrayList;

public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 38;

    // CAMERA VERSION TWO DECLARATIONS
    private CameraSource mCamera2Source = null;
    private ViewPager mViewPager;
    private SectionsPagerAdapter mSectionsPagerAdapter;
    // COMMON TO BOTH CAMERAS
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private Paint paint;
    private Paint textPaint;
    private Handler handler;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (mGraphicOverlay != null)
                mGraphicOverlay.clear();
            getPageSetOverlay(true);
        }
    };

    ArrayList<PlaceholderFragment> pages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        handler = new Handler();

        for (int i = 0; i < 3; i++) {
            pages.add(PlaceholderFragment.newInstance(i));
        }

        // a cute permission request screen
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, PermissionCheckActivity.class));
            finish();
            return;
        }

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.barcodeOverlay);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), pages);

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setOffscreenPageLimit(pages.size());
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                mGraphicOverlay.clear();
                mCamera2Source.setReaderType(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        PageIndicatorView pageIndicatorView = (PageIndicatorView) findViewById(R.id.pageIndicatorView);
        pageIndicatorView.setViewPager(mViewPager);
        pageIndicatorView.setAnimationType(AnimationType.WORM);

        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.GREEN);

        textPaint = new Paint();
        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(36.0f);
    }

    // This snippet hides the system bars.
    private void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CameraActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        hideSystemUI();
        if (checkGooglePlayAvailability()) {
            requestPermissionThenOpenCamera();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopCameraSource();

    }

    private boolean checkGooglePlayAvailability() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode == ConnectionResult.SUCCESS) {
            return true;
        } else {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(this, resultCode, 2404).show();
            }
        }
        return false;
    }

    private void requestPermissionThenOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            createCameraSourceBack();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void createCameraSourceBack() {
        QRCodeMultiReader mQrReader = new QRCodeMultiReader();
        GenericMultipleBarcodeReader dataMatrixReader = new GenericMultipleBarcodeReader(new MultiFormatReader());
        PDF417Reader pdf417Reader = new PDF417Reader();

        mCamera2Source = new CameraSource.Builder(this, mQrReader, dataMatrixReader, pdf417Reader)
                .setFocusMode(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                .setFlashMode(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .build();

        //IF CAMERA2 HARDWARE LEVEL IS LEGACY
        if (mCamera2Source.isCamera2Native()) {
            startCameraSource();
        } else {
            showToast(getString(R.string.camera_error));
            finish();
        }
    }

    private void startCameraSource() {
        if (mCamera2Source != null) {
            try {
                mPreview.start(mCamera2Source, mGraphicOverlay);
            } catch (SecurityException e) {
                showToast(getString(R.string.request_permission));
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source", e);
                showToast("Unable to start camera source");
                mCamera2Source.release();
                mCamera2Source = null;
            }
        }
    }

    private void stopCameraSource() {
        mPreview.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                showToast(getString(R.string.request_permission));
                finish();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }


    @Subscribe//(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MultiResultEvent event) {
        if (event != null && event.results != null && event.results.length > 0) {
            mGraphicOverlay.clear();
            handler.removeCallbacks(runnable);

            for (int i = 0; i < event.results.length; i++) {
                final Result r = event.results[i];
                ArrayList<Point> pointArrayList = new ArrayList<>();
                for (int j = 0; j < r.getResultPoints().length; j++) {
                    float x, y;
                    try {
                        x = r.getResultPoints()[j].getX();
                        y = r.getResultPoints()[j].getY();
                    } catch (NullPointerException e) {
                        return;
                    }

                    final float scaledX = x / event.width * mGraphicOverlay.getWidth();
                    final float scaledY = y / event.height * mGraphicOverlay.getHeight();
                    pointArrayList.add(new Point((int) scaledX, (int) scaledY));
                }
                final Rect rect = Utils.createRect(pointArrayList, r.getBarcodeFormat() == BarcodeFormat.QR_CODE);

                mGraphicOverlay.add(new GraphicOverlay.Graphic(mGraphicOverlay) {
                    @Override
                    public void draw(Canvas canvas) {
                        canvas.drawRect(rect, paint);
                        canvas.drawText(r.getText(), rect.left, rect.bottom, textPaint);
                    }
                });

                CameraActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        getPageSetOverlay(false);
                    }
                });
                handler.postDelayed(runnable, 100);
            }
        }
    }

    private void getPageSetOverlay(boolean show) {
        if (mViewPager != null && mSectionsPagerAdapter != null) {
            for (int i = 0; i < pages.size(); i++) {
                if (mViewPager.getCurrentItem() != i) {
                    pages.get(i).showOverlay(true);
                } else {
                    pages.get(i).showOverlay(show);
                }
            }
        }
    }
}