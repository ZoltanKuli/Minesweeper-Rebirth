package com.colycodes.minesweeperrebirth.coordinates;

import java.util.ArrayList;

public class SquareCoordinates {
	// Store coordinates
	private ArrayList<Integer> mHorizontalCoordinates;
	private ArrayList<Integer> mVerticalCoordinates;

	// Constructor
	// Load coordinates into ArrayLists
	public SquareCoordinates(int[] horizontalCoordinates, int[] verticalCoordinates) {
		mHorizontalCoordinates = new ArrayList<>();
		for (int c: horizontalCoordinates) {
			mHorizontalCoordinates.add(c);
		}

		mVerticalCoordinates = new ArrayList<>();
		for (int c: verticalCoordinates) {
			mVerticalCoordinates.add(c);
		}
	}

	// Getter for mHorizontalCoordinates
	public ArrayList<Integer> getMHorizontalCoordinates() {
		return mHorizontalCoordinates;
	}

	// Getter for mVerticalCoordinates
	public ArrayList<Integer> getMVerticalCoordinates() {
		return mVerticalCoordinates;
	}
}