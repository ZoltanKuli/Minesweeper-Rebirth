package com.colycodes.minesweeperrebirth;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.support.constraint.Guideline;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.colycodes.minesweeperrebirth.coordinates.CoordinatesManager;

import java.io.IOException;
import java.util.Random;

public class GameEngine extends SurfaceView implements Runnable {
	private final String LOG_TAG = GameEngine.class.getSimpleName();
	public volatile boolean mIsRunning;
	public boolean mHasGameEnded;
	// Objects for handling thread and basic functionality
	private Thread mThread;
	private Activity mActivity;
	private Context mContext;
	private Resources mResources;
	// Sound
	private AudioManager mAudioManager;
	private SoundPool mSoundPool;
	private boolean mIsVolumeUp;
	private int mPopSound;
	private int mVictorySound;
	private int mBeepSound;
	// Objects for time
	private ImageView mTimerIV;
	private TextView mTimeTV;
	private int mSecondsPast;
	private int mBestTime;
	private boolean mIsGamePaused;
	// Difficulty and result
	private int mDifficulty;
	private int mMineNumber;
	private boolean mIsGameOver;

	// Adverts
	private int mHelpsNumber;
	private int mRemainingHelps;
	private int mHelpTime;
	private boolean mIsHelpOnline;
	private boolean mSquaresShownTemporarily[];

	// Dimensions
	private int mSquareSize;
	private int mWidthInSquares;
	private int mHeightInSquares;

	// Squares to show
	private int mSquareNumber;
	private int[] mSquaresValues;
	private boolean[] mSquaresShown;
	private int[][] mBoardArray;

	// Input type and relevant information
	private ImageView mFlagsOrChecksIV;
	private TextView mFlagsOrChecksTV;
	private boolean mIsFlagging;
	private int mRemainingFlagNumber;
	private boolean[] mSquaresFlagged; // Needed for game save
	private boolean[] mWrongSquaresFlagged;
	private int mCheckedSquareNumber;

	// For handling touches on board
	private CoordinatesManager mCoordinatesManager;

	// Default constructor
	public GameEngine(Context context) {
		super(context);
	}

	// Constructor for new game (free)
	public GameEngine(Activity activity, Context context, Resources resources,
					  int difficulty, int time, Point dimensions, Toolbar toolbar, int toolbarHeight, int adHeight,
					  ImageView flagOrSearchIV, TextView flagsOrSearchNumberTV, ImageView timerIV, TextView timeTV) {
		super(context);
		mActivity = activity;
		mContext = context;
		mResources = resources;

		mDifficulty = difficulty;
		mIsGameOver = false;

		// Base dimensions
		int screenWidth = dimensions.x;
		int screenHeight = dimensions.y - toolbarHeight - adHeight;

		Guideline guideline = mActivity.findViewById(R.id.guideline);
		guideline.setGuidelineBegin(dimensions.y);

		// Determine exact dimensions for the board and the toolbar
		mWidthInSquares = determineWidthInSquares();
		mSquareSize = screenWidth / mWidthInSquares;
		int remainderHeight = screenHeight % mSquareSize;
		screenHeight = screenHeight - remainderHeight;
		mHeightInSquares = screenHeight / mSquareSize;
		toolbar.setMinimumHeight(toolbarHeight + remainderHeight);

		mTimerIV = timerIV;
		mTimeTV = timeTV;
		mBestTime = time;
		showBestTime();

		// Set objects for handling input type and showing relevant information
		mFlagsOrChecksIV = flagOrSearchIV;
		mFlagsOrChecksTV = flagsOrSearchNumberTV;
		changeInputTypeAndOutput();

		generateBoard();

		mCoordinatesManager = new CoordinatesManager(mSquareSize, mWidthInSquares, mHeightInSquares);

		generateBoardArray();

		soundSystem();
	}

	// Determine how many squares to use horizontally based on screen size and difficulty
	private int determineWidthInSquares() {
		int[] PossibleNumSquaresWide;
		if ((mResources.getConfiguration().screenLayout &
				Configuration.SCREENLAYOUT_SIZE_MASK) ==
				Configuration.SCREENLAYOUT_SIZE_SMALL) {
			PossibleNumSquaresWide = new int[]{6, 8, 10};
		} else if ((mResources.getConfiguration().screenLayout &
				Configuration.SCREENLAYOUT_SIZE_MASK) ==
				Configuration.SCREENLAYOUT_SIZE_NORMAL) {
			PossibleNumSquaresWide = new int[]{8, 10, 12};
		} else if ((mResources.getConfiguration().screenLayout &
				Configuration.SCREENLAYOUT_SIZE_MASK) ==
				Configuration.SCREENLAYOUT_SIZE_LARGE) {
			PossibleNumSquaresWide = new int[]{10, 12, 14};
		} else if ((mResources.getConfiguration().screenLayout &
				Configuration.SCREENLAYOUT_SIZE_MASK) ==
				Configuration.SCREENLAYOUT_SIZE_XLARGE) {
			PossibleNumSquaresWide = new int[]{12, 14, 16};
		} else {
			PossibleNumSquaresWide = new int[]{14, 16, 18};
		}

		return PossibleNumSquaresWide[mDifficulty];
	}

	public void run() {
		while (mIsRunning) {
			draw();
		}
	}

	public void resume() {
		mIsRunning = true;
		mThread = new Thread(this);
		mThread.start();
	}

	public void pause() {
		mIsRunning = false;
		try {
			mThread.join();
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, e.fillInStackTrace().toString());
		}
	}

	public void setIsGamePausedFalse() {
		mIsGamePaused = false;
		setCurrentTime();
	}

	public void setIsGamePausedTrue() {
		mIsGamePaused = true;
	}

	// Show seconds past since starting the game
	public void setCurrentTime() {
		Thread mTimeThread = new Thread() {
			@Override
			public void run() {
				while (!mHasGameEnded && !mIsGamePaused) {
					try {
						Thread.sleep(1000);
						mActivity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								// Don't add seconds after game is paused
								if (!mIsGamePaused && !mHasGameEnded) {
									mSecondsPast++;
									if (mSecondsPast < 10) {
										mTimeTV.setText(mResources.getString(
												R.string.default_time_1, mSecondsPast));
									} else if (mSecondsPast < 100) {
										mTimeTV.setText(mResources.getString(
												R.string.default_time_2, mSecondsPast));
									} else if (mSecondsPast < 1000) {
										mTimeTV.setText(mResources.getString(
												R.string.default_time_3, mSecondsPast));
									} else {
										mIsGameOver = true;
										mHasGameEnded = true;
									}
								}
							}
						});
					} catch (InterruptedException e) {
						Log.e(LOG_TAG, e.fillInStackTrace().toString());
					}
				}
			}
		};

		mTimeThread.start();
	}

	// Draw game
	private void draw() {
		SurfaceHolder holder = getHolder();
		Canvas canvas;
		Paint paint = new Paint();
		if(holder.getSurface().isValid()) {
			canvas = holder.lockCanvas();
			boolean isDark = false;
			int column = 0;
			int row = 0;
			for(int currentSquareNumber = 0; currentSquareNumber < mSquareNumber; currentSquareNumber++) {
				// Determine if square is dark based on if its number is even or not
				if(currentSquareNumber % 2 == 0) {
					isDark = !isDark;
				} else {
					isDark = !isDark;
				}

				// Determine whether square is dark or not
				if(currentSquareNumber % mWidthInSquares == 0 && (currentSquareNumber / mWidthInSquares) % 2 == 0) {
					isDark = false;
				} else if(currentSquareNumber % mWidthInSquares == 0 && (currentSquareNumber / mWidthInSquares) % 2 != 0) {
					isDark = true;
				}

				// Set color of board
				if(mSquaresShown[currentSquareNumber] || mSquaresShownTemporarily[currentSquareNumber]) {
					if (!isDark) {
						paint.setColor(mResources.getColor(R.color.colorBackgroundLight));
					} else {
						paint.setColor(mResources.getColor(R.color.colorBackgroundDark));
					}
				} else {
					if (!isDark) {
						paint.setColor(mResources.getColor(R.color.colorForegroundLight));
					} else {
						paint.setColor(mResources.getColor(R.color.colorForegroundDark));
					}
				}

				// Determine if underneath values should be shown
				if(!mSquaresShown[currentSquareNumber] && mSquaresFlagged[currentSquareNumber] && !mSquaresShownTemporarily[currentSquareNumber]) {
					canvas.drawRect(column * mSquareSize,
							row * mSquareSize,
							column * mSquareSize + mSquareSize,
							row * mSquareSize + mSquareSize,
							paint);

					Drawable flag = ContextCompat.getDrawable(mContext, R.drawable.ic_flag);
					flag.setBounds(column * mSquareSize,
							row * mSquareSize,
							column * mSquareSize + mSquareSize,
							row * mSquareSize + mSquareSize);
					flag.draw(canvas);

					// Cross flags that are not over mines
					if(mWrongSquaresFlagged[currentSquareNumber] && mIsGameOver) {
						Drawable bad = ContextCompat.getDrawable(mContext, R.drawable.ic_bad);
						bad.setBounds(column * mSquareSize,
								row * mSquareSize,
								column * mSquareSize + mSquareSize,
								row * mSquareSize + mSquareSize);
						bad.draw(canvas);
					}
				} else if(!mSquaresFlagged[currentSquareNumber] ||
						(mSquaresFlagged[currentSquareNumber] && mSquaresShownTemporarily[currentSquareNumber])) {
					canvas.drawRect(column * mSquareSize,
							row * mSquareSize,
							column * mSquareSize + mSquareSize,
							row * mSquareSize + mSquareSize,
							paint);
				}

				// Show proper background values if they are shown
				if(mSquaresShown[currentSquareNumber] && !mSquaresFlagged[currentSquareNumber] ||
						mSquaresShownTemporarily[currentSquareNumber]) {
					try {
						if(mSquaresValues[currentSquareNumber] == 0) {
							Drawable circle = ContextCompat.getDrawable(mContext, R.drawable.ic_mine);
							circle.setBounds(column * mSquareSize,
									row * mSquareSize,
									column * mSquareSize + mSquareSize,
									row * mSquareSize + mSquareSize);
							circle.draw(canvas);
						} else if(mSquaresValues[currentSquareNumber] == 1) {
							// System.out.println(mSquaresValues[currentSquareNumber]);
							Drawable drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_1);
							drawable.setBounds(column * mSquareSize,
									row * mSquareSize,
									column * mSquareSize + mSquareSize,
									row * mSquareSize + mSquareSize);
							drawable.draw(canvas);
						} else if(mSquaresValues[currentSquareNumber] == 2) {
							// System.out.println(mSquaresValues[currentSquareNumber]);
							Drawable drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_2);
							drawable.setBounds(column * mSquareSize,
									row * mSquareSize,
									column * mSquareSize + mSquareSize,
									row * mSquareSize + mSquareSize);
							drawable.draw(canvas);
						} else if(mSquaresValues[currentSquareNumber] == 3) {
							// System.out.println(mSquaresValues[currentSquareNumber]);
							Drawable drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_3);
							drawable.setBounds(column * mSquareSize,
									row * mSquareSize,
									column * mSquareSize + mSquareSize,
									row * mSquareSize + mSquareSize);
							drawable.draw(canvas);
						} else if(mSquaresValues[currentSquareNumber] == 4) {
							// System.out.println(mSquaresValues[currentSquareNumber]);
							Drawable drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_4);
							drawable.setBounds(column * mSquareSize,
									row * mSquareSize,
									column * mSquareSize + mSquareSize,
									row * mSquareSize + mSquareSize);
							drawable.draw(canvas);
						} else if(mSquaresValues[currentSquareNumber] == 5) {
							// System.out.println(mSquaresValues[currentSquareNumber]);
							Drawable drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_5);
							drawable.setBounds(column * mSquareSize,
									row * mSquareSize,
									column * mSquareSize + mSquareSize,
									row * mSquareSize + mSquareSize);
							drawable.draw(canvas);
						} else if(mSquaresValues[currentSquareNumber] == 6) {
							// System.out.println(mSquaresValues[currentSquareNumber]);
							Drawable drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_6);
							drawable.setBounds(column * mSquareSize,
									row * mSquareSize,
									column * mSquareSize + mSquareSize,
									row * mSquareSize + mSquareSize);
							drawable.draw(canvas);
						} else if(mSquaresValues[currentSquareNumber] == 7) {
							// System.out.println(mSquaresValues[currentSquareNumber]);
							Drawable drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_7);
							drawable.setBounds(column * mSquareSize,
									row * mSquareSize,
									column * mSquareSize + mSquareSize,
									row * mSquareSize + mSquareSize);
							drawable.draw(canvas);
						} else if(mSquaresValues[currentSquareNumber] == 8) {
							// System.out.println(mSquaresValues[currentSquareNumber]);
							Drawable drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_8);
							drawable.setBounds(column * mSquareSize,
									row * mSquareSize,
									column * mSquareSize + mSquareSize,
									row * mSquareSize + mSquareSize);
							drawable.draw(canvas);
						}
					} catch (NullPointerException e) {
						Log.e(LOG_TAG, e.fillInStackTrace().toString());
					}
				}

				// Increase row and column number when needed
				if((currentSquareNumber + 1) % mWidthInSquares == 0) {
					row++;
					column = 0;
				} else {
					column++;
				}
			}

			holder.unlockCanvasAndPost(canvas);
		}
	}

	// Check if help can be given
	public boolean checkIfHelpAvailable() {
		int squareNumber = mSquaresShown.length - mMineNumber;
		int minimumSquaresShown = squareNumber / mHelpsNumber;
		int squaresShownNumber = 0;
		for(boolean square: mSquaresShown) {
			if(square) {
				squaresShownNumber++;
			}
		}

		if(squaresShownNumber >= (mHelpsNumber - mRemainingHelps) * minimumSquaresShown) {
			return true;
		} else {
			return false;
		}
	}

	public void decreaseRemainingHelps() {
		mRemainingHelps--;
	}

	public void showMinesTemporarily() {
		mIsHelpOnline = true;
		mHelpTime = 0;
		Thread mHelpThread = new Thread() {
			@Override
			public void run() {
				while(mHelpTime <= 400) {
					try {
						Thread.sleep(10);
						mActivity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
									if (mHelpTime < 400 & !mHasGameEnded && !mIsGamePaused) {
										if(mHelpTime >= 200 && mHelpTime <= 250 || mHelpTime >= 300 && mHelpTime <= 350) {
											for(int i = 0; i < mSquaresValues.length; i++) {
												if(mSquaresValues[i] == 0) {
													mSquaresShownTemporarily[i] = false;
												}
											}
										} else {
											for(int i = 0; i < mSquaresValues.length; i++) {
												if(mSquaresValues[i] == 0) {
													mSquaresShownTemporarily[i] = true;
												}
											}
										}
										mHelpTime++;
									} else {
										for(int i = 0; i < mSquaresValues.length; i++) {
											if(mSquaresValues[i] == 0) {
												mSquaresShownTemporarily[i] = false;
											}
										}

										if(!mHasGameEnded && !mIsGamePaused) {
											mIsHelpOnline = false;
											mHelpTime++;
										}
									}
							}
						});
					} catch (InterruptedException e) {
						Log.e(LOG_TAG, e.fillInStackTrace().toString());
					}
				}
			}
		};

		mHelpThread.start();
	}

	// Generate the board according to given rules
	private void generateBoard() {
		mSquareNumber = mHeightInSquares * mWidthInSquares;
		mSquaresValues = new int[mSquareNumber];
		mSquaresShown = new boolean[mSquareNumber];
		mSquaresFlagged = new boolean[mSquareNumber];
		mWrongSquaresFlagged = new boolean[mSquareNumber];
		mSquaresShownTemporarily = new boolean[mSquareNumber];
		for (int i = 0; i < mSquareNumber; i++) {
			mSquaresValues[i] = -1;
			mSquaresShown[i] = false;
			mSquaresFlagged[i] = false;
			mWrongSquaresFlagged[i] = false;
		}

		generateMines();
		setSquareValues();
	}

	// Generate mines based on a set of rules and chance
	private void generateMines() {
		// Generate mines based on difficulty and the number of squares
		int mineNumber = 0;
		switch (mDifficulty) {
			case 0:
				mineNumber = mSquareNumber / 9;
				break;
			case 1:
				mineNumber = mSquareNumber / 6;
				break;
			case 2:
				mineNumber = mSquareNumber / 4;
				break;
		}

		// Round up or down the number of mines to a value that can be divided by five
		if ((mineNumber - ((mineNumber / 5) * 5)) < (5 / 2)) {
			mineNumber = (mineNumber / 5) * 5;
		} else if ((mineNumber - ((mineNumber / 5) * 5)) < 5) {
			mineNumber = (mineNumber / 5) * 5 + 5;
		}

		// Set starting value to remaining flags
		mRemainingFlagNumber = mineNumber;
		mMineNumber = mineNumber;

		// Determine helps that can be given
		mHelpsNumber =  mineNumber / 15 + 1;
 		mRemainingHelps = mHelpsNumber;

		// Generate mines according to a set of rules and chance
		for (int i = 0; i < mineNumber; i++) {
			boolean isGenerationDone = false;
			while (!isGenerationDone) {
				int mineSquare = new Random().nextInt(mSquareNumber - 1);
				if (mSquaresValues[mineSquare] != 0) {
					int outerMines = 0;
					int surroundingMines = 0;
					int row = 0;
					int column = 0;
					// Perform checks to see how many other mines would surround a mine
					for (int currentSquareNumber = 0; currentSquareNumber < mSquareNumber; currentSquareNumber++) {
						if (mineSquare == currentSquareNumber) {
							int bias = 1;
							// Check outer mines
							if (column > 1 && column < (mWidthInSquares - 2) && row > 1 &&
									row < (mHeightInSquares - 2)) {
								// To its left
								if (mSquaresValues[(currentSquareNumber - 2)] == 0) {
									outerMines = outerMines + bias * 2;
								}

								// To its left upper corner
								if (mSquaresValues[(currentSquareNumber - 2 - 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its left top
								if (mSquaresValues[(currentSquareNumber - 2 - mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its left bottom
								if (mSquaresValues[(currentSquareNumber - 2 + mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its left bottom corner
								if (mSquaresValues[(currentSquareNumber - 2 + 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its top
								if (mSquaresValues[(currentSquareNumber - 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias * 2;
								}

								// To its top left
								if (mSquaresValues[(currentSquareNumber - 1 - 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its top right
								if (mSquaresValues[(currentSquareNumber + 1 - 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its bottom
								if (mSquaresValues[(currentSquareNumber + 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias * 2;
								}

								// To its bottom left
								if (mSquaresValues[(currentSquareNumber - 1 + 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its bottom right
								if (mSquaresValues[(currentSquareNumber + 1 + 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its right
								if (mSquaresValues[(currentSquareNumber + 2)] == 0) {
									outerMines = outerMines + bias * 2;
								}

								// To its left upper corner
								if (mSquaresValues[(currentSquareNumber + 2 - 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its left top
								if (mSquaresValues[(currentSquareNumber + 2 - mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its left bottom
								if (mSquaresValues[(currentSquareNumber + 2 + mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}

								// To its left bottom corner
								if (mSquaresValues[(currentSquareNumber + 2 + 2 * mWidthInSquares)] == 0) {
									outerMines = outerMines + bias;
								}
							}

							bias = 2;
							// Check mines that are not located on the edge
							if (column != 0 && column != (mWidthInSquares - 1) && row != 0 &&
									row != (mHeightInSquares - 1)) {
								// To its left
								if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its left upper corner
								if (mSquaresValues[(currentSquareNumber - 1 - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its left bottom corner
								if (mSquaresValues[(currentSquareNumber - 1 + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its top
								if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its bottom
								if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right
								if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right upper corner
								if (mSquaresValues[(currentSquareNumber + 1 - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right bottom corner
								if (mSquaresValues[(currentSquareNumber + 1 + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}
							// Check mines that are on the left edge
							} else if (column == 0 && row != 0 && row != (mHeightInSquares - 1)) {
								// To its top
								if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its bottom
								if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right
								if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right upper corner
								if (mSquaresValues[(currentSquareNumber + 1 - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right bottom corner
								if (mSquaresValues[(currentSquareNumber + 1 + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}
							// Check mines that are on the right edge
							} else if (column == (mWidthInSquares - 1) && row != 0 && row != (mHeightInSquares - 1)) {
								// To its left
								if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its left upper corner
								if (mSquaresValues[(currentSquareNumber - 1 - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its left bottom corner
								if (mSquaresValues[(currentSquareNumber - 1 + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its top
								if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its bottom
								if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}
								// Check mines that are in the upper left corner
							} else if (column == 0 && row == 0 && currentSquareNumber < mWidthInSquares) {
								// To its bottom
								if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right
								if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right bottom corner
								if (mSquaresValues[(currentSquareNumber + 1 + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}
								// Check mines that are in the upper right corner
							} else if (column == (mWidthInSquares - 1) && row == 0) {
								// To its left
								if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// to its left bottom corner
								if (mSquaresValues[(currentSquareNumber - 1 + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its bottom
								if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}
								// Check mines that are in the bottom left corner
							} else if (column == 0 && row == (mHeightInSquares - 1)) {
								// To its top
								if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right
								if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right upper corner
								if (mSquaresValues[(currentSquareNumber + 1 - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}
								// Check mines that are in the bottom right corner
							} else if (column == (mWidthInSquares - 1) && row == (mHeightInSquares - 1)) {
								// To its left
								if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its upper left corner
								if (mSquaresValues[(currentSquareNumber - 1 - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// to its top
								if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;

								}
								// Check mines that are on the upper edge
							} else if (currentSquareNumber < mWidthInSquares) {
								// To its left
								if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its left bottom corner
								if (mSquaresValues[(currentSquareNumber - 1 + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its bottom
								if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right
								if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right bottom corner
								if (mSquaresValues[(currentSquareNumber + 1 + mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}
								// Check mines that are on the bottom edge
							} else if (row == (mHeightInSquares - 1)) {
								// To its left
								if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its left upper corner
								if (mSquaresValues[(currentSquareNumber - 1 - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its top
								if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right
								if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
									surroundingMines = surroundingMines + bias;
								}

								// To its right upper corner
								if (mSquaresValues[(currentSquareNumber + 1 - mWidthInSquares)] == 0) {
									surroundingMines = surroundingMines + bias;
								}
							}
						}

						// Increase row and column number
						if ((currentSquareNumber + 1) % mWidthInSquares == 0) {
							row++;
							column = 0;
						} else {
							column++;
						}
					}

					surroundingMines = surroundingMines + (int) (outerMines * 0.4);
					// Decide if mine-cluster should be made based on their probability
					int chance = new Random().nextInt(1000);
					if (surroundingMines == 0) {
						if (chance < 925) {
							mSquaresValues[mineSquare] = 0;
							isGenerationDone = true;
						}
					} else if (surroundingMines <= 2) {
						if (chance < 800) {
							mSquaresValues[mineSquare] = 0;
							isGenerationDone = true;
						}
					} else if (surroundingMines <= 4) {
						if (chance < 675) {
							mSquaresValues[mineSquare] = 0;
							isGenerationDone = true;
						}
					} else if (surroundingMines <= 6) {
						if (chance < 550) {
							mSquaresValues[mineSquare] = 0;
							isGenerationDone = true;
						}
					} else if (surroundingMines <= 8) {
						if (chance < 425) {
							mSquaresValues[mineSquare] = 0;
							isGenerationDone = true;
						}
					} else if (surroundingMines <= 10) {
						if (chance < 300) {
							mSquaresValues[mineSquare] = 0;
							isGenerationDone = true;
						}
					} else if (surroundingMines <= 12) {
						if (chance < 175) {
							mSquaresValues[mineSquare] = 0;
							isGenerationDone = true;
						}
					} else {
						if (chance < 50) {
							mSquaresValues[mineSquare] = 0;
							isGenerationDone = true;
						}
					}
				}
			}
		}
	}

	// Set square value based on how many mines surround it
	private void setSquareValues() {
		int row = 0;
		int column = 0;
		// Perform checks to see how many mines surround the square
		for (int currentSquareNumber = 0; currentSquareNumber < mSquareNumber; currentSquareNumber++) {
			int surroundingMines = 0;
			if (mSquaresValues[currentSquareNumber] != 0) {
				// Check mines that ar not located on the edge
				if (column != 0 && column != (mWidthInSquares - 1) && row != 0 &&
						row != (mHeightInSquares - 1) &&
						currentSquareNumber < mSquareNumber - mWidthInSquares) {
					// To its left
					if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
						surroundingMines++;
					}

					// To its left upper corner
					if (mSquaresValues[(currentSquareNumber - 1 - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its left bottom corner
					if (mSquaresValues[(currentSquareNumber - 1 + mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its top
					if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its bottom
					if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its right
					if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
						surroundingMines++;
					}

					// To its right upper corner
					if (mSquaresValues[(currentSquareNumber + 1 - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its right bottom corner
					if (mSquaresValues[(currentSquareNumber + 1 + mWidthInSquares)] == 0) {
						surroundingMines++;
					}
				// Check mines that are on the left edge
				} else if (column == 0 && row != 0 && row != (mHeightInSquares - 1)) {
					// To its top
					if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its bottom
					if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its right
					if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
						surroundingMines++;
					}

					// To its right upper corner
					if (mSquaresValues[(currentSquareNumber + 1 - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its right bottom corner
					if (mSquaresValues[(currentSquareNumber + 1 + mWidthInSquares)] == 0) {
						surroundingMines++;
					}
					// Check mines that are on the right edge
				} else if (column == (mWidthInSquares - 1) &&
						row != 0 && row != (mHeightInSquares - 1)) {
					// To its right
					if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
						surroundingMines++;
					}

					// To its left upper corner
					if (mSquaresValues[(currentSquareNumber - 1 - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its left bottom corner
					if (mSquaresValues[(currentSquareNumber - 1 + mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its top
					if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its bottom
					if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
						surroundingMines++;
					}
					// Check mines that are in the upper left corner
				} else if (column == 0 && row == 0 && currentSquareNumber < mWidthInSquares) {
					// To its bottom
					if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its right
					if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
						surroundingMines++;
					}

					// To its right bottom corner
					if (mSquaresValues[(currentSquareNumber + 1 + mWidthInSquares)] == 0) {
						surroundingMines++;
					}
					// Check mines that are in the upper right corner
				} else if (column == (mWidthInSquares - 1) && row == 0) {
					// To its left
					if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
						surroundingMines++;
					}

					// to its left bottom corner
					if (mSquaresValues[(currentSquareNumber - 1 + mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its bottom
					if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
						surroundingMines++;
					}
					// Check mines that are in the bottom left corner
				} else if (column == 0 && row == (mHeightInSquares - 1)) {
					// To its top
					if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its right
					if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
						surroundingMines++;
					}

					// To its right upper corner
					if (mSquaresValues[(currentSquareNumber + 1 - mWidthInSquares)] == 0) {
						surroundingMines++;
					}
					// Check mines that are in the bottom right corner
				} else if (column == (mWidthInSquares - 1) && row == (mHeightInSquares - 1)) {
					// To its left
					if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
						surroundingMines++;
					}

					// To its upper left corner
					if (mSquaresValues[(currentSquareNumber - 1 - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// to its top
					if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
						surroundingMines++;

					}
					// Check mines that are on the upper edge
				} else if (currentSquareNumber < mWidthInSquares) {
					// To its left
					if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
						surroundingMines++;
					}

					// To its left bottom corner
					if (mSquaresValues[(currentSquareNumber - 1 + mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its bottom
					if (mSquaresValues[(currentSquareNumber + mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its right
					if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
						surroundingMines++;
					}

					// To its right bottom corner
					if (mSquaresValues[(currentSquareNumber + 1 + mWidthInSquares)] == 0) {
						surroundingMines++;
					}
					// Check mines that are on the bottom edge
				} else if (row == (mHeightInSquares - 1)) {
					// To its left
					if (mSquaresValues[(currentSquareNumber - 1)] == 0) {
						surroundingMines++;
					}

					// To its left upper corner
					if (mSquaresValues[(currentSquareNumber - 1 - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its top
					if (mSquaresValues[(currentSquareNumber - mWidthInSquares)] == 0) {
						surroundingMines++;
					}

					// To its right
					if (mSquaresValues[(currentSquareNumber + 1)] == 0) {
						surroundingMines++;
					}

					// To its right upper corner
					if (mSquaresValues[(currentSquareNumber + 1 - mWidthInSquares)] == 0) {
						surroundingMines++;
					}
				}
			}

			// Set number of surrounding mines
			if (surroundingMines != 0) {
				mSquaresValues[currentSquareNumber] = surroundingMines;
			}

			// Increase row and column number
			if((currentSquareNumber + 1) % mWidthInSquares == 0) {
				row++;
				column = 0;
			} else {
				column++;
			}
		}
	}

	// Change input type and show relevant information
	private void changeInputTypeAndOutput() {
		mIsFlagging = false;
		mFlagsOrChecksIV.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				// Determine the proper input type
				if (!mIsFlagging) {
					mFlagsOrChecksIV.setImageResource(R.drawable.ic_flag);
					if (mRemainingFlagNumber < 10) {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_1, mRemainingFlagNumber));
					} else if (mRemainingFlagNumber < 100) {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_2, mRemainingFlagNumber));
					} else {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_3, mRemainingFlagNumber));
					}
					mIsFlagging = true;
				} else {
					mFlagsOrChecksIV.setImageResource(R.drawable.ic_search);
					if (mCheckedSquareNumber < 10) {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_1, mCheckedSquareNumber));
					} else if (mCheckedSquareNumber < 100) {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_2, mCheckedSquareNumber));
					} else {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_3, mCheckedSquareNumber));
					}
					mIsFlagging = false;
				}
			}
		});

		mFlagsOrChecksTV.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				// Determine the proper input type
				if (!mIsFlagging) {
					mFlagsOrChecksIV.setImageResource(R.drawable.ic_flag);
					if (mRemainingFlagNumber < 10) {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_1, mRemainingFlagNumber));
					} else if (mRemainingFlagNumber < 100) {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_2, mRemainingFlagNumber));
					} else {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_3, mRemainingFlagNumber));
					}
					mIsFlagging = true;
				} else {
					mFlagsOrChecksIV.setImageResource(R.drawable.ic_search);
					if (mCheckedSquareNumber < 10) {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_1, mCheckedSquareNumber));
					} else if (mCheckedSquareNumber < 100) {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_2, mCheckedSquareNumber));
					} else {
						mFlagsOrChecksTV.setText(mResources.getString(
								R.string.default_flags_or_checks_3, mCheckedSquareNumber));
					}
					mIsFlagging = false;
				}
			}
		});
	}

	public int getBestTime() {
		if(mHasGameEnded && !mIsGameOver && mSecondsPast <= mBestTime ||
				mHasGameEnded && !mIsGameOver && mBestTime == 0) {
			return mSecondsPast;
		} else {
			return mBestTime;
		}
	}

	private void showBestTime() {
		mTimerIV.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String bestTime;
				if(!mIsGameOver && mHasGameEnded && mSecondsPast < mBestTime ||
						!mIsGameOver && mHasGameEnded && mBestTime == 0) {
					if (mSecondsPast < 10) {
						bestTime = mResources.getString(
								R.string.best_time_1, mSecondsPast);
					} else if (mSecondsPast < 100) {
						bestTime = mResources.getString(
								R.string.best_time_2, mSecondsPast);
					} else {
						bestTime = mResources.getString(
								R.string.best_time_3, mSecondsPast);
					}
				} else {
					if (mBestTime == 0) {
						bestTime = mResources.getString(
								R.string.best_time_default);
					} else if (mBestTime < 10) {
						bestTime = mResources.getString(
								R.string.best_time_1, mBestTime);
					} else if (mBestTime < 100) {
						bestTime = mResources.getString(
								R.string.best_time_2, mBestTime);
					} else {
						bestTime = mResources.getString(
								R.string.best_time_3, mBestTime);
					}
				}

				Toast toast = Toast.makeText(mActivity, bestTime, Toast.LENGTH_SHORT);
				toast.show();
			}
		});

		mTimeTV.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String bestTime;
				if(!mIsGameOver && mHasGameEnded && mSecondsPast < mBestTime ||
						!mIsGameOver && mHasGameEnded && mBestTime == 0) {
					if (mSecondsPast < 10) {
						bestTime = mResources.getString(
								R.string.best_time_1, mSecondsPast);
					} else if (mSecondsPast < 100) {
						bestTime = mResources.getString(
								R.string.best_time_2, mSecondsPast);
					} else {
						bestTime = mResources.getString(
								R.string.best_time_3, mSecondsPast);
					}
				} else {
					if (mBestTime == 0) {
						bestTime = mResources.getString(
								R.string.best_time_default);
					} else if (mBestTime < 10) {
						bestTime = mResources.getString(
								R.string.best_time_1, mBestTime);
					} else if (mBestTime < 100) {
						bestTime = mResources.getString(
								R.string.best_time_2, mBestTime);
					} else {
						bestTime = mResources.getString(
								R.string.best_time_3, mBestTime);
					}
				}

				Toast toast = Toast.makeText(mActivity, bestTime, Toast.LENGTH_SHORT);
				toast.show();
			}
		});
	}

	public AudioManager getAudioManager() {
		return mAudioManager;
	}

	public void setIsVolumeUp(boolean isVolumeUp) {
		mIsVolumeUp = isVolumeUp;
	}

	// Load the sounds
	private void soundSystem() {
		if (Build.VERSION.SDK_INT >= 21 ) {
			AudioAttributes audioAttributes = new AudioAttributes.Builder()
					.setUsage(AudioAttributes.USAGE_GAME)
					.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
					.build();

			SoundPool.Builder builder= new SoundPool.Builder();
			builder.setAudioAttributes(audioAttributes);

			mSoundPool = builder.build();

			mAudioManager = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
		}
		try {
			// Create objects of the two required classes
			AssetManager assetManager = mContext.getAssets();
			AssetFileDescriptor descriptor;

			// Prepare the two sounds in memory
			descriptor = assetManager.openFd("pop.ogg");
			mPopSound = mSoundPool.load(descriptor, 0);
			descriptor = assetManager.openFd("victory.ogg");
			mVictorySound = mSoundPool.load(descriptor, 0);
			descriptor = assetManager.openFd("beep.ogg");
			mBeepSound = mSoundPool.load(descriptor, 0);
		} catch (IOException e) {
			Log.e(LOG_TAG, e.fillInStackTrace().toString());
		}

	}

	private void playPop()  {
		if(mIsVolumeUp) {
			mSoundPool.play(mPopSound, 1, 1, 0, 0, 1);
		}
	}

	private void playFlag()  {
		if(mIsVolumeUp) {
			mSoundPool.play(mPopSound, 1, 1, 0, 0, 1);
		}
	}

	private void playVictory() {
		if(mIsVolumeUp) {
			mSoundPool.play(mVictorySound, 1, 1, 0, 0, 1);
		}
	}

	private void playBeep()  {
		if(mIsVolumeUp) {
			mSoundPool.play(mBeepSound, 1, 1, 0, 0, 1);
		}
	}

	public boolean getIsGameOver() {
		return mIsGameOver;
	}

	public boolean getHasGameEnded() {
		return mHasGameEnded;
	}

	// Handle touches on board
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int x = (int)event.getX();
		int y = (int)event.getY();
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				int CurrentSquareNumber = mCoordinatesManager.findSquare(x, y);
				if (!mHasGameEnded && CurrentSquareNumber < mSquareNumber && CurrentSquareNumber >= 0 && !mIsHelpOnline) {
					// Check the square
					if(!mIsFlagging && !mSquaresFlagged[CurrentSquareNumber] && !mSquaresShown[CurrentSquareNumber]) {
						mCheckedSquareNumber++;
						// Set squares checked text view
						if (mCheckedSquareNumber < 10) {
							mFlagsOrChecksTV.setText(mResources.getString(
									R.string.default_flags_or_checks_1, mCheckedSquareNumber));
						} else if (mCheckedSquareNumber < 100) {
							mFlagsOrChecksTV.setText(mResources.getString(
									R.string.default_flags_or_checks_2, mCheckedSquareNumber));
						} else {
							mFlagsOrChecksTV.setText(mResources.getString(
									R.string.default_flags_or_checks_3, mCheckedSquareNumber));
						}

						// If square is empty, execute algorithm to show all relevant empty squares
						if(mSquaresValues[CurrentSquareNumber] == -1) {
							playPop();
							getEmptySquares(CurrentSquareNumber % mWidthInSquares, CurrentSquareNumber / mWidthInSquares);
							int i = 0;
							for(int h = 0; h < mHeightInSquares; h++) {
								for (int w = 0; w < mWidthInSquares; w++) {
									if (mBoardArray[h][w] == -2) {
										mSquaresShown[i] = true;
										if(mSquaresFlagged[i]) {
											mSquaresFlagged[i] = false;
											mRemainingFlagNumber++;
										}
									} else if (mBoardArray[h][w] == -3) {
										mSquaresShown[i] = true;
										if(mSquaresFlagged[i]) {
											mSquaresFlagged[i] = false;
											mRemainingFlagNumber++;
										}
									}
									i++;
								}
							}
						}

						// Normal case, no empty squares, normal checks
						// Determine if game has ended
						// Determine if it's game over
						if(mSquaresValues[CurrentSquareNumber] != 0) {
							mSquaresShown[CurrentSquareNumber] = true;
							int squaresShown = 0;
							for(boolean squareShown: mSquaresShown) {
								if(squareShown) {
									squaresShown++;
								}
							}

							if(squaresShown == mSquareNumber - mMineNumber) {
								playVictory();
								mHasGameEnded = true;
								mIsGameOver = false;
							} else {
								playPop();
							}
						} else {
							playBeep();
							mHasGameEnded = true;
							mIsGameOver = true;

							for (int i = 0; i < mSquareNumber; i++) {
								if (!mSquaresFlagged[i]) {
									mSquaresShown[i] = true;
								}
							}
						}
					} else if (mIsFlagging) {
						// Determine if squares are already flagged and execute accordingly
						if (!mSquaresFlagged[CurrentSquareNumber] &&
								!mSquaresShown[CurrentSquareNumber] &&
								mRemainingFlagNumber != 0) {
							playFlag();
							mRemainingFlagNumber--;
							mSquaresFlagged[CurrentSquareNumber] = true;
							if (mRemainingFlagNumber < 10) {
								mFlagsOrChecksTV.setText(mResources.getString(
										R.string.default_flags_or_checks_1, mRemainingFlagNumber));
							} else if (mRemainingFlagNumber < 100) {
								mFlagsOrChecksTV.setText(mResources.getString(
										R.string.default_flags_or_checks_2, mRemainingFlagNumber));
							} else {
								mFlagsOrChecksTV.setText(mResources.getString(
										R.string.default_flags_or_checks_3, mRemainingFlagNumber));
							}

							if(mSquaresValues[CurrentSquareNumber] != 0) {
								mWrongSquaresFlagged[CurrentSquareNumber] = true;
							}
						} else if (!mSquaresShown[CurrentSquareNumber] &&
								mSquaresFlagged[CurrentSquareNumber]) {
							playFlag();
							mRemainingFlagNumber++;
							mSquaresFlagged[CurrentSquareNumber] = false;
							if (mRemainingFlagNumber < 10) {
								mFlagsOrChecksTV.setText(mResources.getString(
										R.string.default_flags_or_checks_1, mRemainingFlagNumber));
							} else if (mRemainingFlagNumber < 100) {
								mFlagsOrChecksTV.setText(mResources.getString(
										R.string.default_flags_or_checks_2, mRemainingFlagNumber));
							} else {
								mFlagsOrChecksTV.setText(mResources.getString(
										R.string.default_flags_or_checks_3, mRemainingFlagNumber));
							}

							mWrongSquaresFlagged[CurrentSquareNumber] = false;
						}
					}
				}
		}
		return false;
	}

	// Two dimensional array to be used for empty square algorithm
	private void generateBoardArray() {
		mBoardArray = new int[mSquareNumber][mSquareNumber];
		int i = 0;
		for(int h = 0; h < mHeightInSquares; h++) {
			for (int w = 0; w < mWidthInSquares; w++) {
				mBoardArray[h][w] = mSquaresValues[i];
				i++;
			}
		}
	}

	// Flood fill algorithm to find relevant empty squares
	private void getEmptySquares(int x, int y) {
		if(x < 0) return;
		if(y < 0) return;
		if(x > mWidthInSquares) return;
		if(y > mHeightInSquares) return;

		int oldValue = -1;
		int newValue = -2;

		if(newValue == mBoardArray[y][x]) return;
		// Also set surrounding squares to be shown
		if(oldValue != mBoardArray[y][x]) {
			mBoardArray[y][x] = -3;
			if(x - 1 >= 0 &&
					mBoardArray[y][x - 1] != 0 &&
					mBoardArray[y][x - 1] != -1 &&
					mBoardArray[y][x - 1] != -2 &&
					mBoardArray[y][x - 1] != -3) {
				mBoardArray[y][x - 1] = -3;
			} else if(y + 1 < mWidthInSquares &&
					mBoardArray[y][x + 1] != 0 &&
					mBoardArray[y][x + 1] != -1 &&
					mBoardArray[y][x + 1] != -2 &&
					mBoardArray[y][x + 1] != -3) {
				mBoardArray[y][x + 1] = -3;
			} else if(y - 1 >= 0 &&
					mBoardArray[y - 1][x] != 0 &&
					mBoardArray[y - 1][x] != -1 &&
					mBoardArray[y - 1][x] != -2 &&
					mBoardArray[y - 1][x] != -3) {
				mBoardArray[y - 1][x] = -3;
			} else if(y + 1 < mHeightInSquares &&
					mBoardArray[y + 1][x] != 0 &&
					mBoardArray[y + 1][x] != -1 &&
					mBoardArray[y + 1][x] != -2 &&
					mBoardArray[y + 1][x] != -3) {
				mBoardArray[y + 1][x] = -3;
			}
			return;
		}

		mBoardArray[y][x] = newValue;

		getEmptySquares(x - 1, y);
		getEmptySquares(x + 1, y);
		getEmptySquares(x, y - 1);
		getEmptySquares(x, y + 1);
	}
}