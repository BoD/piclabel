/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright 2013 Benoit 'BoD' Lubek (BoD@JRAF.org).  All Rights Reserved.
 */
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.jraf.android.piclabel.Constants;
import org.jraf.android.piclabel.R;

public class TypefaceAdapter extends ArrayAdapter<String> {
    private static final String TAG = Constants.TAG + TypefaceAdapter.class.getSimpleName();
    private static String[] mTypefaceFileNames;
    private static String[] mTypefaceNames;
    private static Typeface[] mTypefaces;

    public TypefaceAdapter(Context context) {
        super(context, android.R.layout.simple_spinner_item, getTypefaces(context));
        //setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    private static String[] getTypefaces(Context context) {
        if (mTypefaceFileNames == null) {
            try {
                mTypefaceFileNames = context.getAssets().list("fonts");
                List<String> typefaceFileNameList = Arrays.asList(mTypefaceFileNames);
                Collections.sort(typefaceFileNameList, new Comparator<String>() {
                    @Override
                    public int compare(String lhs, String rhs) {
                        return lhs.compareToIgnoreCase(rhs);
                    }
                });
                mTypefaceFileNames = typefaceFileNameList.toArray(mTypefaceFileNames);
                mTypefaceNames = new String[mTypefaceFileNames.length];
                mTypefaces = new Typeface[mTypefaceFileNames.length];
                int i = 0;
                for (String fileName : mTypefaceFileNames) {
                    mTypefaceNames[i] = fileName.substring(0, fileName.indexOf('.'));
                    mTypefaceNames[i] = mTypefaceNames[i].substring(0, 1).toUpperCase(Locale.US) + mTypefaceNames[i].substring(1).toLowerCase(Locale.US);
                    mTypefaces[i] = Typeface.createFromAsset(context.getAssets(), "fonts/" + fileName);
                    i++;
                }
            } catch (IOException e) {
                Log.e(TAG, "getTypefaces Could not list fonts", e);
                return new String[0];
            }
        }
        return mTypefaceFileNames;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView res = (TextView) super.getView(position, convertView, parent);
        res.setTextSize(getContext().getResources().getDimension(R.dimen.typefaceAdapter_fontSize));
        res.setTypeface(mTypefaces[position]);
        res.setText(mTypefaceNames[position]);
        return res;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView res = (TextView) super.getDropDownView(position, convertView, parent);
        int padding = getContext().getResources().getDimensionPixelSize(R.dimen.typefaceAdapter_padding_dropDown);
        res.setPadding(padding, padding, padding, padding);
        res.setTextSize(getContext().getResources().getDimension(R.dimen.typefaceAdapter_fontSize_dropDown));
        res.setTypeface(mTypefaces[position]);
        res.setText(mTypefaceNames[position]);
        return res;
    }

    public String getTypefaceName(int position) {
        return mTypefaceFileNames[position];
    }
}