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
package org.jraf.android.piclabel.app.form;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import org.jraf.android.piclabel.Config;
import org.jraf.android.piclabel.Constants;
import org.jraf.android.piclabel.R;
import org.jraf.android.util.async.ProgressDialogAsyncTaskFragment;
import org.jraf.android.util.async.SimpleAsyncTask;
import org.jraf.android.util.bitmap.BitmapUtil;
import org.jraf.android.util.io.IoUtil;
import org.jraf.android.util.mediascanner.MediaScannerUtil;

public class FormActivity extends FragmentActivity {
    private static final String TAG = Constants.TAG + FormActivity.class.getSimpleName();

    private static final int JPEG_QUALITY = 85;

    private static final String FRAGMENT_RETAINED_STATE = "FRAGMENT_RETAINED_STATE";

    private ImageView mImgThumbnail;
    private EditText mEdtDateTime;
    private EditText mEdtLocation;
    private View mBtnSave;
    private View mBtnShare;
    private Spinner mSpnFont;
    private TypefaceAdapter mTypefaceAdapter;
    private View mConLoading;

    private FormStateFragment mState;
    private File mImageFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.form);
        mImgThumbnail = (ImageView) findViewById(R.id.imgThumbnail);
        mEdtDateTime = (EditText) findViewById(R.id.edtDateTime);
        mEdtLocation = (EditText) findViewById(R.id.edtLocation);
        mSpnFont = (Spinner) findViewById(R.id.spnFont);
        mTypefaceAdapter = new TypefaceAdapter(this);
        mSpnFont.setAdapter(mTypefaceAdapter);

        mBtnSave = findViewById(R.id.btnSave);
        mBtnSave.setOnClickListener(mSaveOnClickListener);
        mBtnShare = findViewById(R.id.btnShare);
        mBtnShare.setOnClickListener(mShareOnClickListener);
        mConLoading = findViewById(R.id.pgbLoading);

        mImageFile = new File(getIntent().getData().getPath());
        restoreState();
    }

    private void restoreState() {
        mState = (FormStateFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_RETAINED_STATE);
        if (mState == null) {
            mState = new FormStateFragment();
            getSupportFragmentManager().beginTransaction().add(mState, FRAGMENT_RETAINED_STATE).commit();

            retrieveInfoFromImage();
        } else {
            mImgThumbnail.setImageBitmap(mState.thumbnailBitmap);
            if (mState.thumbnailBitmap != null) {
                mEdtDateTime.setEnabled(true);
                mEdtLocation.setEnabled(true);
                mSpnFont.setEnabled(true);
                mBtnSave.setEnabled(true);
                mBtnShare.setEnabled(true);
                mConLoading.setVisibility(View.GONE);
            }
        }
    }

    private void retrieveInfoFromImage() {
        new SimpleAsyncTask() {
            private ImageInfo mImageInfo;

            @Override
            protected void doInBackground() {
                mState.thumbnailBitmap = BitmapUtil.createThumbnail(mImageFile, 320, 320);
                mImageInfo = extractImageInfo(mImageFile);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (mState.thumbnailBitmap == null) {
                    Toast.makeText(FormActivity.this, R.string.form_couldNotDecodeImage, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                Animation anim = AnimationUtils.loadAnimation(FormActivity.this, android.R.anim.fade_out);
                mConLoading.startAnimation(anim);
                mConLoading.setVisibility(View.GONE);

                mEdtDateTime.setText("");
                mEdtDateTime.append(mImageInfo.dateTime);
                if (mImageInfo.isLocalDateTime) mEdtDateTime.setError(getString(R.string.form_useLocalDate));

                mEdtLocation.setText("");
                mEdtLocation.append(mImageInfo.location);
                if (mImageInfo.reverseGeocodeProblem) {
                    mEdtLocation.setError(getString(R.string.form_cannotReverseGeocode));
                } else if (mImageInfo.isLocalLocation) {
                    mEdtLocation.setError(getString(R.string.form_useLocalLocation));
                }

                mImgThumbnail.setImageBitmap(mState.thumbnailBitmap);
                mEdtDateTime.setEnabled(true);
                mEdtLocation.setEnabled(true);
                mSpnFont.setEnabled(true);
                mBtnSave.setEnabled(true);
                mBtnShare.setEnabled(true);
            }
        }.execute();
    }

    private static class ImageInfo {
        public String dateTime;
        public String location;
        public boolean isLocalDateTime;
        public boolean isLocalLocation;
        public boolean reverseGeocodeProblem;
    }

    protected ImageInfo extractImageInfo(File file) {
        ImageInfo res = new ImageInfo();
        ExifInterface exifInterface = null;
        try {
            exifInterface = new ExifInterface(file.getPath());
        } catch (IOException e) {
            Log.e(TAG, "extractImageInfo Could not read exif", e);
        }

        // Date
        String dateTimeStr = null;
        if (exifInterface != null) dateTimeStr = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
        if (TextUtils.isEmpty(dateTimeStr)) {
            // No date in exif: use 'local' date
            res.dateTime = DateUtils.formatDateTime(this, System.currentTimeMillis(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
                    | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR);
            res.isLocalDateTime = true;
        } else {
            res.dateTime = parseExifDateTime(dateTimeStr);
            if (res.dateTime == null) {
                // Date in exif could not be parsed: use 'local' date
                DateUtils.formatDateTime(this, System.currentTimeMillis(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR);
                res.isLocalDateTime = true;
            }
        }

        // Location
        float[] latLon = new float[2];
        boolean latLonPresent = exifInterface != null && exifInterface.getLatLong(latLon);
        if (!latLonPresent) {
            // No location in exif: use 'local' location
            res.isLocalLocation = true;
            latLonPresent = getLatestLocalLocation(latLon);
            if (latLonPresent) res.location = reverseGeocode(latLon[0], latLon[1]);
        } else {
            res.location = reverseGeocode(latLon[0], latLon[1]);
        }
        if (res.location == null) {
            res.reverseGeocodeProblem = true;
            res.location = "";
        }
        return res;
    }


    private boolean getLatestLocalLocation(float[] latLon) {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = locationManager.getProviders(true);
        Location location = null;
        for (int i = providers.size() - 1; i >= 0; i--) {
            location = locationManager.getLastKnownLocation(providers.get(i));
            if (location != null) break;
        }

        if (location == null) return false;
        latLon[0] = (float) location.getLatitude();
        latLon[1] = (float) location.getLongitude();
        return true;
    }


    private String reverseGeocode(float lat, float lon) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        List<Address> addresses;
        try {
            addresses = geocoder.getFromLocation(lat, lon, 1);
        } catch (Throwable t) {
            Log.w(TAG, "reverseGeocode Could not reverse geocode", t);
            return null;
        }
        if (addresses == null || addresses.isEmpty()) return null;
        Address address = addresses.get(0);
        ArrayList<String> strings = new ArrayList<String>(5);
        if (address.getMaxAddressLineIndex() > 0) strings.add(address.getAddressLine(0));
        if (!TextUtils.isEmpty(address.getLocality())) strings.add(address.getLocality());
        if (!TextUtils.isEmpty(address.getCountryName())) strings.add(address.getCountryName());
        return TextUtils.join(", ", strings);
    }


    private String parseExifDateTime(String s) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy':'MM':'dd' 'HH':'mm':'ss", Locale.US);
        Date date;
        try {
            date = sdf.parse(s);
        } catch (ParseException e) {
            Log.e(TAG, "decodeExifDateTime Could not parse " + s, e);
            return null;
        }
        return DateUtils.formatDateTime(this, date.getTime(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_SHOW_YEAR);
    }


    /*
     * Save.
     */

    private final OnClickListener mSaveOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            new ProgressDialogAsyncTaskFragment() {
                @Override
                protected void doInBackground() throws Exception {
                    Uri uri = processAndSaveImage();
                    if (uri == null) throw new Exception("Received null processed image");
                }

                @Override
                protected void onPostExecuteOk() {
                    super.onPostExecuteOk();
                    finish();
                }
            }.toastOk(R.string.form_process_success).toastFail(R.string.form_couldNotProcessImage).execute(getSupportFragmentManager());
        }
    };


    /*
     * Share.
     */

    private final OnClickListener mShareOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            new ProgressDialogAsyncTaskFragment() {
                private Uri mUri;

                @Override
                protected void doInBackground() throws Exception {
                    mUri = processAndSaveImage();
                    if (mUri == null) throw new Exception("Received null processed image");
                }

                @Override
                protected void onPostExecuteOk() {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/jpeg");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, mUri);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.form_share_subject));
                    shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.form_share_text));
                    shareIntent.putExtra("sms_body", getString(R.string.form_share_subject));
                    startActivity(Intent.createChooser(shareIntent, getText(R.string.common_shareWith)));

                    finish();
                }
            }.toastOk(R.string.form_process_success).toastFail(R.string.form_couldNotProcessImage).execute(getSupportFragmentManager());
        }
    };


    private Uri processAndSaveImage() throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();

        Bitmap bitmap = BitmapUtil.tryDecodeFile(mImageFile, options);
        if (bitmap == null) {
            Log.w(TAG, "processImage Could not decode file, returning null");
            return null;
        }

        // Rotate if necessary
        int rotation = BitmapUtil.getExifRotation(mImageFile);
        if (rotation != 0) {
            if (Config.LOGD) Log.d(TAG, "processImage rotating bitmap");
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotatedBitmap = null;
            try {
                rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                bitmap.recycle();
                bitmap = rotatedBitmap;
            } catch (OutOfMemoryError exception) {
                Log.w(TAG, "processImage Could not rotate bitmap, keeping original orientation", exception);
            }
        }

        // To draw text we need a mutable bitmap
        try {
            bitmap = BitmapUtil.asImmutable(bitmap);
        } catch (Throwable t) {
            throw new Exception(t);
        }
        Canvas canvas = new Canvas(bitmap);

        // Draw text
        drawText(canvas);

        // Save the new bitmap to a file
        File processedImageFile = createProcessedFile();
        try {
            saveBitmap(bitmap, mImageFile, processedImageFile);
            bitmap.recycle();
        } catch (IOException e) {
            Log.e(TAG, "processImage Could not save bitmap, returning null", e);
            return null;
        }

        // Scan it
        return MediaScannerUtil.scanFileNow(this, processedImageFile);
    }

    private void drawText(Canvas canvas) {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/" + getSelectedFontName()));

        int textSize = canvas.getHeight() / 35;
        paint.setTextSize(textSize);
        int margin = textSize / 5;

        // Measure date/time
        String dateTime = mEdtDateTime.getText().toString();
        Rect boundsDateTime = new Rect();
        paint.getTextBounds(dateTime, 0, dateTime.length(), boundsDateTime);

        // Measure location
        String location = mEdtLocation.getText().toString();
        Rect boundsLocation = new Rect();
        paint.getTextBounds(location, 0, location.length(), boundsLocation);

        int totalWidth = boundsDateTime.width() + textSize * 2 + boundsLocation.width();
        if (totalWidth > canvas.getWidth()) {
            // Draw on 2 lines

            // Draw a rectangle
            paint.setColor(Color.argb(180, 0, 0, 0));
            canvas.drawRect(0, 0, canvas.getWidth(), -boundsDateTime.top + boundsDateTime.bottom + -boundsLocation.top + boundsLocation.bottom + margin * 3,
                    paint);

            // Draw date/time
            paint.setColor(Color.WHITE);
            canvas.drawText(dateTime, margin, margin + -boundsDateTime.top, paint);

            // Draw location
            canvas.drawText(location, canvas.getWidth() - boundsLocation.right - boundsLocation.left - margin, margin + -boundsDateTime.top
                    + boundsDateTime.bottom + margin + -boundsLocation.top, paint);

        } else {
            // Draw on 1 line

            // Draw a rectangle
            paint.setColor(Color.argb(180, 0, 0, 0));
            canvas.drawRect(0, 0, canvas.getWidth(), margin + Math.max(boundsDateTime.height(), boundsLocation.height()) + margin, paint);

            // Draw date/time
            paint.setColor(Color.WHITE);
            canvas.drawText(dateTime, margin, margin + -boundsDateTime.top, paint);

            // Draw location
            canvas.drawText(location, canvas.getWidth() - boundsLocation.right - boundsLocation.left - margin, margin + -boundsLocation.top, paint);
        }
    }

    private File createProcessedFile() {
        File picturesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File albumPath = new File(picturesPath, Constants.ALBUM_NAME);
        albumPath.mkdirs();
        String fileDateName = new SimpleDateFormat("yyyy-MM-dd'_'HH-mm-ss", Locale.US).format(new Date());
        File res = new File(albumPath, fileDateName + ".jpg");
        return res;
    }

    private static void saveBitmap(Bitmap bitmap, File originalFile, File outFile) throws IOException {
        // Compress to a new file
        FileOutputStream fileOutputStream = new FileOutputStream(outFile);
        bitmap.compress(CompressFormat.JPEG, JPEG_QUALITY, fileOutputStream);
        IoUtil.closeSilently(fileOutputStream);

        // Copy exif tags from original file
        BitmapUtil.copyExifTags(originalFile, outFile);
    }

    private String getSelectedFontName() {
        return mTypefaceAdapter.getTypefaceName(mSpnFont.getSelectedItemPosition());
    }
}
