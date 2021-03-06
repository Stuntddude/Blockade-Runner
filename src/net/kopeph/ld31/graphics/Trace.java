package net.kopeph.ld31.graphics;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Deque;

import net.kopeph.ld31.spi.PointPredicate;

/**
 * @author alexg
 * @author stuntddude
 */
public class Trace {
	private Trace() {
		throw new AssertionError("No Instantiation of: " + getClass().getName()); //$NON-NLS-1$
	}

	/**
	 * Traces along a line.
	 * Source: http://en.wikipedia.org/wiki/Bresenham's_line_algorithm#Simplification
	 * @return true if the end of the line is reached, false if it is stopped partway
	 */
	public static boolean line(int x1, int y1, int x2, int y2, PointPredicate op) {
		int sx, sy, e2;
		int dx =  Math.abs(x2-x1);
		int dy = -Math.abs(y2-y1); //we calculate -dx in the first place to possibly save a clock cycle down there

		sx = x1 < x2? 1 : -1;
		sy = y1 < y2? 1 : -1;

		int err = dx + dy;

		while (true) {
			if (!op.on(x1,y1)) return false;
			if (x1 == x2 && y1 == y2) return true;
			e2 = 2*err;
			if (e2 > dy) { //down here is where I mean
				err += dy;
				x1 += sx;
			}
			if (e2 < dx) {
				err += dx;
				y1 += sy;
			}
		}
	}

	/** A version of line() that doesn't check to stop if it's reached the end point */
	public static void ray(int x1, int y1, int x2, int y2, PointPredicate op) {
		if (x1 == x2 && y1 == y2) return; //avoid infinite loop if starting position is ending position

		int sx, sy, e2;
		int dx =  Math.abs(x2-x1);
		int dy = -Math.abs(y2-y1); //we calculate -dx in the first place to possibly save a clock cycle down there

		sx = x1 < x2? 1 : -1;
		sy = y1 < y2? 1 : -1;

		int err = dx + dy;

		while (true) {
			if (!op.on(x1,y1)) return;
			e2 = 2*err;
			if (e2 > dy) { //down here is where I mean
				err += dy;
				x1 += sx;
			}
			if (e2 < dx) {
				err += dx;
				y1 += sy;
			}
		}
	}

	/** Source: http://en.wikipedia.org/wiki/Midpoint_circle_algorithm#Example */
	public static void circle(int x0, int y0, int radius, PointPredicate op) {
		int x = radius;
		int y = 0;
		int radiusError = 1-x;

		while(x >= y) {
			circlePointImpl(x, y, x0, y0, op);
			y++;
			if (radiusError<0) {
				radiusError += 2 * y + 1;
			}
			else {
				x--;
				radiusError += 2 * (y - x + 1);
			}
		}
	}

	private static void circlePointImpl(int x, int y, int x0, int y0, PointPredicate op) {
		op.on( x + x0,  y + y0); op.on( y + x0,  x + y0);
		op.on(-x + x0,  y + y0); op.on(-y + x0,  x + y0);
		op.on(-x + x0, -y + y0); op.on(-y + x0, -x + y0);
		op.on( x + x0, -y + y0); op.on( y + x0, -x + y0);
	}

	/** Pseudo-recursive generic flood fill algorithm */
	public static void fill(int x, int y, PointPredicate op) {
		//using a Deque (stack) to simulate recursion
		Deque<Point> points = new ArrayDeque<>();
		points.push(new Point(x, y));

		while (points.size() != 0) {
			Point p = points.pop();

			if (op.on(p.x    , p.y + 1)) points.push(new Point(p.x    , p.y + 1));
			if (op.on(p.x    , p.y - 1)) points.push(new Point(p.x    , p.y - 1));
			if (op.on(p.x + 1, p.y    )) points.push(new Point(p.x + 1, p.y    ));
			if (op.on(p.x - 1, p.y    )) points.push(new Point(p.x - 1, p.y    ));
		}
	}
}
