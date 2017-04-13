
package appwarp;

import java.util.HashMap;

import org.json.JSONObject;

import com.shephertz.app42.gaming.multiplayer.client.WarpClient;
import com.shephertz.app42.gaming.multiplayer.client.command.WarpResponseResultCode;
import com.shephertz.app42.gaming.multiplayer.client.events.RoomEvent;

public class WarpController {

	private static WarpController instance;

	private boolean showLog = true;

	// private final String apiKey = "14a611b4b3075972be364a7270d9b69a5d2b24898ac483e32d4dc72b2df039ef";
	// private final String secretKey = "55216a9a165b08d93f9390435c9be4739888d971a17170591979e5837f618059";
	private final String apiKey = "b5f6c82c5dbe48387f47bb2c285872bc5c1a1a50670f7a38750757e6c6fe9cec";
	private final String secretKey = "77cf7f7d7020856646524f5c8e551cef8d9649237d4339b2dba383ddd25d6259";

	private WarpClient warpClient;

	private String localUser;
	private String roomId;

	private boolean isConnected = false;
	boolean isUDPEnabled = false;

	private WarpListener warpListener;

	private int STATE;

	// Game state constants
	public static final int WAITING = 1;
	public static final int STARTED = 2;
	public static final int COMPLETED = 3;
	public static final int FINISHED = 4;

	// Game completed constants
	public static final int GAME_WIN = 5;
	public static final int GAME_LOOSE = 6;
	public static final int ENEMY_LEFT = 7;

	// DATest room name and owner
	public static final String ROOM_NAME = "DA_test";
	public static final String ROOM_OWNER = "jason";
	public static final int ROOM_MAX_USERS = 3;
	private int numOtherUsers = 0;
	private HashMap<String, Integer> otherUsers = new HashMap<String, Integer>();

	public WarpController () {
		initAppwarp();
		warpClient.addConnectionRequestListener(new ConnectionListener(this));
		warpClient.addChatRequestListener(new ChatListener(this));
		warpClient.addZoneRequestListener(new ZoneListener(this));
		warpClient.addRoomRequestListener(new RoomListener(this));
		warpClient.addNotificationListener(new NotificationListener(this));
	}

	public static WarpController getInstance () {
		if (instance == null) {
			instance = new WarpController();
		}
		return instance;
	}

	public void startApp (String localUser) {
		log("startApp: " + localUser); // DATest
		this.localUser = localUser;
		warpClient.connectWithUserName(localUser);
	}

	public void setListener (WarpListener listener) {
		this.warpListener = listener;
	}

	public void stopApp () {
		if (isConnected) {
			warpClient.unsubscribeRoom(roomId);
			warpClient.leaveRoom(roomId);
		}
		warpClient.disconnect();
	}

	private void initAppwarp () {
		try {
			WarpClient.initialize(apiKey, secretKey);
			warpClient = WarpClient.getInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// DATest
	// public void sendGameUpdate (String msg) {
	public void sendGameUpdate (JSONObject data) {
		try {
			if (isConnected) {
				data.put("username", localUser);
				String msg = data.toString();
				if (isUDPEnabled) {
					warpClient.sendUDPUpdatePeers((localUser + "#@" + msg).getBytes());
				} else {
					warpClient.sendUpdatePeers((localUser + "#@" + msg).getBytes());
				}
			}
		} catch (Exception e) {
			// exception in sendLocation
		}
	}

	public void updateResult (int code, String msg) {
		if (isConnected) {
			STATE = COMPLETED;
			HashMap<String, Object> properties = new HashMap<String, Object>();
			properties.put("result", code);
			warpClient.lockProperties(properties);
		}
	}

	public void onConnectDone (boolean status) {
		log("onConnectDone: " + status);
		if (status) {
			warpClient.initUDP();
			// DATest
			// warpClient.joinRoomInRange(1, 1, false);
			warpClient.joinRoomInRange(1, ROOM_MAX_USERS, false);
		} else {
			isConnected = false;
			handleError();
		}
	}

	public void onDisconnectDone (boolean status) {

	}

	public void onRoomCreated (String roomId) {
		log("onRoomCreated: " + roomId); // DATest
		if (roomId != null) {
			warpClient.joinRoom(roomId);
		} else {
			handleError();
		}
	}

	public void onJoinRoomDone (RoomEvent event) {
		log("onJoinRoomDone: " + event.getResult());
		if (event.getResult() == WarpResponseResultCode.SUCCESS) {// success case
			this.roomId = event.getData().getId();
			warpClient.subscribeRoom(roomId);
		} else if (event.getResult() == WarpResponseResultCode.RESOURCE_NOT_FOUND) {// no such room found
			HashMap<String, Object> data = new HashMap<String, Object>();
			data.put("result", "");
			// DATest
			// warpClient.createRoom("superjumper", "shephertz", 2, data);
			warpClient.createRoom(ROOM_NAME, ROOM_OWNER, ROOM_MAX_USERS, data);
		} else {
			warpClient.disconnect();
			handleError();
		}
	}

	public void onRoomSubscribed (String roomId) {
		log("onSubscribeRoomDone: " + roomId);
		if (roomId != null) {
			isConnected = true;
			warpClient.getLiveRoomInfo(roomId);
		} else {
			warpClient.disconnect();
			handleError();
		}
	}

	public void onGetLiveRoomInfo (String[] liveUsers) {
		log("onGetLiveRoomInfo: " + liveUsers.length);
		if (liveUsers != null) {
			// DATest
			// if(liveUsers.length==2){
			if (liveUsers.length == ROOM_MAX_USERS) {
				startGame();
			} else {
				waitForOtherUser();
			}
		} else {
			warpClient.disconnect();
			handleError();
		}
	}

	public void onUserJoinedRoom (String roomId, String userName) {
		/*
		 * if room id is same and username is different then start the game
		 */
		// DATest
		// if (localUser.equals(userName) == false) {
		// startGame();
		// }

		log("onUserJoinedRoom: roomId = " + roomId + " userName = " + userName + " localUser = " + localUser);

		otherUsers.put(userName, numOtherUsers++);
		warpClient.getLiveRoomInfo(roomId);
	}

	public void onSendChatDone (boolean status) {
		log("onSendChatDone: " + status);
	}

	public void onGameUpdateReceived (String message) {
		// log("onMoveUpdateReceived: " + message);
		String userName = message.substring(0, message.indexOf("#@"));
		String data = message.substring(message.indexOf("#@") + 2, message.length());

		if (!localUser.equals(userName)) {
			warpListener.onGameUpdateReceived(data);
		}
	}

	public void onResultUpdateReceived (String userName, int code) {
		if (localUser.equals(userName) == false) {
			STATE = FINISHED;
			warpListener.onGameFinished(code, true);
		} else {
			warpListener.onGameFinished(code, false);
		}
	}

	public void onUserLeftRoom (String roomId, String userName) {
		log("onUserLeftRoom " + userName + " in room " + roomId);
		if (STATE == STARTED && !localUser.equals(userName)) {// Game Started and other user left the room
			warpListener.onGameFinished(ENEMY_LEFT, true);
		}
	}

	public int getState () {
		return this.STATE;
	}

	private void log (String message) {
		if (showLog) {
			System.out.println(message);
		}
	}

	private void startGame () {
		STATE = STARTED;
		warpListener.onGameStarted("Start the Game");
	}

	private void waitForOtherUser () {
		STATE = WAITING;
		warpListener.onWaitingStarted("Waiting for other user");
	}

	private void handleError () {
		if (roomId != null && roomId.length() > 0) {
			warpClient.deleteRoom(roomId);
		}
		disconnect();
	}

	public void handleLeave () {
		if (isConnected) {
			warpClient.unsubscribeRoom(roomId);
			warpClient.leaveRoom(roomId);
			if (STATE != STARTED) {
				warpClient.deleteRoom(roomId);
			}
			warpClient.disconnect();
		}
	}

	private void disconnect () {
		warpClient.removeConnectionRequestListener(new ConnectionListener(this));
		warpClient.removeChatRequestListener(new ChatListener(this));
		warpClient.removeZoneRequestListener(new ZoneListener(this));
		warpClient.removeRoomRequestListener(new RoomListener(this));
		warpClient.removeNotificationListener(new NotificationListener(this));
		warpClient.disconnect();
	}
}
