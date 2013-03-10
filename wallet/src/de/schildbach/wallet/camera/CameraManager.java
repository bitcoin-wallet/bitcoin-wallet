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

package de.schildbach.wallet.camera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;

/**
 * @author Andreas Schildbach
 */
public final class CameraManager
{
	private static final boolean CONTINUOUS_FOCUS = true;

	private static final int MIN_FRAME_WIDTH = 240;
	private static final int MIN_FRAME_HEIGHT = 240;
	private static final int MAX_FRAME_WIDTH = 600;
	private static final int MAX_FRAME_HEIGHT = 400;
	private static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
	private static final int MAX_PREVIEW_PIXELS = 1280 * 720;

	private Camera camera;
	private Camera.Size cameraResolution;
	private Rect frame;
	private Rect framePreview;

	public Camera getCamera()
	{
		return camera;
	}

	public Rect getFrame()
	{
		return frame;
	}

	public Rect getFramePreview()
	{
		return framePreview;
	}

	public void open(final SurfaceHolder holder) throws IOException
	{
		camera = new CameraSupportManager().build().open();
		camera.setPreviewDisplay(holder);

		final Camera.Parameters parameters = camera.getParameters();

		final Rect surfaceFrame = holder.getSurfaceFrame();
		final int surfaceWidth = surfaceFrame.width();
		final int surfaceHeight = surfaceFrame.height();

		cameraResolution = findBestPreviewSizeValue(parameters, new Point(surfaceWidth, surfaceHeight));

		int width = surfaceWidth * 3 / 4;
		if (width < MIN_FRAME_WIDTH)
			width = MIN_FRAME_WIDTH;
		else if (width > MAX_FRAME_WIDTH)
			width = MAX_FRAME_WIDTH;

		int height = surfaceHeight * 3 / 4;
		if (height < MIN_FRAME_HEIGHT)
			height = MIN_FRAME_HEIGHT;
		else if (height > MAX_FRAME_HEIGHT)
			height = MAX_FRAME_HEIGHT;

		final int finalWidth = Math.min(width, height);

		final int leftOffset = (surfaceWidth - finalWidth) / 2;
		final int topOffset = (surfaceHeight - finalWidth) / 2;
		frame = new Rect(leftOffset, topOffset, leftOffset + finalWidth, topOffset + finalWidth);
		framePreview = new Rect(frame.left * cameraResolution.width / surfaceWidth, frame.top * cameraResolution.height / surfaceHeight, frame.right
				* cameraResolution.width / surfaceWidth, frame.bottom * cameraResolution.height / surfaceHeight);

		final String savedParameters = parameters == null ? null : parameters.flatten();

		try
		{
			setDesiredCameraParameters(camera, cameraResolution, false);
		}
		catch (final RuntimeException x)
		{
			if (savedParameters != null)
			{
				final Camera.Parameters parameters2 = camera.getParameters();
				parameters2.unflatten(savedParameters);
				try
				{
					camera.setParameters(parameters2);
					setDesiredCameraParameters(camera, cameraResolution, true);
				}
				catch (final RuntimeException x2)
				{
					x2.printStackTrace();
				}
			}
		}

		camera.startPreview();
	}

	public void close()
	{
		if (camera != null)
		{
			camera.stopPreview();
			camera.release();
		}
	}

	private static final Comparator<Camera.Size> numPixelComparator = new Comparator<Camera.Size>()
	{
		public int compare(final Camera.Size size1, final Camera.Size size2)
		{
			final int pixels1 = size1.height * size1.width;
			final int pixels2 = size2.height * size2.width;

			if (pixels1 < pixels2)
				return 1;
			else if (pixels1 > pixels2)
				return -1;
			else
				return 0;
		}
	};

	private static Camera.Size findBestPreviewSizeValue(final Camera.Parameters parameters, final Point screenResolution)
	{
		final List<Camera.Size> rawSupportedSizes = parameters.getSupportedPreviewSizes();
		if (rawSupportedSizes == null)
			return parameters.getPreviewSize();

		// sort by size, descending
		final List<Camera.Size> supportedPreviewSizes = new ArrayList<Camera.Size>(rawSupportedSizes);
		Collections.sort(supportedPreviewSizes, numPixelComparator);

		final float screenAspectRatio = (float) screenResolution.x / (float) screenResolution.y;

		Camera.Size bestSize = null;
		float diff = Float.POSITIVE_INFINITY;

		for (final Camera.Size supportedPreviewSize : supportedPreviewSizes)
		{
			final int realWidth = supportedPreviewSize.width;
			final int realHeight = supportedPreviewSize.height;
			final int realPixels = realWidth * realHeight;
			if (realPixels < MIN_PREVIEW_PIXELS || realPixels > MAX_PREVIEW_PIXELS)
				continue;

			final boolean isCandidatePortrait = realWidth < realHeight;
			final int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
			final int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
			if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y)
				return supportedPreviewSize;

			final float aspectRatio = (float) maybeFlippedWidth / (float) maybeFlippedHeight;
			final float newDiff = Math.abs(aspectRatio - screenAspectRatio);
			if (newDiff < diff)
			{
				bestSize = supportedPreviewSize;
				diff = newDiff;
			}
		}

		if (bestSize != null)
			return bestSize;
		else
			return parameters.getPreviewSize();
	}

	@SuppressLint("InlinedApi")
	private static void setDesiredCameraParameters(final Camera camera, final Camera.Size cameraResolution, final boolean safeMode)
	{
		final Camera.Parameters parameters = camera.getParameters();

		if (parameters == null)
			return;

		String focusMode;
		if (safeMode || !CONTINUOUS_FOCUS)
			focusMode = findValue(parameters.getSupportedFocusModes(), Camera.Parameters.FOCUS_MODE_AUTO);
		else
			focusMode = findValue(parameters.getSupportedFocusModes(), Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
					Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, Camera.Parameters.FOCUS_MODE_AUTO);

		if (!safeMode && focusMode == null)
			focusMode = findValue(parameters.getSupportedFocusModes(), Camera.Parameters.FOCUS_MODE_MACRO, Camera.Parameters.FOCUS_MODE_EDOF);

		if (focusMode != null)
			parameters.setFocusMode(focusMode);

		parameters.setPreviewSize(cameraResolution.width, cameraResolution.height);

		camera.setParameters(parameters);
	}

	public void requestPreviewFrame(final PreviewCallback callback)
	{
		camera.setOneShotPreviewCallback(callback);
	}

	public PlanarYUVLuminanceSource buildLuminanceSource(final byte[] data)
	{
		return new PlanarYUVLuminanceSource(data, cameraResolution.width, cameraResolution.height, framePreview.left, framePreview.top,
				framePreview.width(), framePreview.height(), false);
	}

	public void setTorch(final boolean enabled)
	{
		if (enabled != getTorchEnabled(camera))
			setTorchEnabled(camera, enabled);
	}

	private static boolean getTorchEnabled(final Camera camera)
	{
		final Camera.Parameters parameters = camera.getParameters();
		if (parameters != null)
		{
			final String flashMode = camera.getParameters().getFlashMode();
			return flashMode != null && (Camera.Parameters.FLASH_MODE_ON.equals(flashMode) || Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode));
		}

		return false;
	}

	private static void setTorchEnabled(final Camera camera, final boolean enabled)
	{
		final Camera.Parameters parameters = camera.getParameters();

		final List<String> supportedFlashModes = parameters.getSupportedFlashModes();
		if (supportedFlashModes != null)
		{
			final String flashMode;
			if (enabled)
				flashMode = findValue(supportedFlashModes, Camera.Parameters.FLASH_MODE_TORCH, Camera.Parameters.FLASH_MODE_ON);
			else
				flashMode = findValue(supportedFlashModes, Camera.Parameters.FLASH_MODE_OFF);

			if (flashMode != null)
			{
				camera.cancelAutoFocus(); // autofocus can cause conflict

				parameters.setFlashMode(flashMode);
				camera.setParameters(parameters);
			}
		}
	}

	private static String findValue(final Collection<String> values, final String... valuesToFind)
	{
		for (final String valueToFind : valuesToFind)
			if (values.contains(valueToFind))
				return valueToFind;

		return null;
	}
}
