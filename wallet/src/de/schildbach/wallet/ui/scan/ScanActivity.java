/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui.scan;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.WindowManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import de.schildbach.wallet.R;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author Andreas Schildbach
 */
@SuppressWarnings("deprecation")
public final class ScanActivity extends AbstractWalletActivity implements SurfaceTextureListener {
    private static final String INTENT_EXTRA_RESULT = "result";

    public static class Scan extends ActivityResultContract<Void, String> {
        @Override
        public Intent createIntent(final Context context, Void unused) {
            return new Intent(context, ScanActivity.class);
        }

        @Override
        public String parseResult(final int resultCode, final Intent intent) {
            if (resultCode == Activity.RESULT_OK)
                return intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            else
                return null;
        }
    }

    private static final long VIBRATE_DURATION = 50L;
    private static final long AUTO_FOCUS_INTERVAL_MS = 2500L;

    private final CameraManager cameraManager = new CameraManager();

    private View contentView;
    private ScannerView scannerView;
    private TextureView previewView;

    private volatile boolean surfaceCreated = false;

    private Vibrator vibrator;
    private HandlerThread cameraThread;
    private volatile Handler cameraHandler;

    private ScanViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(ScanActivity.class);

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    maybeOpenCamera();
                } else {
                    log.info("missing {}, showing error", Manifest.permission.CAMERA);
                    viewModel.showPermissionWarnDialog.setValue(Event.simple());
                }
            });

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vibrator = getSystemService(Vibrator.class);

        viewModel = new ViewModelProvider(this).get(ScanViewModel.class);
        viewModel.showPermissionWarnDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                WarnDialogFragment.show(getSupportFragmentManager(), R.string.scan_camera_permission_dialog_title,
                        getString(R.string.scan_camera_permission_dialog_message));
            }
        });
        viewModel.showProblemWarnDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                WarnDialogFragment.show(getSupportFragmentManager(), R.string.scan_camera_problem_dialog_title,
                        getString(R.string.scan_camera_problem_dialog_message));
            }
        });

        // Stick to the orientation the activity was started with. We cannot declare this in the
        // AndroidManifest.xml, because it's not allowed in combination with the windowIsTranslucent=true
        // theme attribute.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        // Draw under navigation and status bars.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setContentView(R.layout.scan_activity);
        contentView = findViewById(android.R.id.content);
        scannerView = findViewById(R.id.scan_activity_mask);
        previewView = findViewById(R.id.scan_activity_preview);
        previewView.setSurfaceTextureListener(this);

        cameraThread = new HandlerThread("cameraThread", Process.THREAD_PRIORITY_BACKGROUND);
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            log.info("missing {}, requesting", Manifest.permission.CAMERA);
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        maybeOpenCamera();
    }

    @Override
    protected void onPause() {
        cameraHandler.post(closeRunnable);

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // cancel background thread
        cameraHandler.removeCallbacksAndMessages(null);
        cameraThread.quit();

        previewView.setSurfaceTextureListener(null);

        // We're removing the requested orientation because if we don't, somehow the requested orientation is
        // bleeding through to the calling activity, forcing it into a locked state until it is restarted.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        super.onDestroy();
    }

    private void maybeOpenCamera() {
        if (surfaceCreated && ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            cameraHandler.post(openRunnable);
    }

    @Override
    public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
        surfaceCreated = true;
        maybeOpenCamera();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
        surfaceCreated = false;
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
    }

    @Override
    public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
    }

    @Override
    public void onAttachedToWindow() {
        setShowWhenLocked(true);
    }

    @Override
    public void onBackPressed() {
        scannerView.setVisibility(View.GONE);
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_FOCUS || keyCode == KeyEvent.KEYCODE_CAMERA) {
            // don't launch camera app
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            cameraHandler.post(() -> cameraManager.setTorch(keyCode == KeyEvent.KEYCODE_VOLUME_UP));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void handleResult(final Result scanResult) {
        vibrator.vibrate(VIBRATE_DURATION);

        scannerView.setIsResult(true);

        final Intent result = new Intent();
        result.putExtra(INTENT_EXTRA_RESULT, scanResult.getText());
        setResult(RESULT_OK, result);
        postFinish();
    }

    private void postFinish() {
        new Handler().postDelayed(() -> finish(), 50);
    }

    private final Runnable openRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                final Camera camera = cameraManager.open(previewView, displayRotation());

                final Rect framingRect = cameraManager.getFrame();
                final RectF framingRectInPreview = new RectF(cameraManager.getFramePreview());
                framingRectInPreview.offsetTo(0, 0);
                final boolean cameraFlip = cameraManager.getFacing() == CameraInfo.CAMERA_FACING_FRONT;
                final int cameraRotation = cameraManager.getOrientation();

                runOnUiThread(() -> scannerView.setFraming(framingRect, framingRectInPreview, displayRotation(), cameraRotation,
                        cameraFlip));

                final String focusMode = camera.getParameters().getFocusMode();
                final boolean nonContinuousAutoFocus = Camera.Parameters.FOCUS_MODE_AUTO.equals(focusMode)
                        || Camera.Parameters.FOCUS_MODE_MACRO.equals(focusMode);

                if (nonContinuousAutoFocus)
                    cameraHandler.post(new AutoFocusRunnable(camera));
                cameraHandler.post(fetchAndDecodeRunnable);
            } catch (final Exception x) {
                log.info("problem opening camera", x);
                viewModel.showProblemWarnDialog.postValue(Event.simple());
            }
        }

        private int displayRotation() {
            final int rotation = getWindowManager().getDefaultDisplay().getRotation();
            if (rotation == Surface.ROTATION_0)
                return 0;
            else if (rotation == Surface.ROTATION_90)
                return 90;
            else if (rotation == Surface.ROTATION_180)
                return 180;
            else if (rotation == Surface.ROTATION_270)
                return 270;
            else
                throw new IllegalStateException("rotation: " + rotation);
        }
    };

    private final Runnable closeRunnable = new Runnable() {
        @Override
        public void run() {
            cameraHandler.removeCallbacksAndMessages(null);
            cameraManager.close();
        }
    };

    private final class AutoFocusRunnable implements Runnable {
        private final Camera camera;

        public AutoFocusRunnable(final Camera camera) {
            this.camera = camera;
        }

        @Override
        public void run() {
            try {
                camera.autoFocus(autoFocusCallback);
            } catch (final Exception x) {
                log.info("problem with auto-focus, will not schedule again", x);
            }
        }

        private final Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(final boolean success, final Camera camera) {
                // schedule again
                cameraHandler.postDelayed(AutoFocusRunnable.this, AUTO_FOCUS_INTERVAL_MS);
            }
        };
    }

    private final Runnable fetchAndDecodeRunnable = new Runnable() {
        private final QRCodeReader reader = new QRCodeReader();
        private final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

        @Override
        public void run() {
            cameraManager.requestPreviewFrame((data, camera) -> decode(data));
        }

        private void decode(final byte[] data) {
            final PlanarYUVLuminanceSource source = cameraManager.buildLuminanceSource(data);
            final BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            try {
                hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, (ResultPointCallback) dot -> runOnUiThread(() -> scannerView.addDot(dot)));
                final Result scanResult = reader.decode(bitmap, hints);

                runOnUiThread(() -> handleResult(scanResult));
            } catch (final ReaderException x) {
                // retry
                cameraHandler.post(fetchAndDecodeRunnable);
            } finally {
                reader.reset();
            }
        }
    };

    public static class WarnDialogFragment extends DialogFragment {
        private static final String FRAGMENT_TAG = WarnDialogFragment.class.getName();

        public static void show(final FragmentManager fm, final int titleResId, final String message) {
            final WarnDialogFragment newFragment = new WarnDialogFragment();
            final Bundle args = new Bundle();
            args.putInt("title", titleResId);
            args.putString("message", message);
            newFragment.setArguments(args);
            newFragment.show(fm, FRAGMENT_TAG);
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Bundle args = getArguments();
            final DialogBuilder dialog = DialogBuilder.warn(getActivity(), args.getInt("title"), args.getString(
                    "message"));
            dialog.singleDismissButton((d, which) -> getActivity().finish());
            return dialog.create();
        }

        @Override
        public void onCancel(final DialogInterface dialog) {
            getActivity().finish();
        }
    }
}
