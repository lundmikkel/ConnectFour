public class RandomGameLogic implements IGameLogic {
    // region Fields

    // Width of the board
    private int width;
    // Height of the board
    private int height;
    private int height1;
    private int height2;

    private long all1;
    private long col1;
    private long bottom;
    private long top;

    private int size;
    private static final int MAX_SIZE = 64;

    // The current player's id
    private int player;
    private static final int EMPTY = 0;
    private static final int PLAYER1 = 1;
    private static final int PLAYER2 = 2;

    // The board
    //private int[][] board; // obsolete
    private long[] boards = new long[3];

    // The number of moves made
    private int count;

    /// endregion

    // bitmask corresponds to board as follows in 7x6 case:
    //  .  .  .  .  .  .  . TOP
    //  5 12 19 26 33 40 47
    //  4 11 18 25 32 39 46
    //  3 10 17 24 31 38 45
    //  2  9 16 23 30 37 44
    //  1  8 15 22 29 36 43
    //  0  7 14 21 28 35 42 BOTTOM

    public void initializeGame(int width, int height, int player) {
        // Check board size
        if (width * (height + 1) > MAX_SIZE)
            throw new RuntimeException("The board is too big for the game logic. The board size (height * width) may not exceed " + MAX_SIZE);

        // Set board dimensions
        this.width = width;
        this.height = height;
        height1 = this.height + 1;
        height2 = this.height + 2;

        // Calculate constants
        size = width * height;
        all1 = (1L << (height1 * width)) - 1L;
        col1 = (1L << height1) - 1L;
        bottom = all1 / col1;
        top = bottom << height;

        //board = new int[width][height];
        boards[0] = boards[1] = boards[2] = 0L;

        this.player = player;
    }

    /**
     * Terminal test
     * @return
     */
    public Winner gameFinished() {
        // Check if four got connected in the last move
        long bitboard = boards[player];
        if (hasFourConnected(bitboard))
            return player == PLAYER1 ? Winner.PLAYER1 : Winner.PLAYER2;

        // Chech if we have a tie due to a filled board
        return (count == size) ? Winner.TIE : Winner.NOT_FINISHED;
    }

    public void insertCoin(int column, int player) {
        // Update the current player
        this.player = player;

        // Increment move counter
        count++;

        long bit = (((boards[0] >> (height1 * column)) & col1) + 1L) << (height1 * column);

        boards[0] |= bit;
        boards[player] |= bit;
    }

    public int decideNextMove() {
        // Pick a column at random
        int column = StdRandom.uniform(width);

        // Make sure the column isn't filled up yet
        while (isFull(column))
            column = StdRandom.uniform(width);

        return column;
    }

    // region BitBoard operations

    /**
     * Check if a player has four connected.
     *
     * @param bitboard A bitboard representation of the player's coins on the board.
     * @return True if the player has four connected, false otherwise.
     */
    private boolean hasFourConnected(long bitboard) {
        long a = bitboard & bitboard >> height;
        long b = bitboard & bitboard >> height1;
        long c = bitboard & bitboard >> height2;
        long d = bitboard & bitboard >> 1;

        return (a & a >> 2 * height |  // check diagonal   \
                b & b >> 2 * height1 |  // check horizontal -
                c & c >> 2 * height2 |  // check diagonal   /
                d & d >> 2 * 1) != 0;   // check vertical   |
    }

    private boolean isFull(int column) {
        return ((boards[0] >> height1 * column) + 1L & top) != 0;
    }

    // endregion
}
