public class GameLogic implements IGameLogic {
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

    private static final int MAX_SIZE = 64;

    // The current player's id
    private int me;
    private static final int EMPTY   = 0;
    private static final int PLAYER1 = 1;
    private static final int PLAYER2 = 2;

    // The board
    //private int[][] board; // obsolete
    private long[] boards = new long[3];

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
        all1 = (1L << (height1 * width)) - 1L;
        col1 = (1L << height1) - 1L;
        bottom = all1 / col1;
        top = bottom << height;

        //board = new int[width][height];
        boards[0] = boards[1] = boards[2] = 0L;

        me = player;
    }

    /**
     * Terminal test
     * @return
     */
    public Winner gameFinished() {
        // Check if four got connected in the last move
        if (hasFourConnected(boards[PLAYER1]))
            return Winner.PLAYER1;

        if (hasFourConnected(boards[PLAYER2]))
            return Winner.PLAYER2;

        // Chech if we have a tie due to a filled board
        return ((boards[0] | top) == all1) ? Winner.TIE : Winner.NOT_FINISHED;
    }

    public void insertCoin(int column, int player) {
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

    // Shifting operations:
    // d c
    // x b
    //   a

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

        return (a & a >> 2 * height  |  // check diagonal   \
                b & b >> 2 * height1 |  // check horizontal -
                c & c >> 2 * height2 |  // check diagonal   /
                d & d >> 2 * 1) != 0;   // check vertical   |
    }

    private boolean isFull(int column) {
        return ((boards[0] >> height1 * column) + 1L & top) != 0;
    }

    // region Visualization

    private long fromString(String string) {
        long bitboard = 0L;
        String[] rows = string.split("\n");
        for (int y = 0; y < height; y++) {
            String[] cells = rows[y].split(" ");
            for (int x = 0; x < width; x++) {
                if (cells[x].equalsIgnoreCase("x")) {
                    bitboard |= 1L << (height - 1 - y) + height1 * x;
                }
            }
        }
        return bitboard;
    }

    private String toString(long bitboard) {
        String s = "";

        for (int y = 0; y < height; y++) {
            long row = bitboard >> height - 1 - y;
            for (int x = 0; x < width; x++) {
                s += (row & 1L) != 0 ? "X " : ". ";
                row >>= height1;
            }
            s += '\n';
        }

        return s;
    }

    private String toString(long bitboard1, long bitboard2) {
        String s = "";

        for (int y = 0; y < height; y++) {
            long row1 = bitboard1 >> height - 1 - y;
            long row2 = bitboard2 >> height - 1 - y;
            for (int x = 0; x < width; x++) {
                s += " " + ((row1 & 1L) != 0 ? "X" : ((row2 & 1L) != 0 ? "O" : "."));
                row1 >>= height1;
                row2 >>= height1;
            }
            s += '\n';
        }

        return s;
    }

    // endregion

    // endregion

    // region MiniMax

    private int player(long[] state) {
        return (Long.bitCount(state[0]) % 2) + 1;
    }

    private long[] actions(long[] state) {
        long[] actions = new long[width];

        long bitboard = state[0];
        for (int x = 0; x < width; x++) {
            int shift = x * height1;
            actions[x] = (bitboard + (1L << shift)) & (col1 << shift);
        }

        return actions;
    }

    private long[] result(long[] state, long action) {
        int player = player(state);
        state[0] |= action;
        state[player] |= action;
        return state;
    }

    private boolean terminalTest(long[] state) {
        int player = player(state);

        // player has 4 connect or board is full
        return hasFourConnected(state[player]) || ((state[0] | top) == all1);
        // TODO: is it enough to check one player?
    }

    private int utility(long[] state) {
        // Check if player1 has won
        if (hasFourConnected(state[1]))
            return 1;

        // Check if player2 has won
        if (hasFourConnected(state[2]))
            return -1;

        // No winner
        return 0;
    }
    
    // endregion
}
