package net.kopeph.ld31.graphics;

/**
 * @author alexg
 */
@FunctionalInterface
public interface PointPredicate {
	/**
	 * Called for each pixel applicable to the method.
	 */
	public boolean on(int x, int y);
}
