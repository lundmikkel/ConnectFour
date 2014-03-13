import java.util.HashMap;

public class GameLogic implements IGameLogic {
    // region Fields

    private static boolean debug = false;

    // Width of the board
    private int width;
    // Height of the board
    private int height;
    private int height1;
    private int height2;

    // Helper masks
    private long all1;
    private long col1;
    private long bottom;
    private long top;
    private long odd, even;
    private long[] winnerMasks;

    // Maximal possible size of the board
    private static final int MAX_SIZE = 64;

    // Player ids - We are always MAX
    private int COMMON = 0;
    private int MAX;
    private int MIN;
    private static final int PLAYER1 = 1;
    private static final int PLAYER2 = 2;

    // Points
    private static final int WIN  = +1 << 24;
    private static final int LOSS = -1 << 24;
    private static final int TIE  =  0;

    // The board
    private long[] currentState = new long[3];


    private short[] frequency;
    private int maxX;
    private int nextMove;
    private int lastCutoff = 10;

    // caches
    private HashMap<Long, int[]> actionCache = new HashMap<Long, int[]>(4 * 1000 * 1000);
    private HashMap<Long, Integer> evalCache = new HashMap<Long, Integer>();
    private boolean stop;

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

        odd = bottom;
        for (int i = 0; i < height; i+=2) odd = (odd << 2) | bottom;
        even = all1 ^ odd;

        currentState[COMMON] =
        currentState[PLAYER1] =
        currentState[PLAYER2] = 0L;

        MAX = player;
        MIN = 3 - player;

        winnerMasks = initWinnerMasks();
        frequency = getFrequency();
    }

    public Winner gameFinished() {
        if (hasFourConnected(currentState[PLAYER1]))
            return Winner.PLAYER1;

        if (hasFourConnected(currentState[PLAYER2]))
            return Winner.PLAYER2;

        // Chech if we have a tie due to a filled board
        return isTie(currentState[COMMON]) ? Winner.TIE : Winner.NOT_FINISHED;
    }

    public void insertCoin(int column, int player) {
        long action = (currentState[COMMON] + bottom) & (col1 << column * height1);

        currentState[COMMON] |= action;
        currentState[player] |= action;
    }

    public int decideNextMove() {
        actionCache = new HashMap<Long, int[]>(4 * 1000 * 1000);

        nextMove = -1;
        stop = false;
        Thread main = Thread.currentThread();
        Thread worker = new Thread(new Worker());
        worker.start();

        try {
            main.sleep(5 * 1000);

            // Wait until we have an answer, even if it takes more than 10 s
            while (nextMove < 0)
                main.sleep(100);

            // Stop
            worker.interrupt();
            stop = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Force first move
        if (currentState[COMMON] == 0)
            return width / 2;

        StdOut.println("Picked " + nextMove);
        return nextMove;
    }

    /**
     * Check if we have a tie, i.e. all positions are used.
     */
    private boolean isTie(long commonBoard) {
        return (commonBoard | top) == all1;
    }

    /**
     * Check if a move is valid, or the column is already full.
     */
    private boolean isValid(long action) {
        return (action & top) == 0;
    }


    private class Worker implements Runnable {

        @Override
        public void run() {
            // Board values
            long maxBoard = currentState[MAX];
            long minBoard = currentState[MIN];
            long commonBoard = currentState[COMMON];

            // Cutoff
            int cutoff = lastCutoff;
            int maxCutoff = width * height - Long.bitCount(commonBoard);

            Thread currentThread = Thread.currentThread();

            // Iterative deepening search
            do {
                if (currentThread.isInterrupted())
                    break;

                maxValue(maxBoard, minBoard, commonBoard, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, cutoff);
                nextMove = maxX;

                if (currentState[MAX] == 0)
                    lastCutoff = cutoff - 2;

                if (debug) StdOut.println("Found new best move (" + nextMove + ") with cutoff " + cutoff);
                cutoff += 2;
            } while(cutoff <= maxCutoff);
        }
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

    // endregion

    // region MiniMax

    private int maxValue(long maxBoard, long minBoard, long commonBoard, int alpha, int beta, int depth, int cutoff) {
        // Check if we should end the search
        if (terminalTest(maxBoard, minBoard, commonBoard))
            return utility(maxBoard, minBoard, commonBoard, depth);

        // if we have reached cutoff depth, evaluate board and return
        if (depth >= cutoff)
            return eval(maxBoard, minBoard, commonBoard, depth);

        // Set v to lowest possible value
        int v = Integer.MIN_VALUE;

        // Get the possible actions for the state
        long[] actions = actions(commonBoard);
        // Get a prioritized list of moves to explore
        int[] actionPriority = actionPriority(maxBoard, minBoard, commonBoard, actions);
        
        // Iterate all moves
        for (int i = 0; i < actionPriority.length && !stop; i++) {
            int x = actionPriority[i];
            long action = actions[x];

            // Check if valid move
            if (!isValid(action))
                continue;

            // Get min value
            int min = minValue(
                    maxBoard | action,
                    minBoard,
                    commonBoard | action,
                    alpha,
                    beta,
                    depth + 1,
                    cutoff
            );

            if (debug && depth == 0) StdOut.println(x + ": " + min);

            // Check if min is higher
            if (min > v) {
                v = min;

                if (depth == 0)
                    maxX = x;

                // Beta cut
                if (v >= beta)
                    return v;

                // Update alpha
                alpha = Math.max(alpha, v);
            }
        }

        return v;
    }

    private int minValue(long maxBoard, long minBoard, long commonBoard, int alpha, int beta, int depth, int cutoff) {
        // Return if we are in a terminal state
        if (terminalTest(maxBoard, minBoard, commonBoard))
            return utility(maxBoard, minBoard, commonBoard, depth);

        // if we have reached cutoff depth, evaluate board and return
        if (depth >= cutoff)
            return -eval(minBoard, maxBoard, commonBoard, depth);

        // Set v to lowest possible value
        int v = Integer.MAX_VALUE;

        // Get the possible actions for the state
        long[] actions = actions(commonBoard);
        // Get a prioritized list of moves to explore
        int[] actionPriority = actionPriority(minBoard, maxBoard, commonBoard, actions);

        for (int i = 0; i < actionPriority.length && !stop; i++) {
            int x = actionPriority[i];
            long action = actions[x];

            // Check if valid move
            if (!isValid(action))
                continue;

            // Get min value
            int max = maxValue(
                    maxBoard,
                    minBoard | action,
                    commonBoard | action,
                    alpha,
                    beta,
                    depth + 1,
                    cutoff
            );

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

    private int[] actionPriority(long thisBoard, long thatBoard, long commonBoard, long[] actions) {
        long hash = hashBoard(thisBoard, thatBoard);

        // Check cache
        if (actionCache.containsKey(hash))
            return actionCache.get(hash);

        int[] actionPriority = new int[width];
        int[] heuristics = new int[width];
        int block = -1;

        for (int x = 0; x < width; x++) {
            long action = actions[x];

            // Skip invalid actions
            if (!isValid(action))
                continue;

            heuristics[x] = h(thisBoard, thatBoard, action, x);

            // Return immediately on a win
            if (hasFourConnected(thisBoard | action)) {
                actionPriority = new int[]{x};
                actionCache.put(hash, actionPriority);
                return actionPriority;
            }

            // Save a block to allow returning wins first
            if (hasFourConnected(thatBoard | action))
                block = x;
        }

        // If any blocks found return
        if (block >= 0) {
            actionPriority = new int[]{block};
            actionCache.put(hash, actionPriority);
            return actionPriority;
        }

        // Selection sort-of
        for (int i = 0; i < width; i++) {
            int maxI = 0;
            int max = Integer.MIN_VALUE;

            for (int x = 0; x < width; x++)
                if (heuristics[x] > max)
                    max = heuristics[maxI = x];

            heuristics[actionPriority[i] = maxI] = Integer.MIN_VALUE;
        }

        actionCache.put(hash, actionPriority);
        return actionPriority;
    }

    private long[] actions(long commonBoard) {
        long[] actions = new long[width];
        commonBoard += bottom;

        for (int x = 0; x < width; x++)
            actions[x] = commonBoard & (col1 << (x * height1));

        return actions;
    }

    private boolean terminalTest(long maxBoard, long minBoard, long commonBoard) {
        return hasFourConnected(maxBoard) ||
               hasFourConnected(minBoard) ||
               isTie(commonBoard);
    }

    private int utility(long maxBoard, long minBoard, long commonBoard, int depth) {
        if (hasFourConnected(maxBoard)) return WIN + depth;
        if (hasFourConnected(minBoard)) return LOSS + depth;
        if (isTie(commonBoard)) return TIE;
        throw new RuntimeException("Utility function was called for a non-terminal state");
    }

    private int eval(long thisBoard, long thatBoard, long commonBoard, int depth) {
        int eval1 = eval1(thisBoard, thatBoard, commonBoard) * 2;
        int eval2 = eval2(thisBoard, thatBoard, commonBoard) * 5;

        return eval1 + eval2;

            //eval3(thisBoard, thatBoard, commonBoard) * 1;

        /*int value;

        // TODO: Test
        //long hash = hashBoard(thisBoard, thatBoard);
//
        //if (evalCache.containsKey(hash)) {
        //    value = evalCache.get(hash);
//
        //    // TODO: Remove
        //    if (evalCache2.get(new Key(thisBoard, thatBoard)) != value)
        //        throw new RuntimeException("Cache didn't work!");
//
        //    //StdOut.println("Cache hit on board " + thisBoard + " and " + thatBoard);
//
        //    return value;
        //}
//
        //// TODO: Remove
        //if (evalCache2.containsKey(new Key(thisBoard, thatBoard)))
        //    throw new RuntimeException("Cache didn't work!");


        // TODO: heuristics

        long free = all1 ^ (commonBoard | top);
        long thisThreats = threats(thisBoard, free);
        long thatThreats = threats(thatBoard, free);
        long actions = commonBoard + bottom;
        value = Long.bitCount(thisThreats) - ((thisThreats & actions) != 0 ? 1 : 0) -
                Long.bitCount(thatThreats) + ((thatThreats & actions) != 0 ? 1 : 0);

        double factor = 1 + 1./depth;
        value = (int) (value * 100 * factor) << 18 ;

        value +=
                // Check for free-ended trebles
                (hTreblesFreeEnded(thisBoard, thatBoard) << 16) +
                // Check for trebles with holes
                (hTreblesWithHoles(thisBoard, thatBoard) << 14) +
                // Check for double-free ended pairs
                (hPairsDoubleFreeEnded(thisBoard, thatBoard) << 12) +
                // Check for free-ended pairs
                (hPairsFreeEnded(thisBoard, thatBoard) << 8) +
                // Check for trebles
                (hTrebles(thisBoard) << 4) +
                // Check for pairs
                (hPairs(thisBoard) << 0);   

        //value = hWinningLines(thisBoard, thatBoard) - hWinningLines(thatBoard, thisBoard);

        // Save the eval to the cache
        //evalCache.put(hash, value);
        //evalCache2.put(new Key(thisBoard, thatBoard), value);

        return value;*/
    }

    private int eval1(long thisBoard, long thatBoard, long commonBoard) {
        int value = 0;

        for (long mask : winnerMasks) {
            int thisCount = Long.bitCount(thisBoard & mask);
            int thatCount = Long.bitCount(thatBoard & mask);

            if (thisCount > 0 && thatCount == 0) {
                value += 1 << thisCount * 2;
            }
            else if (thatCount > 0 && thisCount == 0) {
                value -= 1 << thatCount * 2;
            }
        }

        return value;
    }

    private int eval2(long thisBoard, long thatBoard, long commonBoard) {
        long free = all1 ^ (commonBoard | top);
        long thisThreats = threats(thisBoard, free);
        long thatThreats = threats(thatBoard, free);

        int thisEven = Long.bitCount(thisThreats & even);
        int thisOdd = Long.bitCount(thisThreats & odd);
        int thatEven = Long.bitCount(thatThreats & even);
        int thatOdd = Long.bitCount(thatThreats & odd);

        if (Long.bitCount(commonBoard) % 2 == 0) {
            // player 1 turn
            return (3*thisOdd+thisEven)-(2*thatEven+thatOdd);
        } else {
            // player 2 turn
            return (3*thisEven+thisOdd)-(2*thatOdd+thatEven);
        }
    }

    private int eval3(long thisBoard, long thatBoard, long commonBoard) {
        long free = all1 ^ (commonBoard | top);
        long thisThreats = threats(thisBoard, free);
        long thatThreats = threats(thatBoard, free);

        for (long mask : winnerMasks) {

            //if (thisCount == 3) {
            //    long threat = thisThreats & mask;
            //    int column = (int) (Math.log(threat)/Math.log(2)) / height1;
            //    long below = (free + threat) & col1 << (column * height1);
//
            //    value -= below << 1;
            //}
//
//
            //if (thatCount == 3) {
            //    long threat = thatThreats & mask;
            //    int column = (int) (Math.log(threat)/Math.log(2)) / height1;
            //    long below = (free + threat) & col1 << (column * height1);
//
            //    value += below << 1;
            //}
        }

        return 0;
    }

    private long hashBoard(long thisBoard, long thatBoard) {
        return ((thisBoard + thatBoard) * (thisBoard + thatBoard + 1) >> 1) + thatBoard;
    }

    // endregion

    // region Heuristics

    private int h(long thisBoard, long thatBoard, long action, int column) {
        long thisNextBoard = thisBoard | action;

        return
            // Check for free-ended trebles
            (hTreblesFreeEnded(thisNextBoard, thatBoard) << 16) +
            // Check for trebles with holes
            (hTreblesWithHoles(thisNextBoard, thatBoard) << 14) +
            // Check for double-free ended pairs
            (hPairsDoubleFreeEnded(thisNextBoard, thatBoard) << 12) +
            // Check for free-ended pairs
            (hPairsFreeEnded(thisNextBoard, thatBoard) << 8) +
            // Check for trebles
            (hTrebles(thisNextBoard) << 4) +
            // Check for pairs
            (hPairs(thisNextBoard) << 0) +
            // Check column
            hColumn(column);
    }

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

    private int hPairsDoubleFreeEnded(long thisBoard, long thatBoard) {
        long a = thisBoard & thisBoard >> height;
        long b = thisBoard & thisBoard >> height1;
        long c = thisBoard & thisBoard >> height2;
        long d = thisBoard & thisBoard >> 1;

        // Find all possibly free spots - not top/opponent's coins
        long free = all1 ^ (thisBoard | thatBoard | top);

        long a2 = free & free >> height;
        long b2 = free & free >> height1;
        long c2 = free & free >> height2;
        long d2 = free & free >> 1;

        return Long.bitCount(a & a2 >> 2 * height) +
               Long.bitCount(b & b2 >> 2 * height1) +
               Long.bitCount(c & c2 >> 2 * height2) +
               Long.bitCount(d & d2 >> 2 * 1) +
               Long.bitCount(a2 & a >> height) +
               Long.bitCount(b2 & b >> height1) +
               Long.bitCount(c2 & c >> height2);
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

    /**
     * Searches for XX_X
     */
    private int hTreblesWithHoles(long bitboard1, long bitboard2) {
        // XX
        long a = bitboard1 & bitboard1 >> height;
        long b = bitboard1 & bitboard1 >> height1;
        long c = bitboard1 & bitboard1 >> height2;

        // Find all possibly free spots - not top/opponent's coins
        long free = all1 ^ (bitboard2 | top);

        // _X
        long fa = free & bitboard1 >> height;
        long fb = free & bitboard1 >> height1;
        long fc = free & bitboard1 >> height2;

        // X_
        long af = bitboard1 & free >> height;
        long bf = bitboard1 & free >> height1;
        long cf = bitboard1 & free >> height2;

        return Long.bitCount(a & fa >> 2 * height) +
               Long.bitCount(b & fb >> 2 * height1) +
               Long.bitCount(c & fc >> 2 * height2) +
               Long.bitCount(af & a >> 2 * height) +
               Long.bitCount(bf & b >> 2 * height1) +
               Long.bitCount(cf & c >> 2 * height2);
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

    /**
     * Generate masks representing all possible ways to get four connected
     */
    public long[] initWinnerMasks() {
        int i = 0;
        int size = height * (width - 3) + (height - 3) * width + 2 * (height - 3) * (width - 3);
        long[] winnerMasks = new long[size];

        // Horizontal
        long mask = 0L;
        for (int j = 0; j < 4; j++)
            mask |= 1L << j * height1;

        for (int y = 0; y < height; y++)
            for (int x = 0; x < width - 3; x++)
                winnerMasks[i++] = (mask << (x * height1)) << y;


        // Vertical
        mask = 0L;
        for (int j = 0; j < 4; j++)
            mask |= 1L << j * 1;

        for (int y = 0; y < height - 3; y++)
            for (int x = 0; x < width; x++)
                winnerMasks[i++] = (mask << (x * height1)) << y;


        // Diagonal /
        mask = 0L;
        for (int j = 0; j < 4; j++)
            mask |= 1L << j * height2;

        for (int y = 0; y < height - 3; y++)
            for (int x = 0; x < width - 3; x++)
                winnerMasks[i++] = (mask << (x * height1)) << y;


        // Diagonal \
        mask = 0L;
        for (int j = 0; j < 4; j++)
            mask |= 1L << j * height;

        for (int y = 3; y < height; y++)
            for (int x = 0; x < width - 3; x++)
                winnerMasks[i++] = (mask << (x * height1)) << y;


        return winnerMasks;
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


    private long threats(long bitboard, long free) {
        long threats = 0L;

        // Diagonal \
        long aa = bitboard & bitboard >> height;
        long fa = free     & bitboard >> height;
        long af = free     & bitboard << height;

        threats |= fa & aa >> 2 * height;
        threats |= af & aa >> 1 * height;
        threats |= fa & aa << 2 * height;
        threats |= af & aa << 3 * height;


        // Horizontal -
        long bb = bitboard & bitboard >> height1;
        long fb = free     & bitboard >> height1;
        long bf = free     & bitboard << height1;

        threats |= fb & bb >> 2 * height1;
        threats |= bf & bb >> 1 * height1;
        threats |= fb & bb << 2 * height1;
        threats |= bf & bb << 3 * height1;


        // Diagonal /
        long cc = bitboard & bitboard >> height2;
        long fc = free     & bitboard >> height2;
        long cf = free     & bitboard << height2;

        threats |= fc & cc >> 2 * height2;
        threats |= cf & cc >> 1 * height2;
        threats |= fc & cc << 2 * height2;
        threats |= cf & cc << 3 * height2;

        // Vertical |
        long dd = bitboard & bitboard << 1;
        long df = free     & bitboard << 1;

        threats |= df & dd << 2;

        // Return the threats board now containing 1's in all the places we have threats
        return threats;
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
}
