public class GameLogic implements IGameLogic {
    // Width of the board
    private int width;
    // Height of the board
    private int height;
    private int height1;
    private int height2;

    private int size;
    private static final int MAX_SIZE = 64;

    // The current player's id
    private int player;
    private static final int EMPTY   = 0;
    private static final int PLAYER1 = 1;
    private static final int PLAYER2 = 2;

    // The board
    private int[][] board;

    // The number of moves made
    private int count;

    public GameLogic() {}

    public void initializeGame(int width, int height, int player) {
        // Check board size
        if (width * height > MAX_SIZE)
            throw new RuntimeException("The board is too big for the game logic. The board size (height * width) may not exceed " + MAX_SIZE);

        // Set board dimensions
        this.width = width;
        this.height = height;
        height1 = this.height + 1;
        height2 = this.height + 2;

        size = width * height;

        board = new int[width][height];

        this.player = player;
    }

    public Winner gameFinished() {
        // Check if four got connected in the last move
        long bitboard = convertToBitBoard(player);
        if (hasFourConnected(bitboard))
            return player == PLAYER1 ? Winner.PLAYER1 : Winner.PLAYER2;

        // Chech if we have a tie due to a filled board
        if (count == size)
            return Winner.TIE;

        return Winner.NOT_FINISHED;
    }

    /**
     * Converts a board to a bitboard representation of the given player's coins.
     * @param player The player.
     * @return Bitboard for the player.
     */
    private long convertToBitBoard(int player) {
        long bitboard = 0;

        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (board[x][y] == player)
                    bitboard |= 1L << (y + x * height1);

        return bitboard;
    }

    /**
     * Check if a player has four connected.
     * @param bitboard A bitboard representation of the player's coins on the board.
     * @return True if the player has four connected, false otherwise.
     */
    final boolean hasFourConnected(long bitboard)
    {
        // Print bitboard for debug
        //StdOut.printf("Player%d: %48s\n", player, Long.toBinaryString(bitboard));

        long a = bitboard & bitboard >> height;
        long b = bitboard & bitboard >> height1;
        long c = bitboard & bitboard >> height2;
        long d = bitboard & bitboard >> 1;

        return (a & a >> 2 * height  |  // check diagonal   \
                b & b >> 2 * height1 |  // check horizontal -
                c & c >> 2 * height2 |  // check diagonal   /
                d & d >> 2 * 1) != 0;   // check vertical   |
    }

    public void insertCoin(int x, int player) {
        // Update the current player
        this.player = player;

        // Increment move counter
        count++;

        // Find the y-coordinate
        int y = 0;
        while (board[x][y] != 0)
            y++;
        board[x][y] = player;
    }

    public int decideNextMove() {
        // Pick a column at random
        int column = StdRandom.uniform(width);

        // Make sure the column isn't filled up yet
        while (isFull(column))
            column = StdRandom.uniform(width);

        return column;
    }

    private boolean isFull(int column) {
        return board[column][height - 1] != 0;
    }
}
