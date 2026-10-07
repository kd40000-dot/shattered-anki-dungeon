/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.android;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.net.ConnectivityManager;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyDiagnostics;
import com.watabou.noosa.Game;
import com.watabou.utils.PlatformSupport;
import com.watabou.utils.RectF;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidPlatformSupport extends PlatformSupport {

	private EditText nativeTextInputProxy;
	private TextWatcher nativeTextInputWatcher;
	private volatile NativeTextInputListener nativeTextInputListener;
	private boolean nativeTextInputUpdating;
	private volatile long nativeTextInputGeneration;

	
	public void updateDisplaySize(){
		AndroidLauncher.instance.setRequestedOrientation( SPDSettings.landscape() ?
				ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE :
				ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED );

		ShatteredPixelDungeon.seamlessResetScene();
	}

	public boolean supportsFullScreen(){
		//We support hiding the navigation bar or gesture bar, if it is present
		// on Android 9+ we check for this, on earlier just assume it's present
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			WindowInsets insets = AndroidLauncher.instance.getApplicationWindow().getDecorView().getRootWindowInsets();
			return insets != null && (insets.getStableInsetBottom() > 0 || insets.getStableInsetRight() > 0 || insets.getStableInsetLeft() > 0);
		} else {
			return true;
		}
	}

	@Override
	public RectF getDisplayCutout() {
		RectF cutoutRect = new RectF();

		//some extra logic here is because cutouts can apparently be returned inverted
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			DisplayCutout cutout = AndroidLauncher.instance.getApplicationWindow().getDecorView().getRootWindowInsets().getDisplayCutout();

			Rect largest = null;
			if (cutout != null) {
				for (Rect r : cutout.getBoundingRects()) {
					if (largest == null
							|| Math.abs(r.height() * r.width()) > Math.abs(largest.height() * largest.width())) {
						largest = r;
					}
				}
			}

			if (largest != null){
				cutoutRect.left = Math.min(largest.left, largest.right);
				cutoutRect.right = Math.max(largest.left, largest.right);
				cutoutRect.top  = Math.min(largest.top, largest.bottom);
				cutoutRect.bottom  = Math.max(largest.top, largest.bottom);
			}
		}

		return cutoutRect;
	}

	@Override
	public RectF getSafeInsets( int level ) {
		RectF insets = new RectF();

		//getting insets technically works down to 6.0 Marshmallow, but we let the device handle all of that prior to 9.0 Pie
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !AndroidLauncher.instance.isInMultiWindowMode()) {
			WindowInsets rootInsets = AndroidLauncher.instance.getApplicationWindow().getDecorView().getRootWindowInsets();
			if (rootInsets != null) {

				//Navigation bar (never on the top)
				if (supportsFullScreen() && !SPDSettings.fullscreen()) {
					insets.left = Math.max(insets.left, rootInsets.getStableInsetLeft());
					insets.right = Math.max(insets.right, rootInsets.getStableInsetRight());
					insets.bottom = Math.max(insets.bottom, rootInsets.getStableInsetBottom());
				}

				//display cutout
				if (level > INSET_BLK) {
					DisplayCutout cutout = rootInsets.getDisplayCutout();

					if (cutout != null) {
						boolean largeCutout = false;
						boolean cutoutsPresent = false;

						int screenSize = Game.width * Game.height;
						for (Rect r : cutout.getBoundingRects()) {
							//use abs as some cutouts can apparently be returned inverted
							int cutoutSize = Math.abs(r.height() * r.width());
							//display cutouts are considered large if they take up more than 0.75%
							// of the screen/ in reality we want less than about 0.5%,
							// but some cutouts over-report their size, Pixel devices especially =S
							if (cutoutSize > 0){
								cutoutsPresent = true;
								if (cutoutSize * 133.33f >= screenSize) {
									largeCutout = true;
								}
							}
						}

						if (!cutoutsPresent){
							//if we get no cutouts reported, assume the device is lying to us
							// and there actually is a cutout, which we must assume is large =S
							largeCutout = true;
						}

						if (largeCutout || level == INSET_ALL) {
							insets.left = Math.max(insets.left, cutout.getSafeInsetLeft());
							insets.top = Math.max(insets.top, cutout.getSafeInsetTop());
							insets.right = Math.max(insets.right, cutout.getSafeInsetRight());
							insets.bottom = Math.max(insets.bottom, cutout.getSafeInsetBottom());
						}
					}
				}
			}
		}
		return insets;
	}

	public void updateSystemUI() {
		
		AndroidLauncher.instance.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				boolean fullscreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.N
						|| !AndroidLauncher.instance.isInMultiWindowMode();
				
				if (fullscreen){
					AndroidLauncher.instance.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
							WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
				} else {
					AndroidLauncher.instance.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
							WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
				}

				if (supportsFullScreen() && SPDSettings.fullscreen()) {
					AndroidLauncher.instance.getWindow().getDecorView().setSystemUiVisibility(
							View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
									| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
									| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY );
				} else {
					//still want to hide the status bar and cutout void
					AndroidLauncher.instance.getWindow().getDecorView().setSystemUiVisibility(
							View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN );
				}
			}
		});
		
	}
	
	@Override
	public boolean connectedToUnmeteredNetwork() {
		//Returns true if using unmetered connection
		return !((ConnectivityManager) AndroidLauncher.instance.getSystemService(Context.CONNECTIVITY_SERVICE)).isActiveNetworkMetered();
	}

	@Override
	public boolean supportsVibration() {
		return true; //always true on Android
	}

	@Override
	public boolean supportsNativeTextInputProxy() {
		return true;
	}

	/**
	 * The study IME uses one Activity-lifetime EditText. Reusing the same native
	 * view avoids a race in Android's input-method manager where removing turn
	 * one's focused view and immediately attaching a new one could crash on the
	 * next review.
	 */
	@Override
	public void startNativeTextInputProxy(String initialText, boolean multiline, NativeTextInputListener listener) {
		AndroidLauncher.instance.runOnUiThread(() -> {
			ensureNativeTextInputProxyOnUiThread();

			final long generation = ++nativeTextInputGeneration;
			nativeTextInputListener = listener;

			EditText input = nativeTextInputProxy;
			input.setSingleLine(!multiline);
			input.setMaxLines(multiline ? Integer.MAX_VALUE : 1);
			input.setImeOptions(multiline ? EditorInfo.IME_FLAG_NO_ENTER_ACTION : EditorInfo.IME_ACTION_DONE);
			input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
					| (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));

			nativeTextInputUpdating = true;
			input.setText(initialText == null ? "" : initialText);
			input.setSelection(input.getText().length());
			nativeTextInputUpdating = false;

			input.setOnEditorActionListener((v, actionId, event) -> {
				if (actionId != EditorInfo.IME_ACTION_DONE) return false;

				NativeTextInputListener currentListener = nativeTextInputListener;
				long currentGeneration = nativeTextInputGeneration;
				if (currentListener == null || currentGeneration != generation || Gdx.app == null) {
					return true;
				}

				Gdx.app.postRunnable(() -> {
					if (nativeTextInputGeneration == currentGeneration
							&& nativeTextInputListener == currentListener) {
						currentListener.onEnterPressed();
					}
				});
				return true;
			});

			input.requestFocus();
			InputMethodManager imm = (InputMethodManager) AndroidLauncher.instance.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				input.post(() -> {
					if (nativeTextInputGeneration == generation && nativeTextInputListener == listener) {
						imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
					}
				});
			}
		});
	}

	private void ensureNativeTextInputProxyOnUiThread() {
		if (nativeTextInputProxy != null) return;

		EditText input = new EditText(AndroidLauncher.instance);
		nativeTextInputProxy = input;

		input.setBackground(null);
		input.setPadding(0, 0, 0, 0);
		input.setAlpha(0.01f);
		input.setCursorVisible(false);

		nativeTextInputWatcher = new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				if (nativeTextInputUpdating) return;

				NativeTextInputListener listener = nativeTextInputListener;
				if (listener == null || Gdx.app == null) return;

				long generation = nativeTextInputGeneration;
				String value = s == null ? "" : s.toString();

				Gdx.app.postRunnable(() -> {
					if (nativeTextInputGeneration == generation
							&& nativeTextInputListener == listener) {
						listener.onTextChanged(value);
					}
				});
			}
		};
		input.addTextChangedListener(nativeTextInputWatcher);

		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, 1);
		params.gravity = Gravity.TOP | Gravity.START;
		AndroidLauncher.instance.addContentView(input, params);
	}

	@Override
	public void updateNativeTextInputProxy(String text) {
		AndroidLauncher.instance.runOnUiThread(() -> {
			if (nativeTextInputProxy == null || nativeTextInputListener == null) return;

			String value = text == null ? "" : text;
			if (value.contentEquals(nativeTextInputProxy.getText())) return;

			nativeTextInputUpdating = true;
			nativeTextInputProxy.setText(value);
			nativeTextInputProxy.setSelection(nativeTextInputProxy.getText().length());
			nativeTextInputUpdating = false;
		});
	}

	@Override
	public void setNativeTextInputProxyMaxLength(int maxLength) {
		AndroidLauncher.instance.runOnUiThread(() -> {
			if (nativeTextInputProxy == null) return;

			if (maxLength > 0) {
				nativeTextInputProxy.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
			} else {
				nativeTextInputProxy.setFilters(new InputFilter[0]);
			}
		});
	}

	@Override
	public void setNativeTextInputProxyVisible(boolean visible, boolean multiline) {
		AndroidLauncher.instance.runOnUiThread(() -> {
			if (nativeTextInputProxy == null) return;

			InputMethodManager imm = (InputMethodManager) AndroidLauncher.instance.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (visible && nativeTextInputListener != null) {
				nativeTextInputProxy.requestFocus();
				if (imm != null) imm.showSoftInput(nativeTextInputProxy, InputMethodManager.SHOW_IMPLICIT);
			} else {
				if (imm != null) imm.hideSoftInputFromWindow(nativeTextInputProxy.getWindowToken(), 0);
				nativeTextInputProxy.clearFocus();
			}
		});
	}

	@Override
	public void stopNativeTextInputProxy() {
		AndroidLauncher.instance.runOnUiThread(this::stopNativeTextInputProxyOnUiThread);
	}

	private void stopNativeTextInputProxyOnUiThread() {
		nativeTextInputGeneration++;
		nativeTextInputListener = null;

		if (nativeTextInputProxy == null) return;

		InputMethodManager imm = (InputMethodManager) AndroidLauncher.instance.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.hideSoftInputFromWindow(nativeTextInputProxy.getWindowToken(), 0);
		}

		nativeTextInputProxy.setOnEditorActionListener(null);
		nativeTextInputProxy.clearFocus();

		// Keep the native view attached for the next review turn. Clearing its
		// contents prevents old composing text from leaking into the next card.
		nativeTextInputUpdating = true;
		nativeTextInputProxy.setText("");
		nativeTextInputUpdating = false;
	}

	/** Permanently releases the Activity-owned native input view. */
	public void destroyNativeTextInputProxy() {
		AndroidLauncher.instance.runOnUiThread(() -> {
			stopNativeTextInputProxyOnUiThread();

			if (nativeTextInputProxy == null) return;

			if (nativeTextInputWatcher != null) {
				nativeTextInputProxy.removeTextChangedListener(nativeTextInputWatcher);
			}

			if (nativeTextInputProxy.getParent() instanceof android.view.ViewGroup) {
				((android.view.ViewGroup) nativeTextInputProxy.getParent()).removeView(nativeTextInputProxy);
			}

			nativeTextInputProxy = null;
			nativeTextInputWatcher = null;
			nativeTextInputUpdating = false;
		});
	}

	/* FONT SUPPORT */
	
	//droid sans / roboto, or a custom pixel font, for use with Latin and Cyrillic languages
	private static FreeTypeFontGenerator basicFontGenerator;
	//droid sans / nanum gothic / noto sans, for use with Korean
	private static FreeTypeFontGenerator KRFontGenerator;
	//droid sans / noto sans, for use with Chinese
	private static FreeTypeFontGenerator ZHFontGenerator;
	//droid sans / noto sans, for use with Japanese
	private static FreeTypeFontGenerator JPFontGenerator;
	
	//special logic for handling korean android 6.0 font oddities
	private static boolean koreanAndroid6OTF = false;
	
	@Override
	public void setupFontGenerators(int pageSize, boolean systemfont) {
		//don't bother doing anything if nothing has changed
		if (fonts != null && this.pageSize == pageSize && this.systemfont == systemfont){
			return;
		}
		this.pageSize = pageSize;
		this.systemfont = systemfont;

		resetGenerators(false);
		fonts = new HashMap<>();
		basicFontGenerator = KRFontGenerator = ZHFontGenerator = JPFontGenerator = null;
		
		if (systemfont && Gdx.files.absolute("/system/fonts/Roboto-Regular.ttf").exists()) {
			basicFontGenerator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/Roboto-Regular.ttf"));
		} else if (systemfont && Gdx.files.absolute("/system/fonts/DroidSans.ttf").exists()){
			basicFontGenerator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/DroidSans.ttf"));
		} else {
			basicFontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/pixel_font.ttf"));
		}
		
		//android 7.0+. all asian fonts are nicely contained in one spot
		if (Gdx.files.absolute("/system/fonts/NotoSansCJK-Regular.ttc").exists()) {
			//typefaces are 0-JP, 1-KR, 2-SC, 3-TC.
			int typeFace;
			switch (SPDSettings.language()) {
				case JAPANESE:
					typeFace = 0;
					break;
				case KOREAN:
					typeFace = 1;
					break;
				case CHI_SMPL:
				default:
					typeFace = 2;
					break;
				case CHI_TRAD:
					typeFace = 3;
					break;
			}
			KRFontGenerator = ZHFontGenerator = JPFontGenerator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/NotoSansCJK-Regular.ttc"), typeFace);
			
		//otherwise we have to go over a few possibilities.
		} else {
			
			//Korean font generators
			if (Gdx.files.absolute("/system/fonts/NanumGothic.ttf").exists()){
				KRFontGenerator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/NanumGothic.ttf"));
			} else if (Gdx.files.absolute("/system/fonts/NotoSansKR-Regular.otf").exists()){
				KRFontGenerator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/NotoSansKR-Regular.otf"));
				koreanAndroid6OTF = true;
			}
			
			//Chinese font generators
			//we don't use a separate generator for traditional chinese because
			// NotoSansTC-Regular and NotoSansHant-Regular seem to only contain some hant-specific
			// ways to draw certain symbols, too much messing for old android
			if (Gdx.files.absolute("/system/fonts/NotoSansSC-Regular.otf").exists()){
				ZHFontGenerator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/NotoSansSC-Regular.otf"));
			} else if (Gdx.files.absolute("/system/fonts/NotoSansHans-Regular.otf").exists()){
				ZHFontGenerator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/NotoSansHans-Regular.otf"));
			}
			
			//Japaneses font generators
			if (Gdx.files.absolute("/system/fonts/NotoSansJP-Regular.otf").exists()){
				JPFontGenerator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/NotoSansJP-Regular.otf"));
			}
			
			//set up a fallback generator for any remaining fonts
			FreeTypeFontGenerator fallbackGenerator;
			if (Gdx.files.absolute("/system/fonts/DroidSansFallback.ttf").exists()){
				fallbackGenerator = new FreeTypeFontGenerator(Gdx.files.absolute("/system/fonts/DroidSansFallback.ttf"));
			} else {
				//no fallback font, just set to null =/
				fallbackGenerator = null;
			}
			
			if (KRFontGenerator == null) KRFontGenerator = fallbackGenerator;
			if (ZHFontGenerator == null) ZHFontGenerator = fallbackGenerator;
			if (JPFontGenerator == null) JPFontGenerator = fallbackGenerator;
			
		}
		
		if (basicFontGenerator != null) fonts.put(basicFontGenerator, new HashMap<>());
		if (KRFontGenerator != null) fonts.put(KRFontGenerator, new HashMap<>());
		if (ZHFontGenerator != null) fonts.put(ZHFontGenerator, new HashMap<>());
		if (JPFontGenerator != null) fonts.put(JPFontGenerator, new HashMap<>());
		
		//would be nice to use RGBA4444 to save memory, but this causes problems on some gpus =S
		packer = new PixmapPacker(pageSize, pageSize, Pixmap.Format.RGBA8888, 1, false);
	}

	private static Matcher KRMatcher = Pattern.compile("\\p{InHangul_Syllables}").matcher("");
	private static Matcher ZHMatcher = Pattern.compile("\\p{InCJK_Unified_Ideographs}|\\p{InCJK_Symbols_and_Punctuation}|\\p{InHalfwidth_and_Fullwidth_Forms}").matcher("");
	private static Matcher JPMatcher = Pattern.compile("\\p{InHiragana}|\\p{InKatakana}").matcher("");

	@Override
	protected FreeTypeFontGenerator getGeneratorForString( String input ){
		if (KRMatcher.reset(input).find()){
			return KRFontGenerator;
		} else if (ZHMatcher.reset(input).find()){
			return ZHFontGenerator;
		} else if (JPMatcher.reset(input).find()){
			return JPFontGenerator;
		} else {
			return basicFontGenerator;
		}
	}

	//splits on newline (for layout), chinese/japanese (for font choice), and '_'/'**' (for highlighting)
	private Pattern regularsplitter = Pattern.compile(
			"(?<=\n)|(?=\n)|(?<=_)|(?=_)|(?<=\\*\\*)|(?=\\*\\*)|" +
					"(?<=\\p{InHiragana})|(?=\\p{InHiragana})|" +
					"(?<=\\p{InKatakana})|(?=\\p{InKatakana})|" +
					"(?<=\\p{InCJK_Unified_Ideographs})|(?=\\p{InCJK_Unified_Ideographs})|" +
					"(?<=\\p{InCJK_Symbols_and_Punctuation})|(?=\\p{InCJK_Symbols_and_Punctuation})|" +
					"(?<=\\p{InHalfwidth_and_Fullwidth_Forms})|(?=\\p{InHalfwidth_and_Fullwidth_Forms})");

	//additionally splits on spaces, so that each word can be laid out individually
	private Pattern regularsplitterMultiline = Pattern.compile(
			"(?<= )|(?= )|(?<=\n)|(?=\n)|(?<=_)|(?=_)|(?<=\\*\\*)|(?=\\*\\*)|" +
					"(?<=\\p{InHiragana})|(?=\\p{InHiragana})|" +
					"(?<=\\p{InKatakana})|(?=\\p{InKatakana})|" +
					"(?<=\\p{InCJK_Unified_Ideographs})|(?=\\p{InCJK_Unified_Ideographs})|" +
					"(?<=\\p{InCJK_Symbols_and_Punctuation})|(?=\\p{InCJK_Symbols_and_Punctuation})|" +
					"(?<=\\p{InHalfwidth_and_Fullwidth_Forms})|(?=\\p{InHalfwidth_and_Fullwidth_Forms})");
	
	//splits on each non-hangul character. Needed for weird android 6.0 font files
	private Pattern android6KRSplitter = Pattern.compile(
			"(?<= )|(?= )|(?<=\n)|(?=\n)|(?<=_)|(?=_)|(?<=\\*\\*)|(?=\\*\\*)|" +
					"(?!\\p{InHangul_Syllables})|(?<!\\p{InHangul_Syllables})");
	
	@Override
	public String[] splitforTextBlock(String text, boolean multiline) {
		if (koreanAndroid6OTF && getGeneratorForString(text) == KRFontGenerator){
			return android6KRSplitter.split(text);
		} else if (multiline) {
			return regularsplitterMultiline.split(text);
		} else {
			return regularsplitter.split(text);
		}
	}
	
}
