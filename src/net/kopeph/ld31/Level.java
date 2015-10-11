package net.kopeph.ld31;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.kopeph.ld31.entity.Enemy;
import net.kopeph.ld31.entity.Objective;
import net.kopeph.ld31.entity.Player;
import net.kopeph.ld31.graphics.Trace;
import net.kopeph.ld31.util.RouteNode;
import net.kopeph.ld31.util.Vector2;
import processing.core.PApplet;
import processing.core.PImage;

/**
 * @author stuntddude
 */
public class Level {
	//C-style enumeration of color values
	public static final int
		FLOOR_NONE    = 0xFF333333,
		FLOOR_WHITE   = 0xFFFFFFFF,
		FLOOR_BLACK   = 0xFF000000,
		FLOOR_RED     = 0xFFFF0000,
		FLOOR_GREEN   = 0xFF00FF00,
		FLOOR_BLUE    = 0xFF0000FF,
		FLOOR_CYAN    = 0xFF00FFFF,
		FLOOR_YELLOW  = 0xFFFFFF00,
		FLOOR_MAGENTA = 0xFFFF00FF;

	public final int LEVEL_WIDTH,
	                 LEVEL_HEIGHT;

	//enemies and player
	public List<Enemy> enemies = new ArrayList<>();
	public Player player;
	public Objective objective;

	public final int[] tiles;

	public Level(int width, int height) {
		PApplet context = LD31.getContext();

		LEVEL_WIDTH = width;
		LEVEL_HEIGHT = height;

		//a few adjustments to make the level properties scale somewhat with the game size
		//these are more or less just arbitrary magic numbers that are "close enough" to the desired result
		final int ROOM_COUNT = LEVEL_WIDTH*LEVEL_HEIGHT / 36000,
		          MIN_ROOM_WIDTH = 50,
		          MIN_ROOM_HEIGHT = 50,
		          MAX_ROOM_WIDTH = 150,
		          MAX_ROOM_HEIGHT = 150,

		          HALLWAY_COUNT = LEVEL_WIDTH*LEVEL_HEIGHT / 18000 + 10,
		          MIN_HALLWAY_LENGTH = LEVEL_WIDTH*LEVEL_HEIGHT / 18000,
		          MAX_HALLWAY_LENGTH = LEVEL_WIDTH*LEVEL_HEIGHT / 9000 + 200,
		          HALLWAY_SIZE = 5, //number of pixels to either side of the center of a hallway

		          VORONOI_POINTS = 50;

		tiles = new int[LEVEL_WIDTH * LEVEL_HEIGHT];

		do {
			Arrays.fill(tiles, FLOOR_NONE);

			//clear out the rooms
			for (int r = 0; r < ROOM_COUNT; ++r) {
				int rw = (int)context.random(MIN_ROOM_WIDTH, MAX_ROOM_WIDTH);
				int rh = (int)context.random(MIN_ROOM_HEIGHT, MAX_ROOM_HEIGHT);
				int rx = (int)context.random(LEVEL_WIDTH - rw - 1);
				int ry = (int)context.random(LEVEL_HEIGHT - rh - 1);

				clearRect(rx, ry, rw, rh, FLOOR_BLACK);
			}

			//clear out some hallways
			for (int i = 0; i < HALLWAY_COUNT; ++i) {
				int rx1, ry1, rx2, ry2;
				//find valid start and end points
				do {
					rx1 = (int)context.random(HALLWAY_SIZE, LEVEL_WIDTH - HALLWAY_SIZE);
					ry1 = (int)context.random(HALLWAY_SIZE, LEVEL_HEIGHT - HALLWAY_SIZE);
					rx2 = (int)context.random(HALLWAY_SIZE, LEVEL_WIDTH - HALLWAY_SIZE);
					ry2 = (int)context.random(HALLWAY_SIZE, LEVEL_HEIGHT - HALLWAY_SIZE);
				} while (Math.abs(rx2 - rx1) + Math.abs(ry2 - ry1) < MIN_HALLWAY_LENGTH ||
				         Math.abs(rx2 - rx1) + Math.abs(ry2 - ry1) > MAX_HALLWAY_LENGTH ||
				         !validTile(rx1, ry1) || !validTile(rx2, ry2));

				//clear out the tiles
				clearRect(PApplet.min(rx1, rx2) - HALLWAY_SIZE, ry1 - HALLWAY_SIZE, PApplet.abs(rx2 - rx1) + HALLWAY_SIZE*2 + 1, HALLWAY_SIZE*2 + 1, FLOOR_BLACK);
				clearRect(rx2 - HALLWAY_SIZE, PApplet.min(ry1, ry2) - HALLWAY_SIZE, HALLWAY_SIZE*2 + 1, PApplet.abs(ry2 - ry1) + HALLWAY_SIZE*2 + 1, FLOOR_BLACK);
			}

			//remove one-pixel-wide level artifacts
			for (int i = tiles.length - LEVEL_WIDTH - 1; i --> LEVEL_WIDTH;) {
				if (tiles[i] == FLOOR_NONE) {
					if (tiles[i + 1] != FLOOR_NONE && tiles[i - 1] != FLOOR_NONE)
						tiles[i] = FLOOR_BLACK;
					else if (tiles[i + LEVEL_WIDTH] != FLOOR_NONE && tiles[i - LEVEL_WIDTH] != FLOOR_NONE)
						tiles[i] = FLOOR_BLACK;
				}
			}
		} while (!validateLevel()); //keep generating new layouts until we get one that's continuous

		//create points for a voronoi diagram which will determine level coloring
		int[] posx = new int[VORONOI_POINTS];
		int[] posy = new int[VORONOI_POINTS];
		int[] colors = new int[VORONOI_POINTS];
		for (int i = 0; i < VORONOI_POINTS; ++i) {
			//assign a random position
			posx[i] = (int)context.random(LEVEL_WIDTH);
			posy[i] = (int)context.random(LEVEL_HEIGHT);

			//assign a random color
			int[] possibleColors = { FLOOR_BLACK, FLOOR_RED, FLOOR_GREEN, FLOOR_BLUE };
			colors[i] = possibleColors[(int)context.random(possibleColors.length)];
		}

		//for each pixel of floor
		for (int i = tiles.length - 1; i-- > 0;) {
			if (tiles[i] != FLOOR_NONE) {
				//assign the color of the closest voronoi point (by manhattan distance)
				int minDistance = 1000000; //arbitrarily large number
				int color = FLOOR_BLACK;
				int x = i%LEVEL_WIDTH;
				int y = i/LEVEL_WIDTH;

				for (int v = VORONOI_POINTS - 1; v --> 0;) {
					int distance = Math.abs(posx[v] - x) + Math.abs(posy[v] - y);
					if (distance < minDistance) {
						minDistance = distance;
						color = colors[v];
					}
				}

				tiles[i] = color;
			}
		}

		//add enemies
		int enemyCount = LEVEL_WIDTH*LEVEL_HEIGHT / 44100;
		for (int i = 0; i < enemyCount; ++i)
			enemies.add(new Enemy(this));

		placeEntities();
	}

	//filePath should be a plain text file containing level information
	//see level file spec for more information
	public Level(String filePath) {
		PApplet context = LD31.getContext();

		String[] lines = context.loadStrings(filePath);
		PImage img = context.loadImage(lines[0]);
		LEVEL_WIDTH = img.width;
		LEVEL_HEIGHT = img.height;
		tiles = img.pixels;

		for (String line : lines)
			parseLine(line);

		placeEntities();
	}

	//helper function for constructor
	private void parseLine(String line) {
		if (line.isEmpty()) return;
		String[] parts = line.split("\t");

		//retrieve all possible properties before determining the specifier
		//because it's simple and avoids code repetition
		int x = -1, y = -1, color = Enemy.randomColor(); //placeholder values
		List<RouteNode> route = null; //placeholder value
		for (int i = 1; i < parts.length; ++i) {
			if (parts[i].isEmpty()) continue;
			String[] pair = parts[i].split(":");

			switch (pair[0].trim().toLowerCase()) {
				case "x":
					x = Integer.parseInt(pair[1]);
					break;
				case "y":
					y = Integer.parseInt(pair[1]);
					break;
				case "color":
					color = Enemy.getColorByString(pair[1]);
					if (color == Level.FLOOR_NONE) //if the string given is invalid
						color = Enemy.randomColor();
					break;
				case "route":
				case "path":
					route = parseRoute(pair[1]);
					break;
			}
		}

		//determine how to use information based on the specifier
		switch (parts[0].trim().toLowerCase()) {
			case "player":
				player = new Player(this, x, y);
				break;
			case "objective":
				objective = new Objective(this, x, y);
				break;
			case "enemy":
				//if incomplete coordinates are given, place enemy in a random location
				//this behavior is subject to change
				if (validTile(x, y))
					enemies.add(new Enemy(this, x, y, color, route));
				else
					enemies.add(new Enemy(this, color, route));
				break;
		}
	}

	//helper function for parseLine()
	List<RouteNode> parseRoute(String in) {
		String[] coords = in.toLowerCase().split(",");
		List<RouteNode> route = new ArrayList<>(coords.length/3);
		try {
			for (int i = 0; i < coords.length; i += 3) {
				//if an OutOfBoundsException pops up here again, just pray it away apparently
				route.add(new RouteNode(new Vector2(Integer.parseInt(coords[i    ].trim()),
				                      				Integer.parseInt(coords[i + 1].trim())),
				                      	Integer.parseInt(coords[i + 2].trim().substring(1, coords[i + 2].length() - 1).trim())));
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
		if (route.size() == 0)
			return null;
		return route;
	}

	//helper function for constructors
	private void placeEntities() {
		//allow the player + objective placement to give up after so many attempts
		//this is so we don't lock up on edge cases where one of the placements can't possibly succeed
		int placementFailCount = 0;

		if (player == null) { //if we haven't already placed a player
			do {
				player = new Player(this);
				++placementFailCount;
			} while (!goodPlayerPlacement() && placementFailCount < 100);
		}

		if (objective == null) { //if we haven't already placed an objective
			placementFailCount = 0;
			do {
				objective = new Objective(this);
				++placementFailCount;
			} while (!goodObjectivePlacement() && placementFailCount < 100);
		}
	}

	//checks to make sure the level is continuous by doing a flood fill and then checking for any pixels not reached
	private boolean validateLevel() {
		for (int i = 0; i < tiles.length; ++i) {
			if (tiles[i] == FLOOR_BLACK) {
				//find the first pixel of floor and flood fill from there
				Trace.fill(i%LEVEL_WIDTH, i/LEVEL_WIDTH, (x, y) -> {
					if (!inBounds(x, y) || tiles[y*LEVEL_WIDTH + x] != FLOOR_BLACK)
						return false;
					tiles[y*LEVEL_WIDTH + x] = FLOOR_WHITE;
					return true;
				});
				break;
			}
		}

		//iterate backwards looking for unfilled tiles because it's slightly faster
		for (int i = tiles.length - 1; i --> 0;)
			if (tiles[i] == FLOOR_BLACK)
				return false;

		return true;
	}

	//helper function for constructor/room + hallway generation
	private void clearRect(int x0, int y0, int w, int h, int color) {
		for (int y = y0 + h; y --> y0;)
			Arrays.fill(tiles, y*LEVEL_WIDTH + x0, y*LEVEL_WIDTH + x0 + w, color);
	}

	//helper function for constructor/player placement
	private boolean goodPlayerPlacement() {
		for (Enemy e : enemies)
			if (PApplet.dist(e.x(), e.y(), player.x(), player.y()) < e.viewDistance)
				return false;
		return true;
	}

	//helper function for constructor/objective placement
	private boolean goodObjectivePlacement() {
		return (PApplet.dist(player.x(), player.y(), objective.x(), objective.y()) > 200); //the magic numbers are real
	}

	//returns true if an only if the coordinates are inside the level and not inside a wall
	public boolean validTile(int x, int y) {
		return (inBounds(x, y) && tiles[y*LEVEL_WIDTH + x] != FLOOR_NONE);
	}

	public boolean inBounds(int x, int y) {
		return (x > 0 && x < LEVEL_WIDTH && y > 0 && y < LEVEL_HEIGHT);
	}
}
