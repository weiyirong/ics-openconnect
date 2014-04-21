/*
 * Copyright (c) 2014, Kevin Cernekee
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * In addition, as a special exception, the copyright holders give
 * permission to link the code of portions of this program with the
 * OpenSSL library.
 */

package app.openconnect.fragments;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import org.stoken.LibStoken;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import app.openconnect.R;
import app.openconnect.VpnProfile;
import app.openconnect.core.ProfileManager;

public class TokenDiagFragment extends Fragment {

	public static final String TAG = "OpenConnect";

	public static final String EXTRA_UUID = "app.openconnect.UUID";
	public static final String EXTRA_PIN = "app.openconnect.PIN";
	public static final String EXTRA_PIN_PROMPT = "app.openconnect.PIN_PROMPT";

	private LibStoken mStoken;
	private TextView mTokencode;
	private String mRawTokencode = "";
	private ProgressBar mProgressBar;
	private View mView;
	private Calendar mLastUpdate;

	private String mPin;
	private boolean mPinPrompt = false;
	private AlertDialog mDialog;

	private Handler mHandler;
	private Runnable mRunnable;

	private String mUUID;

	@Override
	public void setArguments(Bundle b) {
		mUUID = b.getString(EXTRA_UUID);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
        mStoken = new LibStoken();
        mHandler = new Handler();

        mRunnable = new Runnable() {
			@Override
			public void run() {
				updateUI();
			}
        };
	}

	private int importToken(String UUID) {
		VpnProfile vp = ProfileManager.get(mUUID);
		if (vp == null) {
			return R.string.securid_internal_error;
		}

		String tokenString = vp.mPrefs.getString("token_string", "").trim();
		if (tokenString.equals("")) {
			return R.string.securid_internal_error;
		}

		if (mStoken.importString(tokenString) != LibStoken.SUCCESS) {
			return R.string.securid_parse_error;
		}

		if (mStoken.decryptSeed(null, null) != LibStoken.SUCCESS) {
			return R.string.securid_encrypted;
		}

		mPinPrompt = mStoken.isPINRequired();
		return 0;
	}

    private void writeStatusField(int id, int header_res, String value) {
    	String html = "<b>" + getString(header_res) + "</b><br>" + value;
    	TextView tv = (TextView)mView.findViewById(id);
    	tv.setText(Html.fromHtml(html));
    }

	private void populateView(View v) {
		mTokencode = (TextView)v.findViewById(R.id.tokencode);
		mProgressBar = (ProgressBar)v.findViewById(R.id.progress_bar);
		mView = v;

		Button copyButton = (Button)v.findViewById(R.id.copy_button);
		copyButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Activity act = getActivity();
				ClipboardManager clipboard = (ClipboardManager)
						act.getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("Tokencode", mRawTokencode);
				clipboard.setPrimaryClip(clip);
				Toast.makeText(act.getBaseContext(), R.string.copied_entry, Toast.LENGTH_SHORT).show();
			}
		});

		/* static fields */
		if (mStoken.isPINRequired()) {
			writeStatusField(R.id.using_pin, R.string.using_pin,
					mPin == null ?
					getString(R.string.no) :
					getString(R.string.yes));
		} else {
			writeStatusField(R.id.using_pin, R.string.using_pin,
					getString(R.string.not_required));
		}

		writeStatusField(R.id.token_sn, R.string.token_sn, getString(R.string.unknown));
		writeStatusField(R.id.exp_date, R.string.exp_date, getString(R.string.unknown));
		writeStatusField(R.id.dev_id, R.string.dev_id, getString(R.string.unknown));
	}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {

    	View v;
    	int error = 0;

    	if (mUUID == null) {
    		error = R.string.securid_none_defined;
    	} else {
    		error = importToken(mUUID);
    	}

    	if (error != 0) {
    		v = inflater.inflate(R.layout.token_diag_error, container, false);

    		TextView tv = (TextView)v.findViewById(R.id.msg);
    		tv.setText(error);
    	} else {
    		v = inflater.inflate(R.layout.token_diag_info, container, false);
    		populateView(v);
    	}

    	if (savedInstanceState != null) {
    		mPin = savedInstanceState.getString(EXTRA_PIN);
    		mPinPrompt = savedInstanceState.getBoolean(EXTRA_PIN_PROMPT, mPinPrompt);
    	}

    	return v;
    }

    private String formatTokencode(String s) {
    	int midpoint = s.length() / 2;
    	return s.substring(0, midpoint) + " " + s.substring(midpoint);
    }

    private void updateUI() {
    	/* in this case we're just displaying a static error message */
    	if (mTokencode == null) {
    		return;
    	}

    	/* don't update if the PIN dialog is up */
    	if (mDialog != null) {
    		mHandler.postDelayed(mRunnable, 500);
    		return;
    	}

    	Calendar now = Calendar.getInstance();
    	mProgressBar.setProgress(59 - now.get(Calendar.SECOND));

    	if (mLastUpdate == null ||
    		now.get(Calendar.MINUTE) != mLastUpdate.get(Calendar.MINUTE)) {

    		long t = now.getTimeInMillis() / 1000;
    		mRawTokencode = mStoken.computeTokencode(t, mPin);
    		mTokencode.setText(formatTokencode(mRawTokencode));
    		writeStatusField(R.id.next_tokencode, R.string.next_tokencode,
    				formatTokencode(mStoken.computeTokencode(t + 60, mPin)));
    	}

		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG);
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		writeStatusField(R.id.gmt, R.string.gmt, df.format(now.getTime()));

    	mLastUpdate = now;
    	mHandler.postDelayed(mRunnable, 500);
    }

    private void pinDialog() {
    	Context ctx = getActivity();

    	final TextView tv = new EditText(ctx);
		tv.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		tv.setTransformationMethod(PasswordTransformationMethod.getInstance());

    	AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
    		.setView(tv)
    		.setTitle(R.string.enter_pin)
    		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					mPin = tv.getText().toString().trim();
					if (!mStoken.checkPIN(mPin)) {
						mPin = null;
					} else {
						writeStatusField(R.id.using_pin, R.string.using_pin,
								getString(R.string.yes));
					}

					mPinPrompt = false;
				}
    		});
    	mDialog = builder.create();
    	mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				mPinPrompt = false;
				mDialog = null;
			}
    	});
    	mDialog.show();
    }

    @Override
    public void onSaveInstanceState(Bundle b) {
    	/* FIXME: fragment save/restore is currently broken */
    	b.putString(EXTRA_PIN, mPin);
    	b.putBoolean(EXTRA_PIN_PROMPT, mPinPrompt);
    }

    @Override
    public void onResume() {
    	super.onResume();
    	mLastUpdate = null;
    	mHandler.post(mRunnable);

    	if (mPinPrompt) {
    		pinDialog();
    	}
    }

    @Override
    public void onPause() {
    	super.onPause();
    	mHandler.removeCallbacks(mRunnable);

    	if (mDialog != null) {
    		mDialog.dismiss();
    		mDialog = null;
    	}
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	mStoken.destroy();
    }

}