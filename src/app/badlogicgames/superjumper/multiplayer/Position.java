
package app.badlogicgames.superjumper.multiplayer;

public class Position {
	float x;
	float y;
	float width;
	float height;

	public Position (float x, float y, float width, float height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public float getX () {
		return x;
	}

	public float getY () {
		return y;
	}

	public float getWidth () {
		return width;
	}

	public float getHeight () {
		return height;
	}
};
