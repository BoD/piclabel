/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2013 Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jraf.android.piclabel.app.main;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import org.jraf.android.piclabel.Config;
import org.jraf.android.piclabel.Constants;
import org.jraf.android.piclabel.R;
import org.jraf.android.piclabel.app.form.FormActivity;
import org.jraf.android.util.file.FileUtil;
import org.jraf.android.util.string.StringUtil;

public class MainActivity extends FragmentActivity {
    private static final String TAG = Constants.TAG + MainActivity.class.getSimpleName();

    protected static final int REQUEST_TAKE_PICTURE = 0;
    protected static final int REQUEST_PICK_FROM_GALLERY = 1;

    private File mImageCaptureFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        findViewById(R.id.btnTakePicture).setOnClickListener(mTakePictureOnClickListener);
        findViewById(R.id.btnPickFromGallery).setOnClickListener(mPickFromGalleryOnClickListener);

        if (savedInstanceState != null) {
            mImageCaptureFile = (File) savedInstanceState.getSerializable("mImageCaptureFile");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("mImageCaptureFile", mImageCaptureFile);
        super.onSaveInstanceState(outState);
    }


    /*
     * Take picture.
     */

    private final OnClickListener mTakePictureOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mImageCaptureFile = FileUtil.newTemporaryFile(MainActivity.this, "ImageCapture", ".jpg");
            Uri imageCaptureUri = Uri.fromFile(mImageCaptureFile);
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, imageCaptureUri);
            startActivityForResult(Intent.createChooser(cameraIntent, null), REQUEST_TAKE_PICTURE);
        }
    };


    /*
     * Pick from gallery.
     */

    private final OnClickListener mPickFromGalleryOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
            startActivityForResult(Intent.createChooser(galleryIntent, null), REQUEST_PICK_FROM_GALLERY);
        }
    };

    @Override
    public void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        if (Config.LOGD) Log.d(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode + " data=" + StringUtil.toString(data));
        if (resultCode != Activity.RESULT_OK) return;

        switch (requestCode) {
            case REQUEST_TAKE_PICTURE:
                // The picture is stored in the temporary file
                Uri pictureUri = Uri.fromFile(mImageCaptureFile);
                startActivity(new Intent(MainActivity.this, FormActivity.class).setData(pictureUri));
                break;

            case REQUEST_PICK_FROM_GALLERY:
                // The picture is stored in the media store: get the corresponding file
                new AsyncTask<Void, Void, File>() {
                    @Override
                    protected File doInBackground(Void... params) {
                        return getFileFromMediaUri(data.getData());
                    }

                    @Override
                    protected void onPostExecute(File result) {
                        if (result == null) {
                            // The user probably picked a Picasa picture
                            Toast.makeText(MainActivity.this, R.string.main_externalSource, Toast.LENGTH_LONG).show();
                            return;
                        }
                        Uri picUri = Uri.fromFile(result);
                        startActivity(new Intent(MainActivity.this, FormActivity.class).setData(picUri));
                    }
                }.execute();
                break;
        }
    }

    private File getFileFromMediaUri(Uri mediaUri) {
        if (Config.LOGD) Log.d(TAG, "getFileFromMediaUri mediaUri=" + mediaUri);

        // Sometimes the image picker returns a file uri directly
        if ("file".equals(mediaUri.getScheme())) {
            File res = new File(mediaUri.getPath());
            if (!res.exists()) {
                return null;
            }
            return res;
        }

        // Do a query to get the path
        String[] projection = { MediaColumns.DATA };
        Cursor cursor = getContentResolver().query(mediaUri, projection, null, null, null);
        try {
            if (cursor == null || !cursor.moveToFirst()) {
                Log.w(TAG, "getFileFromMediaUri null or empty cursor, returning null");
                return null;
            }
            String filePath = cursor.getString(0);
            if (Config.LOGD) Log.d(TAG, "getFileFromMediaUri filePath=" + filePath);
            if (filePath == null) {
                Log.w(TAG, "getFileFromMediaUri Returned filePath is null (Picasa?)");
                return null;
            }
            return new File(filePath);
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
