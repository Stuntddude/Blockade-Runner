package net.kopeph.ld31;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kopeph.ld31.graphics.Font;
import net.kopeph.ld31.spi.Interaction;
import net.kopeph.ld31.spi.KeyPredicate;
import processing.core.PConstants;

/**
 * Do NOT send PConstants values to keyId parameters. use Input.K_* values instead.
 * @author alexg
 */
//TODO: make addAction() and handleBind() read and write to a config.
public class Input {
	public static final int
		K_UNBOUND = -1, //These fill both halves of the word, so they can't collide
		K_BINDING = -2,

		K_ESC     = PConstants.ESC,
		K_TAB     = PConstants.TAB,
		K_ENTER   = PConstants.ENTER,
		K_BKSP    = PConstants.BACKSPACE,
		K_SHIFT   = PConstants.SHIFT   << 16,
		K_CTRL    = PConstants.CONTROL << 16,
		K_ALT     = PConstants.ALT     << 16,
		K_UP      = PConstants.UP      << 16,
		K_DOWN    = PConstants.DOWN    << 16,
		K_LEFT    = PConstants.LEFT    << 16,
		K_RIGHT   = PConstants.RIGHT   << 16;

	private Map<Integer, Boolean> keyStates = new HashMap<>();

	private Map<String, KeyAction> actions = new HashMap<>();
	private Map<Integer, KeyAction> keyMap = new HashMap<>();
	private List<KeyPredicate> triggers = new ArrayList<>();


	public void eventKey(char pKey, int pKeyCode, boolean isDown) {
		//Need to blacklist/transform a key from processing? do it here.

		//These are the same thing on the keyboard...
		if (pKey == PConstants.RETURN)
			pKey = PConstants.ENTER;

		if (pKeyCode > 0xFFFF)
			//this breaks the 1:1 assumption described below, so nix it
			return;

		//ALL CAPS FOR CASE INSENSITIVITY
		pKey = Character.toUpperCase(pKey);

		//chars (pKey) are only 16 bits in size, and all pKeyCodes that we care about
		//will also fit into 16 bits, so for simplicity in other parts of the code,
		//we are squashing these together into an int (0xAAAABBBB) A: pKeyCode B: pKey
		//We only care about the A values if B says we should, so really keyId =
		//0x0000BBBB when B != CODED
		//0xAAAA0000 when A == CODED
		int keyId = (pKey == PConstants.CODED) ? pKeyCode << 16 : pKey;

		keyStates.put(keyId, isDown);

		if (isDown) {
			//Triggers take precedence
			for (int i = 0; i < triggers.size(); i++) {
				if (triggers.get(i).press(keyId)) {
					triggers.remove(i);
					return;
				}
			}
			if (keyMap.containsKey(keyId))
				keyMap.get(keyId).lambda.interact();
		}
	}

	public void addAction(String id, int[] keyIds, Interaction lambda) {
		KeyAction action = new KeyAction(id, keyIds, lambda);
		actions.put(id, action);
		for (int keyId : keyIds)
			keyMap.put(keyId, action);
	}

	public void addMonitor(String id, int[] keyIds) {
		addAction(id, keyIds, () -> { /* no action */ });
	}

	/**
	 * Returns a mapping between ids and a list of button titles for the settings menu.
	 */
	public Map<String, List<String>> keyMap() {
		Map<String, List<String>> retMap = new HashMap<>();

		//KeyMap output
		for (KeyAction action : keyMap.values())
			retMap.put(action.id, action.keyIdTitles());

		return retMap;
	}

	public static String getKeyTitle(int keyId) {
		if (keyId >= 0x21 && keyId <= 0xFFFF) //UTF-16 range minus SPACE and control codes
			return String.valueOf((char) keyId);
		switch (keyId) {
		case ' '      : return "SPACE";
		case K_UNBOUND: return "---";
		case K_BINDING: return "<???>";
		case K_ESC    : return "ESC";
		case K_TAB    : return "TAB";
		case K_ENTER  : return "ENTER";
		case K_SHIFT  : return "SHIFT";
		case K_CTRL   : return "CTRL";
		case K_ALT    : return "ALT";
		case K_UP     : return String.valueOf(Font.ARROW_UP);
		case K_DOWN   : return String.valueOf(Font.ARROW_DOWN);
		case K_LEFT   : return String.valueOf(Font.ARROW_LEFT);
		case K_RIGHT  : return String.valueOf(Font.ARROW_RIGHT);
		}

		//well shit, we got a weird key
		//Should be distinct from others, so we can catch bugs easier
		return "!!!";
	}

	public static List<String> getKeyTitle(List<Integer> keyIds) {
		List<String> retList = new ArrayList<>();
		for (Integer keyId : keyIds)
			retList.add(getKeyTitle(keyId));
		return retList;
	}

	public boolean getKey(int keyId) {
		if (keyStates.containsKey(keyId))
			return keyStates.get(keyId);
		return false;
	}

	public boolean getKey(String id) {
		for (Integer keyId : actions.get(id).keyIds) {
			if (getKey(keyId))
				return true;
		}
		return false;
	}

	public void handleBind(String id, final int index, final String escapeId) {
		final KeyAction action = actions.get(id);

		if (action != null) {
			//Remove the binding from keyMap, then set the keyId in action to ???
			while (index >= action.keyIds.size())
				action.keyIds.add(K_UNBOUND); //expand list as needed

			keyMap.remove(action.keyIds.get(index));
			action.keyIds.set(index, K_BINDING);

			//Wait until a key is pressed, and lock onto it
			postTrigger((keyId) -> {
				if (getKey(escapeId)) {
					//change from ??? to ---
					action.keyIds.set(index, K_UNBOUND);
				}
				else {
					//Update internal key array of clobbered mapping if applicable
					KeyAction clobbered = keyMap.remove(keyId);
					if (clobbered != null)
						unlinkMapping(clobbered, keyId);

					//Then add the new value
					action.keyIds.set(index, keyId);
					keyMap.put(keyId, action);
				}
				//Capture and remove
				return true;
			});
		}
		else
			throw new IllegalArgumentException("id=" + id);
	}

	private static void unlinkMapping(KeyAction action, int keyId) {
		for (int i = 0; i < action.keyIds.size(); i++) {
			if (action.keyIds.get(i) == keyId) {
				action.keyIds.set(i, K_UNBOUND);
				break;
			}
		}
	}

	/**
	 * Predicate should return true to remove from the handler,
	 * or false to keep it in, and allow it to propagate.
	 */
	public void postTrigger(KeyPredicate action) {
		triggers.add(action);
	}

	private static class KeyAction {
		public final String id;
		public final Interaction lambda;
		public final List<Integer> keyIds = new ArrayList<>();

		public KeyAction(String id, int[] keyIds, Interaction lambda) {
			this.id = id;
			this.lambda = lambda;
			for (int keyId : keyIds)
				this.keyIds.add(keyId);
		}

		public List<String> keyIdTitles() {
			List<String> retList = new ArrayList<>();

			for(int keyId : keyIds)
				retList.add(getKeyTitle(keyId));

			return retList;
		}
	}
}