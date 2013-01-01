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

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.os.Build;

/**
 * @author Andreas Schildbach
 */
@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public final class GingerbreadCameraInterface implements CameraInterface
{
	public Camera open()
	{
		final int numCameras = Camera.getNumberOfCameras();
		if (numCameras == 0)
			throw new RuntimeException("no cameras found");

		final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

		// search for back facing camera
		for (int i = 0; i < numCameras; i++)
		{
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
				return Camera.open(i);
		}

		// none found
		return Camera.open(0);
	}
}
