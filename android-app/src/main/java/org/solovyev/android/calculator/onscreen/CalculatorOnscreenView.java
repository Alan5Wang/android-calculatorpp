/*
 * Copyright 2013 serso aka se.solovyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Contact details
 *
 * Email: se.solovyev@gmail.com
 * Site:  http://se.solovyev.org
 */

package org.solovyev.android.calculator.onscreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import org.solovyev.android.calculator.*;
import org.solovyev.android.prefs.Preference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * User: serso
 * Date: 11/21/12
 * Time: 9:03 PM
 */
public class CalculatorOnscreenView {
	/*
	**********************************************************************
	*
	*                           CONSTANTS
	*
	**********************************************************************
	*/

	private static final String TAG = CalculatorOnscreenView.class.getSimpleName();

	/*
	**********************************************************************
	*
	*                           STATIC
	*
	**********************************************************************
	*/

	private static final Preference<CalculatorOnscreenViewState> viewStatePreference = new CalculatorOnscreenViewState.Preference("onscreen_view_state", CalculatorOnscreenViewState.newDefaultState());

	/*
	**********************************************************************
	*
	*                           FIELDS
	*
	**********************************************************************
	*/

	@Nonnull
	private View root;

	@Nonnull
	private View content;

	@Nonnull
	private View header;

	@Nonnull
	private AndroidCalculatorEditorView editorView;

	@Nonnull
	private AndroidCalculatorDisplayView displayView;

	@Nonnull
	private Context context;

	@Nonnull
	private CalculatorOnscreenViewState state = CalculatorOnscreenViewState.newDefaultState();

	@Nullable
	private OnscreenViewListener viewListener;

	/*
	**********************************************************************
	*
	*                           STATES
	*
	**********************************************************************
	*/

	private boolean minimized = false;

	private boolean attached = false;

	private boolean folded = false;

	private boolean initialized = false;

	private boolean hidden = true;


	/*
	**********************************************************************
	*
	*                           CONSTRUCTORS
	*
	**********************************************************************
	*/

	private CalculatorOnscreenView() {
	}

	public static CalculatorOnscreenView newInstance(@Nonnull Context context,
													 @Nonnull CalculatorOnscreenViewState state,
													 @Nullable OnscreenViewListener viewListener) {
		final CalculatorOnscreenView result = new CalculatorOnscreenView();

		result.root = View.inflate(context, R.layout.onscreen_layout, null);
		result.context = context;
		result.viewListener = viewListener;

		final CalculatorOnscreenViewState persistedState = readState(context);
		if (persistedState != null) {
			result.state = persistedState;
		} else {
			result.state = state;
		}

		return result;
	}

	/*
	**********************************************************************
	*
	*                           METHODS
	*
	**********************************************************************
	*/

	public void updateDisplayState(@Nonnull CalculatorDisplayViewState displayState) {
		checkInit();
		displayView.setState(displayState);
	}

	public void updateEditorState(@Nonnull CalculatorEditorViewState editorState) {
		checkInit();
		editorView.setState(editorState);
	}

	private void setHeight(int height) {
		checkInit();

		final WindowManager.LayoutParams params = (WindowManager.LayoutParams) root.getLayoutParams();

		params.height = height;

		getWindowManager().updateViewLayout(root, params);
	}

	/*
	**********************************************************************
	*
	*                           LIFECYCLE
	*
	**********************************************************************
	*/

	private void init() {

		if (!initialized) {
			for (final CalculatorButton widgetButton : CalculatorButton.values()) {
				final View button = root.findViewById(widgetButton.getButtonId());
				if (button != null) {
					button.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							widgetButton.onClick(context);
							if (widgetButton == CalculatorButton.app) {
								minimize();
							}
						}
					});
					button.setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View v) {
							widgetButton.onLongClick(context);
							return true;
						}
					});
				}
			}

			final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

			header = root.findViewById(R.id.onscreen_header);
			content = root.findViewById(R.id.onscreen_content);

			displayView = (AndroidCalculatorDisplayView) root.findViewById(R.id.calculator_display);
			displayView.init(this.context, false);

			editorView = (AndroidCalculatorEditorView) root.findViewById(R.id.calculator_editor);
			editorView.init();

			final View onscreenFoldButton = root.findViewById(R.id.onscreen_fold_button);
			onscreenFoldButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (folded) {
						unfold();
					} else {
						fold();
					}
				}
			});

			final View onscreenHideButton = root.findViewById(R.id.onscreen_minimize_button);
			onscreenHideButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					minimize();
				}
			});

			root.findViewById(R.id.onscreen_close_button).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					hide();
				}
			});

			final ImageView onscreenTitleImageView = (ImageView) root.findViewById(R.id.onscreen_title);
			onscreenTitleImageView.setOnTouchListener(new WindowDragTouchListener(wm, root));

			initialized = true;
		}

	}

	private void checkInit() {
		if (!initialized) {
			throw new IllegalStateException("init() must be called!");
		}
	}

	public void show() {
		if (hidden) {
			init();
			attach();

			hidden = false;
		}
	}

	public void attach() {
		checkInit();

		final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		if (!attached) {
			final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
					state.getWidth(),
					state.getHeight(),
					state.getX(),
					state.getY(),
					WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
					WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
					PixelFormat.TRANSLUCENT);

			params.gravity = Gravity.TOP | Gravity.LEFT;

			wm.addView(root, params);
			attached = true;
		}
	}

	private void fold() {
		if (!folded) {
			int newHeight = header.getHeight();
			content.setVisibility(View.GONE);
			setHeight(newHeight);
			folded = true;
		}
	}

	private void unfold() {
		if (folded) {
			content.setVisibility(View.VISIBLE);
			setHeight(state.getHeight());
			folded = false;
		}
	}

	public void detach() {
		checkInit();

		if (attached) {
			getWindowManager().removeView(root);
			attached = false;
		}
	}

	public void minimize() {
		checkInit();
		if (!minimized) {
			persistState(context, getCurrentState(!folded));

			detach();

			if (viewListener != null) {
				viewListener.onViewMinimized();
			}

			minimized = true;
		}
	}

	public static void persistState(@Nonnull Context context, @Nonnull CalculatorOnscreenViewState state) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		viewStatePreference.putPreference(preferences, state);
	}

	@Nullable
	public static CalculatorOnscreenViewState readState(@Nonnull Context context) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		if (viewStatePreference.isSet(preferences)) {
			return viewStatePreference.getPreference(preferences);
		} else {
			return null;
		}
	}

	public void hide() {
		checkInit();

		if (!hidden) {

			persistState(context, getCurrentState(!folded));

			detach();

			if (viewListener != null) {
				viewListener.onViewHidden();
			}

			hidden = true;
		}
	}

	@Nonnull
	private WindowManager getWindowManager() {
		return ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));
	}

	@Nonnull
	public CalculatorOnscreenViewState getCurrentState(boolean useRealSize) {
		final WindowManager.LayoutParams params = (WindowManager.LayoutParams) root.getLayoutParams();
		if (useRealSize) {
			return CalculatorOnscreenViewState.newInstance(params.width, params.height, params.x, params.y);
		} else {
			return CalculatorOnscreenViewState.newInstance(state.getWidth(), state.getHeight(), params.x, params.y);
		}
	}

	/*
	**********************************************************************
	*
	*                           STATIC
	*
	**********************************************************************
	*/

	private static class WindowDragTouchListener implements View.OnTouchListener {

    	/*
		**********************************************************************
    	*
    	*                           CONSTANTS
    	*
    	**********************************************************************
    	*/

		private static final float DIST_EPS = 0f;
		private static final float DIST_MAX = 100000f;
		private static final long TIME_EPS = 0L;

    	/*
    	**********************************************************************
    	*
    	*                           FIELDS
    	*
    	**********************************************************************
    	*/

		@Nonnull
		private final WindowManager wm;

		private int orientation;

		private float x0;

		private float y0;

		private long time = 0;

		@Nonnull
		private final View view;

		private int displayWidth;

		private int displayHeight;

    	/*
    	**********************************************************************
    	*
    	*                           CONSTRUCTORS
    	*
    	**********************************************************************
    	*/

		public WindowDragTouchListener(@Nonnull WindowManager wm,
									   @Nonnull View view) {
			this.wm = wm;
			this.view = view;
			initDisplayParams();
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (orientation != this.wm.getDefaultDisplay().getOrientation()) {
				// orientation has changed => we need to check display width/height each time window moved
				initDisplayParams();
			}

			//Log.d(TAG, "Action: " + event.getAction());

			final float x1 = event.getRawX();
			final float y1 = event.getRawY();

			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					Log.d(TAG, "0:" + toString(x0, y0) + ", 1: " + toString(x1, y1));
					x0 = x1;
					y0 = y1;
					return true;

				case MotionEvent.ACTION_MOVE:
					final long currentTime = System.currentTimeMillis();

					if (currentTime - time >= TIME_EPS) {
						time = currentTime;
						processMove(x1, y1);
					}
					return true;
			}

			return false;
		}

		private void initDisplayParams() {
			this.orientation = this.wm.getDefaultDisplay().getOrientation();

			final DisplayMetrics displayMetrics = new DisplayMetrics();
			wm.getDefaultDisplay().getMetrics(displayMetrics);

			this.displayWidth = displayMetrics.widthPixels;
			this.displayHeight = displayMetrics.heightPixels;
		}

		private void processMove(float x1, float y1) {
			final float Δx = x1 - x0;
			final float Δy = y1 - y0;

			final WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
			Log.d(TAG, "0:" + toString(x0, y0) + ", 1: " + toString(x1, y1) + ", Δ: " + toString(Δx, Δy) + ", params: " + toString(params.x, params.y));

			boolean xInBounds = isDistanceInBounds(Δx);
			boolean yInBounds = isDistanceInBounds(Δy);
			if (xInBounds || yInBounds) {

				if (xInBounds) {
					params.x = (int) (params.x + Δx);
				}

				if (yInBounds) {
					params.y = (int) (params.y + Δy);
				}

				params.x = Math.min(Math.max(params.x, 0), displayWidth - params.width);
				params.y = Math.min(Math.max(params.y, 0), displayHeight - params.height);

				wm.updateViewLayout(view, params);

				if (xInBounds) {
					x0 = x1;
				}

				if (yInBounds) {
					y0 = y1;
				}
			}
		}

		private boolean isDistanceInBounds(float δx) {
			δx = Math.abs(δx);
			return δx >= DIST_EPS && δx < DIST_MAX;
		}

		@Nonnull
		private static String toString(float x, float y) {
			return "(" + formatFloat(x) + ", " + formatFloat(y) + ")";
		}

		private static String formatFloat(float value) {
			if (value >= 0) {
				return "+" + String.format("%.2f", value);
			} else {
				return String.format(Locale.ENGLISH, "%.2f", value);
			}
		}
	}
}
