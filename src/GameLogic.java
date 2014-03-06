import java.util.Arrays;

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
    private static final int PLAYER1 = 1;
    private static final int PLAYER2 = 2;

    // Points
    private static final int WIN  = +1;
    private static final int LOSS = -1;
    private static final int TIE  =  0;

    // The board
    private long[] currentState = new long[3];

    private long[][] states;
    private int[][] utils;
    private short[] frequency;

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
        all1 = (1L << height1 * width) - 1L;
        col1 = (1L << height1)         - 1L;
        bottom = all1 / col1;
        top = bottom << height;

        currentState[0] = currentState[1] = currentState[2] = 0L;

        states = new long[width * height][3];
        utils  = new  int[width * height][width];

        frequency = getFrequency();

        me = player;

        //
        //long bitboard1 = 1L << 7;
        //long bitboard2 = 1L << 9 | 1L << 21;
        //
        //long bitboardA = bitboard1 | 1L << 8;
        //long bitboardB = bitboard1 | 1L << 14;
        //
        //StdOut.println("BitboardA:\n" + toString(bitboardA, bitboard2));
        //StdOut.println("BitboardB:\n" + toString(bitboardB, bitboard2));
        //
        //StdOut.println("BitboardA free ended pairs heuristic: " + hPairsFreeEnded(bitboardA, bitboard2));
        //StdOut.println("BitboardB free ended pairs heuristic: " + hPairsFreeEnded(bitboardB, bitboard2));
        //
        //long bitboard = 0L;
        //bitboard |= 1L << 14;
        //bitboard |= 1L << 22;
        //bitboard |= 1L << 28;
        //
        //long bitboard1 = bitboard, bitboard2 = bitboard, bitboard3 = bitboard;
        //
        //bitboard1 |= 1L << 21;
        //bitboard2 |= 1L << 29;
        //bitboard3 |= 1L << 35;
        //
        //StdOut.println("Bitboard1:\n" + toString(bitboard1));
        //StdOut.println("Bitboard2:\n" + toString(bitboard2));
        //StdOut.println("Bitboard3:\n" + toString(bitboard3));
        //
        //StdOut.println("Bitboard1 pairs heuristic: " + hPairs(bitboard1));
        //StdOut.println("Bitboard2 pairs heuristic: " + hPairs(bitboard2));
        //StdOut.println("Bitboard3 pairs heuristic: " + hPairs(bitboard3));
        //StdOut.println("Bitboard1 trebles heuristic: " + hTrebles(bitboard1));
        //StdOut.println("Bitboard2 trebles heuristic: " + hTrebles(bitboard2));
        //StdOut.println("Bitboard3 trebles heuristic: " + hTrebles(bitboard3));
        //
    }

    public Winner gameFinished() {
        // Check if four got connected in the last move
        if (hasFourConnected(currentState[PLAYER1]))
            return Winner.PLAYER1;

        if (hasFourConnected(currentState[PLAYER2]))
            return Winner.PLAYER2;

        // Chech if we have a tie due to a filled board
        return isTie(currentState) ? Winner.TIE : Winner.NOT_FINISHED;
    }

    private boolean isTie(long[] state) {
        return (state[0] | top) == all1;
    }

    private boolean isValid(long action) {
        return (action & top) == 0;
    }

    public void insertCoin(int column, int player) {
        long action = (currentState[0] + bottom) & (col1 << column * height1);

        currentState[0] |= action;
        currentState[player] |= action;
    }

    public int decideNextMove() {
        // TODO: Apply iterative deepening
        int cutoff = width * height;

        int v = maxValue(currentState, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, cutoff);

        StdOut.println(); for (int i = 0; i < width; i++) StdOut.println(i + ": " + utils[0][i]);

        for (int x = 0; x < width; x++)
            if (utils[0][x] == v)
                return x;

        throw new RuntimeException();

        // TODO
        //int maxX = 0;
        //int maxFrequency = 0;
        //int[] utilities = utils[0];
        //long[] actions = actions(currentState);

        //StdOut.println();
        //for (int i = 0; i < width; i++) {
        //    StdOut.println(i + ": " + utilities[i]);
        //}

        //for (int x = 0; x < width; x++)
        //    if (utilities[x] == v) {
        //        long action = actions[x];
        //        int bit = (int) (Math.log(action) / Math.log(2.0));

        //        if (frequency[bit] > maxFrequency) {
        //            maxFrequency = frequency[bit];
        //            maxX = x;
        //        }
        //    }

        //return maxX;
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

        return (a & a >> 2 * height |  // check diagonal   \
                b & b >> 2 * height1 |  // check horizontal -
                c & c >> 2 * height2 |  // check diagonal   /
                d & d >> 2 * 1) != 0;   // check vertical   |
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

    private int maxValue(long[] state, int alpha, int beta, int depth, int cutoff) {
        // Check if we should end the search
        if (terminalTest(state))
            return utility(state);

        // Set v to lowest possible value
        int v = Integer.MIN_VALUE;
        // Get the possible actions for the state
        long[] actions = actions(state);
        // TODO
        // Get a prioritized list of moves to explore
        //int[] indexOrder = getIndexOrder(state, actions);

        // Reset all utils for the current depth
        for (int i = 0; i < width; i++)
            utils[depth][i] = Integer.MIN_VALUE;

        // Iterate all moves
        for (int x = 0; x < width; x++) {
            //int x = indexOrder[i];
            long action = actions[x];

            // Check if valid move
            if (!isValid(action))
                continue;

            long[] newState = result(Arrays.copyOf(state, 3), action);

            // Get min value
            int min = utils[depth][x] = minValue(newState, alpha, beta, depth + 1, cutoff);

            // Check if min is higher
            if (min > v) {
                v = min;

                // Beta cut
                if (v >= beta)
                    return v;

                // Update alpha
                alpha = Math.max(alpha, v);
            }
        }

        return v;
    }

    private int minValue(long[] state, int alpha, int beta, int depth, int cutoff) {
        // Return if we are in a terminal state
        if (terminalTest(state))
            return utility(state);

        // Set v to lowest possible value
        int v = Integer.MAX_VALUE;
        // Get the possible actions for the state
        long[] actions = actions(state);
        // TODO
        // Get a prioritized list of moves to explore
        //int[] indexOrder = getIndexOrder(state, actions);

        // Reset all utils for the current depth
        for (int i = 0; i < width; i++)
            utils[depth][i] = Integer.MAX_VALUE;

        for (int x = 0; x < width; x++) {
            //int x = indexOrder[x];
            long action = actions[x];

            // Check if valid move
            if (!isValid(action))
                continue;

            long[] newState = result(Arrays.copyOf(state, 3), action);

            // Get min value
            int max = utils[depth][x] = maxValue(newState, alpha, beta, depth + 1, cutoff);

            // Check if min is higher
            if (max < v) {
                v = max;

                // Beta cut
                if (v <= alpha)
                    return v;

                // Update alpha
                beta = Math.min(beta, v);
            }
        }

        return v;
    }

    private int eval(long[] state) {
        // TODO: Make this work properly
        return 0;
    }

    private int[] getIndexOrder(long[] state, long[] actions) {
        int[] indexOrder = new int[width];
        int[] heuristics = new int[width];
        int player = player(state);
        int opponent = 3 - player;

        for (int x = 0; x < width; x++) {
            long action = actions[x];

            // Skip invalid actions
            if (!isValid(action))
                continue;

            heuristics[x] = h(state[player], state[opponent], action, x);
        }

        for (int i = 0; i < width; i++) {
            int maxI = 0;
            int max = -1;
            for (int x = 0; x < width; x++) {
                if (heuristics[x] > max) {
                    max = heuristics[x];
                    maxI = x;
                }
            }

            heuristics[maxI] = -1;
            indexOrder[i] = maxI;
        }

        return indexOrder;
    }

    private int h(long bitboard1, long bitboard2, long action, int column) {
        long nextBitboard1 = bitboard1 | action;
        long nextBitboard2 = bitboard2 | action;

        return
                // Check for four connected
                (hQuad(nextBitboard1) << 10) +
                // Check for blocking four connected
                (hQuad(nextBitboard2) << 8) +
                // Check for free-ended trebles
                (hTreblesFreeEnded(nextBitboard1, bitboard2) << 6) +
                // Check for trebles
                (hTrebles(nextBitboard1) << 4) +
                // Check for free-ended pairs
                (hPairsFreeEnded(nextBitboard1, bitboard2) << 2) +
                // Check for pairs
                (hPairs(nextBitboard1) << 0) +
                // Check column
                hColumn(column);
    }

    private int player(long[] state) {
        return Long.bitCount(state[0]) % 2 + 1;
    }

    private long[] actions(long[] state) {
        long[] actions = new long[width];
        long spots = state[0] + bottom;

        for (int x = 0; x < width; x++)
            actions[x] = spots & col1 << x * height1;

        return actions;
    }

    private long[] result(long[] state, long action) {
        int player = player(state);
        state[0] |= action;
        state[player] |= action;
        return state;
    }

    private boolean terminalTest(long[] state) {
        return hasFourConnected(state[PLAYER1]) ||
               hasFourConnected(state[PLAYER2]) ||
               isTie(state);
    }

    private int utility(long[] state) {
        // Check if player1 has won
        if (hasFourConnected(state[PLAYER1]))
            return me == PLAYER1 ? WIN : LOSS;

        // Check if player2 has won
        if (hasFourConnected(state[PLAYER2]))
            return me == PLAYER2 ? WIN : LOSS;

        // No winner
        if (isTie(state))
            return TIE;

        throw new RuntimeException();
    }

    // endregion

    // region Heuristics

    private int hColumn(int column) {
        return column > width / 2 ? width - column - 1 : column;
    }

    private int hPairs(long bitboard) {
        long a = bitboard & bitboard >> height;
        long b = bitboard & bitboard >> height1;
        long c = bitboard & bitboard >> height2;
        long d = bitboard & bitboard >> 1;

        return Long.bitCount(a) +
               Long.bitCount(b) +
               Long.bitCount(c) +
               Long.bitCount(d);
    }

    private int hPairsFreeEnded(long bitboard1, long bitboard2) {
        long a = bitboard1 & bitboard1 >> height;
        long b = bitboard1 & bitboard1 >> height1;
        long c = bitboard1 & bitboard1 >> height2;
        long d = bitboard1 & bitboard1 >> 1;

        // Find all possibly free spots - not top/opponent's coins
        long free = all1 ^ (bitboard2 | top);

        return Long.bitCount(a & free >> 2 * height) +
               Long.bitCount(b & free >> 2 * height1) +
               Long.bitCount(c & free >> 2 * height2) +
               Long.bitCount(d & free >> 2 * 1) +
               Long.bitCount(free & a >> height) +
               Long.bitCount(free & b >> height1) +
               Long.bitCount(free & c >> height2);
    }

    private int hTrebles(long bitboard) {
        long a = bitboard & bitboard >> height;
        long b = bitboard & bitboard >> height1;
        long c = bitboard & bitboard >> height2;
        long d = bitboard & bitboard >> 1;

        return Long.bitCount(a & a >> height) +
               Long.bitCount(b & b >> height1) +
               Long.bitCount(c & c >> height2) +
               Long.bitCount(d & d >> 1);
    }

    private int hTreblesFreeEnded(long bitboard1, long bitboard2) {
        long a = bitboard1 & bitboard1 >> height;
        long b = bitboard1 & bitboard1 >> height1;
        long c = bitboard1 & bitboard1 >> height2;
        long d = bitboard1 & bitboard1 >> 1;

        a = a & a >> height;
        b = b & b >> height1;
        c = c & c >> height2;
        d = d & d >> 1;

        // Find all possibly free spots - not top/opponent's coins
        long free = all1 ^ (bitboard2 | top);

        return Long.bitCount(a & free >> 3 * height) +
               Long.bitCount(b & free >> 3 * height1) +
               Long.bitCount(c & free >> 3 * height2) +
               Long.bitCount(d & free >> 3 * 1) +
               Long.bitCount(free & a >> height) +
               Long.bitCount(free & b >> height1) +
               Long.bitCount(free & c >> height2);
    }

    private int hQuad(long bitboard) {
        long a = bitboard & bitboard >> height;
        long b = bitboard & bitboard >> height1;
        long c = bitboard & bitboard >> height2;
        long d = bitboard & bitboard >> 1;

        return Long.bitCount(a & a >> 2 * height) +  // check diagonal   \
               Long.bitCount(b & b >> 2 * height1) + // check horizontal -
               Long.bitCount(c & c >> 2 * height2) + // check diagonal   /
               Long.bitCount(d & d >> 2 * 1);        // check vertical   |
    }

    public short[] getFrequency() {
        short[][] frequency2dArray = new short[width][height];

        // Horizontal
        for (int x = 0; x < width - 3; x++)
            for (int y = 0; y < height; y++)
                for (int i = 0; i < 4; i++)
                    frequency2dArray[x + i][y]++;

        // Vertical
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height - 3; y++)
                for (int i = 0; i < 4; i++)
                    frequency2dArray[x][y + i]++;

        // Diagonal /
        for (int x = 0; x < width - 3; x++)
            for (int y = 0; y < height - 3; y++)
                for (int i = 0; i < 4; i++)
                    frequency2dArray[x + i][y + i]++;

        // Diagonal \
        for (int x = 0; x < width - 3; x++)
            for (int y = 3; y < height; y++)
                for (int i = 0; i < 4; i++)
                    frequency2dArray[x + i][y - i]++;

        short[] frequencyArray = new short[width * height1];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                frequencyArray[x * height1 + y] = frequency2dArray[x][y];
            }
        }

        return frequencyArray;
    }

    // endregion


    // region To be done

    //private HashMap<long[], Integer> transpositionTable;

    // transposition table
    //int max = utils[depth][x] = transpositionTable.containsKey(newState)
    //        ? transpositionTable.get(newState)
    //        : maxValue(newState, alpha, beta, depth + 1, cutoff);

    //transpositionTable.put(state, v);

    // endregion
}
