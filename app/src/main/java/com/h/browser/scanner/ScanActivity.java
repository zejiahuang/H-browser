package com.h.browser.scanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.util.EnumMap;
import java.util.Map;

public class ScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView surfaceView;
    private Camera camera;
    private HandlerThread decodeThread;
    private Handler decodeHandler;
    private MultiFormatReader reader;
    private volatile boolean decoding;
    private boolean previewing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        surfaceView = new SurfaceView(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        TextView hint = new TextView(this);
        hint.setText("将二维码放入取景框");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(16);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        hint.setPadding(0, 24, 0, 48);
        root.addView(hint, hintParams);
        setContentView(root);

        reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.Arrays.asList(
                com.google.zxing.BarcodeFormat.QR_CODE,
                com.google.zxing.BarcodeFormat.CODE_128,
                com.google.zxing.BarcodeFormat.EAN_13));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);

        decodeThread = new HandlerThread("qr-decode");
        decodeThread.start();
        decodeHandler = new Handler(decodeThread.getLooper());

        surfaceView.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        openCamera(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null && !previewing) {
            try {
                camera.startPreview();
                previewing = true;
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        release();
    }

    private void openCamera(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = chooseSize(parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                parameters.setPictureSize(size.width, size.height);
            }
            if (parameters.getSupportedFocusModes() != null
                    && parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            camera.setParameters(parameters);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
            previewing = true;
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开相机：" + t.getMessage(), Toast.LENGTH_LONG).show();
            release();
            finish();
        }
    }

    private Camera.Size chooseSize(Camera.Parameters parameters) {
        Camera.Size best = null;
        long target = 1280L * 720L;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (best == null || Math.abs((long) size.width * size.height - target)
                    < Math.abs((long) best.width * best.height - target)) {
                best = size;
            }
        }
        return best;
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera source) {
        if (decoding || data == null || source == null) {
            return;
        }
        decoding = true;
        final Camera.Size size = source.getParameters().getPreviewSize();
        decodeHandler.post(() -> {
            try {
                PlanarYUVLuminanceSource luminance = new PlanarYUVLuminanceSource(data,
                        size.width, size.height, 0, 0, size.width, size.height, false);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(luminance));
                final String text = reader.decodeWithState(bitmap).getText();
                if (text != null && !text.isEmpty()) {
                    succeed(text);
                    return;
                }
            } catch (NotFoundException ignored) {
            } catch (Throwable ignored) {
            } finally {
                decoding = false;
                try {
                    reader.reset();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void succeed(final String text) {
        runOnUiThread(() -> {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(60);
            }
            Intent result = new Intent();
            result.putExtra("url", text);
            setResult(Activity.RESULT_OK, result);
            finish();
        });
    }

    private void release() {
        previewing = false;
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
            } catch (Throwable ignored) {
            }
            try {
                camera.release();
            } catch (Throwable ignored) {
            }
            camera = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera == null && surfaceView.getHolder().getSurface().isValid()) {
            openCamera(surfaceView.getHolder());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        release();
    }

    @Override
    protected void onDestroy() {
        release();
        if (decodeThread != null) {
            decodeThread.quit();
            decodeThread = null;
        }
        super.onDestroy();
    }
}
