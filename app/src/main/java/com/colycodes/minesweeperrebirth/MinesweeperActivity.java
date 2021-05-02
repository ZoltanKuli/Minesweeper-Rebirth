package com.colycodes.minesweeperrebirth;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.constraint.Guideline;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;

public class MinesweeperActivity extends AppCompatActivity implements RewardedVideoAdListener {
	private static final String M_PREFERENCES = "PREFERENCES";
	private static final String M_DIFFICULTY_KEY = "DIFFICULTY";
	private static final String M_EASY_TIME = "EASY";
	private static final String M_MEDIUM_TIME = "MEDIUM";
	private static final String M_HARD_TIME = "HARD";
	private static final String M_VOLUME_KEY = "VOLUME";
	// For debugging purposes
	private final String LOG_TAG = MinesweeperActivity.class.getSimpleName();
	// Storing data
	private SharedPreferences mSharedPreferences;
	/**
	 * Declares an instance of {@link GameEngine}
	 */
	private GameEngine mGameEngine;
	private boolean mIsGameEngineNull;
	private int mDifficulty;
	private boolean mHasGameEnded;

	// Sound
	private boolean mIsVolumeUp;

	// Adverts
	private RewardedVideoAd mRewardedVideoAd;
	private boolean mAdHasBeenWatched;

	// Menu
	private Toolbar mToolbar;
	private int mAdHeight;
	private Menu mMenu;
	private boolean mIsMenuOpened;
	private boolean mHasMenuChanged;
	private Dialog mDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_minesweeper);

		// Attaching the layout to the toolbar object
		mToolbar = findViewById(R.id.toolbar);
		// Setting toolbar as the ActionBar with setSupportActionBar() call
		setSupportActionBar(mToolbar);

		try {
			getSupportActionBar().setDisplayShowTitleEnabled(false);
		} catch (java.lang.NullPointerException e) {
			Log.e(LOG_TAG, e.fillInStackTrace().toString());
		}

		mGameEngine = null;

		mDifficulty = loadDifficulty();
		startGame(mDifficulty);

		mIsVolumeUp = loadSoundPreferences();
		mGameEngine.setIsVolumeUp(mIsVolumeUp);

		createGameOverMenu();

		MobileAds.initialize(this, "ca-app-pub-8167208762921452/4642253790");
		mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(this);
		mRewardedVideoAd.setRewardedVideoAdListener(this);
		mRewardedVideoAd.loadAd("ca-app-pub-8167208762921452/4642253790",
				new AdRequest.Builder().build());
	}

	// Load difficulty from shared preferences
	private int loadDifficulty() {
		mSharedPreferences = getSharedPreferences(M_PREFERENCES, Context.MODE_PRIVATE);

		return mSharedPreferences.getInt(M_DIFFICULTY_KEY, 0);
	}

	// Save time to shared preferences
	private void saveTime(int time) {
		mSharedPreferences = getSharedPreferences(M_PREFERENCES, Context.MODE_PRIVATE);

		SharedPreferences.Editor editor = mSharedPreferences.edit();
		if(mDifficulty == 0) {
			editor.putInt(M_EASY_TIME, time);
		} else if (mDifficulty == 1) {
			editor.putInt(M_MEDIUM_TIME, time);
		} else {
			editor.putInt(M_HARD_TIME, time);
		}
		editor.apply();
	}

	// Load time from shared preferences
	private int loadTime() {
		mSharedPreferences = getSharedPreferences(M_PREFERENCES, Context.MODE_PRIVATE);

		if(mDifficulty == 0) {
			return mSharedPreferences.getInt(M_EASY_TIME, 0);
		} else if (mDifficulty == 1) {
			return mSharedPreferences.getInt(M_MEDIUM_TIME, 0);
		} else {
			return mSharedPreferences.getInt(M_HARD_TIME, 0);
		}
	}

	// Save difficulty to shared preferences
	private void saveDifficulty(int difficulty) {
		mSharedPreferences = getSharedPreferences(M_PREFERENCES, Context.MODE_PRIVATE);

		SharedPreferences.Editor editor = mSharedPreferences.edit();
		editor.putInt(M_DIFFICULTY_KEY, difficulty);
		editor.apply();
	}

	// Load sound preferences from shared preferences
	private boolean loadSoundPreferences() {
		mSharedPreferences = getSharedPreferences(M_PREFERENCES, Context.MODE_PRIVATE);

		return mSharedPreferences.getBoolean(M_VOLUME_KEY, true);
	}

	// Save sound preferences to shared preferences
	private void saveSoundPreferences() {
		mSharedPreferences = getSharedPreferences(M_PREFERENCES, Context.MODE_PRIVATE);

		SharedPreferences.Editor editor = mSharedPreferences.edit();
		editor.putBoolean(M_VOLUME_KEY, mIsVolumeUp);
		editor.apply();
	}

	// Prepare game
	// Start game
	private void startGame(int difficulty) {
		// Get objects
		ImageView flagOrSearchIV = findViewById(R.id.iv_flag_or_search);
		ImageView timerIV = findViewById(R.id.iv_timer);
		TextView flagsOrSearchNumberTV = findViewById(R.id.tv_flags_or_search_number);
		TextView timeTV = findViewById(R.id.tv_time);

		// Get dimensions
		Display display = getWindowManager().getDefaultDisplay();
		int toolbarHeight = getResources().getDimensionPixelSize(R.dimen.dimenToolbar);

		// Load the resolution into a point object
		Point size = new Point();
		display.getSize(size);

		if (!BuildConfig.PAID_VERSION) {
			NetworkInfo netInfo = null;
			try {
				ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
				netInfo = cm.getActiveNetworkInfo();
			} catch (NullPointerException e) {
				Log.e(LOG_TAG, e.fillInStackTrace().toString());
			}
			if (netInfo != null && netInfo.isConnectedOrConnecting()) {
				mAdHeight  = AdSize.SMART_BANNER.getHeightInPixels(MinesweeperActivity.this);
			} else {
				mAdHeight = 0;
			}

			mGameEngine = new GameEngine(this, this, this.getResources(), difficulty, loadTime(), size, mToolbar, toolbarHeight, mAdHeight,
					flagOrSearchIV, flagsOrSearchNumberTV, timerIV, timeTV);

			MobileAds.initialize(this, "ca-app-pub-8167208762921452~8512930632");
			AdView mAdView = findViewById(R.id.adView);
			AdRequest adRequest = new AdRequest.Builder().build();
			mAdView.loadAd(adRequest);
		} else if (BuildConfig.PAID_VERSION) {
			mAdHeight = 0;
			AdView mAdView = findViewById(R.id.adView);
			mAdView.setVisibility(View.GONE);
			Guideline guideline = findViewById(R.id.guideline);
			guideline.setVisibility(View.GONE);
			mGameEngine = new GameEngine(this, this, this.getResources(), difficulty, loadTime(), size, mToolbar, toolbarHeight, mAdHeight,
					flagOrSearchIV, flagsOrSearchNumberTV, timerIV, timeTV);
		}

		// Start the game
		LinearLayout view = findViewById(R.id.holder_board);
		view.addView(mGameEngine);

		mIsGameEngineNull = false;
		mHasGameEnded = false;
		mHasMenuChanged = false;
		mIsMenuOpened = false;
	}

	// Create new menu if game is over
	private void createGameOverMenu() {
		Thread isGameEndedThread = new Thread(){
			@Override
			public void run(){
				while(!mHasMenuChanged && !mIsGameEngineNull){
					mHasGameEnded = mGameEngine.getHasGameEnded();
					if(mHasGameEnded) {
						try {
							Thread.sleep(5);
							MinesweeperActivity.this.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if(!mHasMenuChanged) {
										// Show short message in toast
										if(!mGameEngine.getIsGameOver()) {
											Toast toast = Toast.makeText(MinesweeperActivity.this, R.string.game_won, Toast.LENGTH_SHORT);
											toast.show();
											saveTime(mGameEngine.getBestTime());
										} else {
											Toast toast = Toast.makeText(MinesweeperActivity.this, R.string.game_over, Toast.LENGTH_SHORT);
											toast.show();
										}

										invalidateOptionsMenu();
										onCreateOptionsMenu(mMenu);
									}
								}
							});
						} catch (InterruptedException e) {
							Log.e(LOG_TAG, e.fillInStackTrace().toString());
						}
					}
				}
			}
		};

		isGameEndedThread.start();
	}

	/**
	 * Starts the thread in {@link GameEngine}
	 * when {@link MinesweeperActivity} is shown to the player
	 */
	@Override
	protected void onResume() {
		mRewardedVideoAd.resume(this);
		super.onResume();
		mGameEngine.resume();
		if(!mIsMenuOpened) {
			mGameEngine.setIsGamePausedFalse();
		}
	}

	/**
	 * Makes sure the thread in {@link GameEngine} is stopped
	 * when {@link MinesweeperActivity} is about to be stopped
	 */
	@Override
	protected void onPause() {
		mRewardedVideoAd.pause(this);
		super.onPause();
		saveSoundPreferences();
		if(!mIsMenuOpened) {
			mGameEngine.setIsGamePausedTrue();
		}
		mGameEngine.pause();
	}

	@Override
	protected void onDestroy() {
		mRewardedVideoAd.destroy(this);
		super.onDestroy();
		mIsGameEngineNull = true;
		mDialog.dismiss();
	}

	// Create the menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		mMenu = menu;

		// Inflate the menu
		getMenuInflater().inflate(R.menu.menu_minesweeper, menu);

		// Change menu if game is over
		if(mHasGameEnded) {
			mMenu.findItem(R.id.action_settings).setVisible(false);
			mMenu.findItem(R.id.action_help).setVisible(false);
			mMenu.findItem(R.id.action_restart).setVisible(true);
			mHasMenuChanged = true;
		}

		return super.onCreateOptionsMenu(menu);
	}

	// Handle menu methods
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int id = item.getItemId();
		if (id == R.id.action_settings) {
			mGameEngine.setIsGamePausedTrue();
			mIsMenuOpened = true;
			// Create custom mDialog object
			mDialog = new Dialog(this);
			mDialog.setContentView(R.layout.dialog_pause_menu);
			try {
				mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			} catch (NullPointerException e) {
				Log.e(LOG_TAG, e.fillInStackTrace().toString());
			}

			if (BuildConfig.PAID_VERSION) {
				TextView goPremium = mDialog.findViewById(R.id.go_premium_main);
				goPremium.setVisibility(View.GONE);
			}

			TextView volume = mDialog.findViewById(R.id.volume_control);
			if(mIsVolumeUp) {
				volume.setText(R.string.volume_down);
			} else {
				volume.setText(R.string.volume_up);
			}

			// Change menu icon
			// Open mDialog
			mMenu.getItem(2).setIcon(ContextCompat.getDrawable(MinesweeperActivity.this, R.drawable.ic_pause));
			mDialog.show();

			// Handle mDialog closing
			mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialogInterface) {
					mMenu.getItem(2).setIcon(ContextCompat.getDrawable(MinesweeperActivity.this, R.drawable.ic_menu));
					mIsMenuOpened = false;
					mGameEngine.setIsGamePausedFalse();
				}
			});

			pauseMenuManager();

			return true;
		} else if (id == R.id.action_restart) {
			mGameEngine.setIsGamePausedTrue();
			mIsMenuOpened = true;
			// Create custom mDialog object
			mDialog = new Dialog(this);
			mDialog.setContentView(R.layout.dialog_new_game_menu);
			try {
				mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			} catch (NullPointerException e) {
				Log.e(LOG_TAG, e.fillInStackTrace().toString());
			}
			if(mGameEngine.getIsGameOver()) {
				LinearLayout gameWon = mDialog.findViewById(R.id.game_won_message);
				gameWon.setVisibility(View.GONE);
				TextView goPremium = mDialog.findViewById(R.id.go_premium);
				goPremium.setVisibility(View.GONE);
				TextView rateApp = mDialog.findViewById(R.id.rate_app);
				rateApp.setVisibility(View.GONE);
				LinearLayout gameOver = mDialog.findViewById(R.id.game_over_message);
				gameOver.setVisibility(View.VISIBLE);
			}

			if (BuildConfig.PAID_VERSION) {
				TextView goPremium = mDialog.findViewById(R.id.go_premium);
				goPremium.setVisibility(View.GONE);
			}

			// Change menu icon
			// Open mDialog
			mMenu.getItem(1).setIcon(ContextCompat.getDrawable(MinesweeperActivity.this, R.drawable.ic_pause_restart));
			mDialog.show();

			// Handle mDialog closing
			mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialogInterface) {
					mMenu.getItem(1).setIcon(ContextCompat.getDrawable(MinesweeperActivity.this, R.drawable.ic_restart));
					mIsMenuOpened = false;
					mGameEngine.setIsGamePausedFalse();
				}
			});

			restartMenuManager();
		} else if (id == R.id.action_help) {
			NetworkInfo netInfo = null;
			try {
				ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
				netInfo = cm.getActiveNetworkInfo();
			} catch (NullPointerException e) {
				Log.e(LOG_TAG, e.fillInStackTrace().toString());
			}
			if(mGameEngine.checkIfHelpAvailable()) {
				if (BuildConfig.PAID_VERSION) {
					mGameEngine.showMinesTemporarily();
					mGameEngine.decreaseRemainingHelps();
				} else {
					if(netInfo != null && netInfo.isConnectedOrConnecting() && mRewardedVideoAd.isLoaded()) {
						mRewardedVideoAd.show();
						mRewardedVideoAd.setRewardedVideoAdListener(this);
					} else {
						Toast toast = Toast.makeText(MinesweeperActivity.this, R.string.no_help, Toast.LENGTH_SHORT);
						toast.show();
					}
				}
			} else {
				Toast toast = Toast.makeText(MinesweeperActivity.this, R.string.no_help, Toast.LENGTH_SHORT);
				toast.show();
			}
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onRewarded(RewardItem reward) {
	}

	@Override
	public void onRewardedVideoAdLeftApplication() {

	}

	@Override
	public void onRewardedVideoAdClosed() {
		if(mAdHasBeenWatched) {
			mGameEngine.showMinesTemporarily();
			mGameEngine.decreaseRemainingHelps();
			mAdHasBeenWatched = false;
		} else {
			Toast toast = Toast.makeText(MinesweeperActivity.this, R.string.watch_full, Toast.LENGTH_SHORT);
			toast.show();
			mRewardedVideoAd.loadAd("ca-app-pub-8167208762921452/4642253790",
					new AdRequest.Builder().build());
		}
	}

	@Override
	public void onRewardedVideoAdFailedToLoad(int errorCode) {
	}

	@Override
	public void onRewardedVideoAdLoaded() {
	}

	@Override
	public void onRewardedVideoAdOpened() {
	}

	@Override
	public void onRewardedVideoStarted() {
	}

	@Override
	public void onRewardedVideoCompleted() {
		mAdHasBeenWatched = true;
	}

	// Pause menu methods
	private void pauseMenuManager() {
		final TextView resumeTV = mDialog.findViewById(R.id.resume);
		final TextView volumeControlTV = mDialog.findViewById(R.id.volume_control);
		final TextView goPremium = mDialog.findViewById(R.id.go_premium_main);
		final TextView rateApp = mDialog.findViewById(R.id.rate_app_main);
		final TextView restart = mDialog.findViewById(R.id.in_game_restart);
		final LinearLayout difficultySelector = mDialog.findViewById(R.id.in_game_difficulty_selector);
		final ImageView easy = mDialog.findViewById(R.id.in_game_easy);
		final ImageView medium = mDialog.findViewById(R.id.in_game_medium);
		final ImageView hard = mDialog.findViewById(R.id.in_game_hard);

		resumeTV.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				mDialog.dismiss();
			}
		});

		volumeControlTV.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				TextView volume = mDialog.findViewById(R.id.volume_control);
				if(mIsVolumeUp) {
					MobileAds.setAppMuted(true);
					mIsVolumeUp = false;
					mGameEngine.setIsVolumeUp(mIsVolumeUp);
					volume.setText(R.string.volume_up);
				} else {
					MobileAds.setAppMuted(false);
					mIsVolumeUp = true;
					mGameEngine.setIsVolumeUp(mIsVolumeUp);
					volume.setText(R.string.volume_down);
				}

				mRewardedVideoAd.loadAd("ca-app-pub-8167208762921452/4642253790",
						new AdRequest.Builder().build());
			}
		});

		goPremium.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				final String appPackageName = getPackageName();
				try {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.colycodes.minesweeperrebirth.premium")));
				} catch (android.content.ActivityNotFoundException e) {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.colycodes.minesweeperrebirth.premium")));
				}
			}
		});

		rateApp.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				final String appPackageName = getPackageName();
				if (BuildConfig.PAID_VERSION) {
					try {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.colycodes.minesweeperrebirth.premium")));
					} catch (android.content.ActivityNotFoundException e) {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.colycodes.minesweeperrebirth.premium")));
					}
				} else {
					try {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.colycodes.minesweeperrebirth.free")));
					} catch (android.content.ActivityNotFoundException e) {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.colycodes.minesweeperrebirth.free")));
					}
				}
			}
		});

		restart.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				restart.setVisibility(View.GONE);
				difficultySelector.setVisibility(View.VISIBLE);
			}
		});

		easy.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				saveDifficulty(0);
				MinesweeperActivity.this.recreate();
			}
		});

		medium.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				saveDifficulty(1);
				MinesweeperActivity.this.recreate();
			}
		});

		hard.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				saveDifficulty(2);
				MinesweeperActivity.this.recreate();
			}
		});
	}

	private void restartMenuManager() {
		final TextView restart = mDialog.findViewById(R.id.game_ended_restart);
		final TextView goPremium = mDialog.findViewById(R.id.go_premium);
		final TextView rateApp = mDialog.findViewById(R.id.rate_app);
		final LinearLayout difficultySelector = mDialog.findViewById(R.id.game_ended_difficulty_selector);
		final ImageView easy = mDialog.findViewById(R.id.game_ended_easy);
		final ImageView medium = mDialog.findViewById(R.id.game_ended_medium);
		final ImageView hard = mDialog.findViewById(R.id.game_ended_hard);

		restart.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				restart.setVisibility(View.GONE);
				difficultySelector.setVisibility(View.VISIBLE);
			}
		});

		goPremium.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				final String appPackageName = getPackageName();
				try {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.colycodes.minesweeperrebirth.premium")));
				} catch (android.content.ActivityNotFoundException e) {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.colycodes.minesweeperrebirth.premium")));
				}
			}
		});

		rateApp.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				final String appPackageName = getPackageName();

				if (BuildConfig.PAID_VERSION) {
					try {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.colycodes.minesweeperrebirth.premium")));
					} catch (android.content.ActivityNotFoundException e) {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.colycodes.minesweeperrebirth.premium")));
					}
				} else {
					try {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.colycodes.minesweeperrebirth.free")));
					} catch (android.content.ActivityNotFoundException e) {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.colycodes.minesweeperrebirth.free")));
					}
				}
			}
		});

		easy.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				saveDifficulty(0);
				MinesweeperActivity.this.recreate();
			}
		});

		medium.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				saveDifficulty(1);
				MinesweeperActivity.this.recreate();
			}
		});

		hard.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				saveDifficulty(2);
				MinesweeperActivity.this.recreate();
			}
		});
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				mGameEngine.getAudioManager().adjustStreamVolume(AudioManager.STREAM_MUSIC,
						AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				mGameEngine.getAudioManager().adjustStreamVolume(AudioManager.STREAM_MUSIC,
						AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
				return true;
			default:
				return false;
		}
	}
}