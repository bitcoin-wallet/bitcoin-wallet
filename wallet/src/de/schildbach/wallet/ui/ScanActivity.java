/*
 * Copyright 2012-2013 the original author or authors.
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import de.schildbach.wallet.camera.CameraManager;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class ScanActivity extends Activity implements SurfaceHolder.Callback
{
	public static final String INTENT_EXTRA_RESULT = "result";

	private static final long VIBRATE_DURATION = 50L;
	private static final long AUTO_FOCUS_INTERVAL_MS = 2500L;

	private final CameraManager cameraManager = new CameraManager();
	private ScannerView scannerView;
	private SurfaceHolder surfaceHolder;
	private Vibrator vibrator;
	private HandlerThread cameraThread;
	private Handler cameraHandler;

	private static final int DIALOG_CAMERA_PROBLEM = 0;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

		setContentView(R.layout.scan_activity);

		scannerView = (ScannerView) findViewById(R.id.scan_activity_mask);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		cameraThread = new HandlerThread("cameraThread", Process.THREAD_PRIORITY_BACKGROUND);
		cameraThread.start();
		cameraHandler = new Handler(cameraThread.getLooper());

		final SurfaceView surfaceView = (SurfaceView) findViewById(R.id.scan_activity_preview);
		surfaceHolder = surfaceView.getHolder();
		surfaceHolder.addCallback(this);
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceCreated(final SurfaceHolder holder)
	{
		cameraHandler.post(openRunnable);
	}

	public void surfaceDestroyed(final SurfaceHolder holder)
	{
	}

	public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height)
	{
	}

	@Override
	protected void onPause()
	{
		cameraHandler.post(closeRunnable);

		surfaceHolder.removeCallback(this);

		super.onPause();
	}

	@Override
	public void onBackPressed()
	{
		setResult(RESULT_CANCELED);
		finish();
	}

	@Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event)
	{
		switch (keyCode)
		{
			case KeyEvent.KEYCODE_FOCUS:
			case KeyEvent.KEYCODE_CAMERA:
				// don't launch camera app
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				cameraHandler.post(new Runnable()
				{
					public void run()
					{
						cameraManager.setTorch(keyCode == KeyEvent.KEYCODE_VOLUME_UP);
					}
				});
				return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	public void handleResult(final Result scanResult, final Bitmap scanImage)
	{
		vibrator.vibrate(VIBRATE_DURATION);

		// superimpose dots to highlight the key features of the qr code
		final ResultPoint[] points = scanResult.getResultPoints();
		if (points != null && points.length > 0)
		{
			final Paint paint = new Paint();
			paint.setColor(getResources().getColor(R.color.scan_result_dots));
			paint.setStrokeWidth(10.0f);

			final Canvas canvas = new Canvas(scanImage);
			for (final ResultPoint point : points)
				canvas.drawPoint(point.getX(), point.getY(), paint);
		}

		scannerView.drawResultBitmap(scanImage);

		final Intent result = new Intent();
		result.putExtra(INTENT_EXTRA_RESULT, scanResult.getText());
		setResult(RESULT_OK, result);

		// delayed finish
		new Handler().post(new Runnable()
		{
			public void run()
			{
				finish();
			}
		});
	}

	private final Runnable openRunnable = new Runnable()
	{
		public void run()
		{
			try
			{
				cameraManager.open(surfaceHolder);

				final Rect framingRect = cameraManager.getFrame();
				final Rect framingRectInPreview = cameraManager.getFramePreview();

				runOnUiThread(new Runnable()
				{
					public void run()
					{
						scannerView.setFraming(framingRect, framingRectInPreview);
					}
				});

				cameraHandler.post(autofocusRunnable);
				cameraHandler.post(fetchAndDecodeRunnable);
			}
			catch (final IOException x)
			{
				x.printStackTrace();
				showDialog(DIALOG_CAMERA_PROBLEM);
			}
			catch (final RuntimeException x)
			{
				x.printStackTrace();
				showDialog(DIALOG_CAMERA_PROBLEM);
			}
		}
	};

	private final Runnable closeRunnable = new Runnable()
	{
		public void run()
		{
			cameraManager.close();

			// cancel background thread
			cameraHandler.removeCallbacksAndMessages(null);
			cameraThread.quit();
		}
	};

	private final Runnable autofocusRunnable = new Runnable()
	{
		public void run()
		{
			final Camera camera = cameraManager.getCamera();
			final String focusMode = camera.getParameters().getFocusMode();
			final boolean useAutoFocus = focusMode.equals(Camera.Parameters.FOCUS_MODE_AUTO) || focusMode.equals(Camera.Parameters.FOCUS_MODE_MACRO);

			if (useAutoFocus)
			{
				camera.autoFocus(new Camera.AutoFocusCallback()
				{
					public void onAutoFocus(final boolean success, final Camera camera)
					{
					}
				});

				// schedule again
				cameraHandler.postDelayed(autofocusRunnable, AUTO_FOCUS_INTERVAL_MS);
			}
		}
	};

	private final Runnable fetchAndDecodeRunnable = new Runnable()
	{
		private final QRCodeReader reader = new QRCodeReader();
		private final Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);

		public void run()
		{
			cameraManager.requestPreviewFrame(new PreviewCallback()
			{
				public void onPreviewFrame(final byte[] data, final Camera camera)
				{
					decode(data);
				}
			});
		}

		private void decode(final byte[] data)
		{
			final PlanarYUVLuminanceSource source = cameraManager.buildLuminanceSource(data);
			final BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

			try
			{
				hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, new ResultPointCallback()
				{
					public void foundPossibleResultPoint(final ResultPoint dot)
					{
						runOnUiThread(new Runnable()
						{
							public void run()
							{
								scannerView.addDot(dot);
							}
						});
					}
				});
				final Result scanResult = reader.decode(bitmap, hints);

				// success
				final int sourceWidth = source.getWidth();
				final int sourceHeight = source.getHeight();

				final Bitmap grayscaleBitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888);
				grayscaleBitmap.setPixels(source.renderCroppedGreyscaleBitmap(), 0, sourceWidth, 0, 0, sourceWidth, sourceHeight);

				runOnUiThread(new Runnable()
				{
					public void run()
					{
						handleResult(scanResult, grayscaleBitmap);
					}
				});
			}
			catch (final ReaderException x)
			{
				// retry
				cameraHandler.post(fetchAndDecodeRunnable);
			}
			finally
			{
				reader.reset();
			}
		}
	};

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);

		if (id == DIALOG_CAMERA_PROBLEM)
		{
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setTitle(R.string.scan_camera_problem_dialog_title);
			builder.setMessage(R.string.scan_camera_problem_dialog_message);
			builder.setNeutralButton(R.string.button_dismiss, new OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int which)
				{
					finish();
				}
			});
			builder.setOnCancelListener(new OnCancelListener()
			{
				public void onCancel(final DialogInterface dialog)
				{
					finish();
				}
			});
		}

		return builder.create();
	}
}
