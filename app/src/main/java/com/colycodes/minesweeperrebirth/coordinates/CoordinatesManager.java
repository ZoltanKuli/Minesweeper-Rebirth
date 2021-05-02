package com.colycodes.minesweeperrebirth.coordinates;

import java.util.ArrayList;

public class CoordinatesManager {
	// Dimensions
	private int mSquareSize;
	private int mSquareNumberHorizontally;
	private int mSquareNumberVertically;
	// private int mNumberOfSquares;

	// Objects
	private ArrayList<SquareCoordinates> coordinatesList;

	// Constructor
	// Creates ArrayList for coordinatesList
	public CoordinatesManager(int squareSize, int squareNumberHorizontally,
							  int squareNumberVertically) {
		mSquareSize = squareSize;
		mSquareNumberHorizontally = squareNumberHorizontally;
		mSquareNumberVertically = squareNumberVertically;

		coordinatesList = new ArrayList<>();
		assignCoordinates();
	}

	// Assign proper coordinates to individual squares
	private void assignCoordinates() {
		for (int v = 0; v < mSquareNumberVertically; v++) {
			for (int h = 0; h < mSquareNumberHorizontally; h++) {
				int[] xCoordinates = new int[mSquareSize * mSquareSize];
				int[] yCoordinates = new int[mSquareSize * mSquareSize];
				for (int x = 0; x < mSquareSize; x++) {
					for (int y = 0; y < mSquareSize; y++) {
						xCoordinates[x * 10 + y] = x + h * mSquareSize;
						yCoordinates[x * 10 + y] = y + v * mSquareSize;
					}
				}
				SquareCoordinates squareCoordinates = new SquareCoordinates(xCoordinates,
						yCoordinates);
				coordinatesList.add(squareCoordinates);
			}
		}
	}

	// Find index of square with given coordinates
	public int findSquare(int x, int y) {
		for (SquareCoordinates coordinates: coordinatesList) {
			for (int coordinateY: coordinates.getMVerticalCoordinates()) {
				if(coordinateY == y) {
					for (int coordinateX: coordinates.getMHorizontalCoordinates()) {
						if(coordinateX == x) {
							return coordinatesList.indexOf(coordinates);
						}
					}
				}
			}
		}

		return -1;
	}
}