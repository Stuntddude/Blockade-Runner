package net.kopeph.ld31;

import java.util.HashMap;
import java.util.Map;

import net.kopeph.ld31.graphics.Font;
import net.kopeph.ld31.spi.Interaction;
import processing.core.PConstants;

/**
 * @author stuntddude
 */
public class InputHandler {
	public static final int
		UP       = 0,
		LEFT     = 2,
		DOWN     = 1,
		RIGHT    = 3,
		RESTART  = 4,
		PAUSE    = 5,
		ESCAPE   = 6,
		UNCAUGHT = 7;
	
	//there may be a better way to do this
	public static String getControlString(int controlCode) {
		switch (controlCode) {
			case UP     : return "UP     ";
			case LEFT   : return "LEFT   ";
			case DOWN   : return "DOWN   ";
			case RIGHT  : return "RIGHT  ";
			case RESTART: return "RESTART";
			case PAUSE  : return "PAUSE  ";
			case ESCAPE : return "ESCAPE ";
		}
		return "UNCAUGHT";
	}
	
	public static int[][] bindings = {
		{ UP     , 'W', '8' , PConstants.UP    << 16 },
		{ LEFT   , 'A', '4' , PConstants.LEFT  << 16 },
		{ DOWN   , 'S', '2' , PConstants.DOWN  << 16 },
		{ RIGHT  , 'D', '6' , PConstants.RIGHT << 16 },
		{ RESTART, 'R', ' ' , PConstants.ENTER << 16 },
		{ PAUSE  , 'P', '\t', PConstants.TAB   << 16 },
		{ ESCAPE ,  0 ,  0  , PConstants.ESC   << 16 }
	};
	
	private static boolean[] pressed = new boolean[bindings.length];
	
	private static Map<Integer, Interaction> behaviors = new HashMap<>();
	
	public static void handleInput(int key, int keyCode, boolean down) {
		if (key == 0 || (key == PConstants.CODED && keyCode == 0)) return; //just to be safe, like, you never know, y'know?
		
		key = Character.toUpperCase(key); //make input case insensitive
		
		//the canonical values of PApplet.keyCode overlap with the ASCII set, which is annoying and means we have to do a bit shift
		if ((keyCode & 0xFFFF0000) != 0) return; //no truncation
		keyCode = keyCode << 16; //key is originally a char, so this way we clear any possibility of overlap
		
		for (int i = 0; i < bindings.length; ++i) {
			for (int j = 1; j < bindings[i].length; ++j) {
				if (bindings[i][j] == key || bindings[i][j] == keyCode) {
					setPressed(i, j, down);
					//currently only acts on keyDown events
					if (behaviors.containsKey(bindings[i][0]) && down)
						behaviors.get(bindings[i][0]).interact();
					else if (behaviors.containsKey(UNCAUGHT))
						behaviors.get(UNCAUGHT).interact();
					return;
				}
			}
		}
		
		if (behaviors.containsKey(UNCAUGHT))
			behaviors.get(UNCAUGHT).interact();
	}
	
	private static void setPressed(int i, int j, boolean down) {
		pressed[i] = down; //this is going to be more complicated later
	}
	
	public static boolean isPressed(int keyConstant) {
		//now searches for keyConstant in the bindings instead of assuming that it will map correctly to the array index
		for (int i = 0; i < bindings.length; ++i)
			if (bindings[i][0] == keyConstant)
				return pressed[i];
		return false;
	}
	
	public static void addBehavior(int keyConstant, Interaction behavior) {
		//TODO: find a way to differentiate between key-down and key-up events
		behaviors.put(keyConstant, behavior);
	}
	
	public static void removeBehavior(int key) {
		behaviors.remove(key);
	}
	
	public static String toKey(int keyCode) {
		String ret = toKeyImpl(keyCode); //we don't know if this is coming to us already shifted or not, so try normal first
		if (ret != "") return ret;
		ret = toKeyImpl(keyCode >> 16); //if it fails that test, it's probably left-shifted, so right-shift and try again
		if (ret != "") return ret;
		return "---"; //key not found
	}
	
	private static String toKeyImpl(int keyCode) {
		switch(keyCode) {
			case ' ':					return "SPACE";
			case PConstants.BACKSPACE:	return "BACKSP";
			case PConstants.TAB:		return "TAB";
			case PConstants.ESC:		return "ESC";
			case PConstants.ENTER:		return "ENTER";
			case PConstants.SHIFT:		return "SHIFT";
			case PConstants.RIGHT:		return String.valueOf((char)Font.ARROW_RIGHT);
			case PConstants.LEFT:		return String.valueOf((char)Font.ARROW_LEFT );
			case PConstants.UP:			return String.valueOf((char)Font.ARROW_UP   );
			case PConstants.DOWN:		return String.valueOf((char)Font.ARROW_DOWN );
		}
		
		if (keyCode > 0 && keyCode < 128)
			return String.valueOf((char)keyCode);
		return ""; //no match
	}
}
