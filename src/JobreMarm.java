
import java.util.Random;


public class JobreMarm implements IGameLogic {
    private Board board;
    private int playerID;
    private int turnsTaken = 0;
    private final int CUTOFF = 20;
    private int x;
    private int y;
    
    
    
    public JobreMarm() {

    }
	
    public void initializeGame(int x, int y, int playerID) {
        this.x = x;
        this.y = y;
        this.board = new Board(x, y, playerID);
        this.playerID = playerID;
        System.out.println("Player "+playerID+" is Jonas & Marcher!");
    }
	
    public final Winner gameFinished() {
        return board.gameFinished();
    }


    public final void insertCoin(int column, int playerID) {
        board.insertCoin(column, playerID);
        turnsTaken++;
    }
    
    public int otherPlayer(int playerID) {
        return 3 - playerID;
    }
    

    public int decideNextMove() {
        int cutoff = (int)(CUTOFF*.2 + (CUTOFF*.8*(turnsTaken/(1.*x*y))));
        StdOut.println("Cutoff: " + cutoff);
        return alphaBetaCutOff(cutoff);
    }

    private int alphaBetaCutOff(int cutoff) {
        int returnAction = -1;
        int max = Integer.MIN_VALUE;
        for (int a : board.actions()) {
            int res=this.minValue(board.result(a, playerID), Integer.MIN_VALUE, Integer.MAX_VALUE, cutoff);
            System.out.println(res);
            if (res > max) {
                if(res==10000) return a; //We will be winning THIS turn!
                returnAction = a;
                max = res;
            }
        }
        System.out.println(max);
        return returnAction;
    }
    
    private int maxValue(Board board, int alpha, int beta, int cutoff) {
        if(board.terminalTest() || cutoff == 0) return ((Board)board).utility();
        int v = Integer.MIN_VALUE;
        for(int action : board.actions()){
            v = Math.max(v, (int)(minValue(board.result(action, playerID), alpha, beta, cutoff-1)*.99));
            if(v >= beta) return v;
            alpha = Math.max(alpha, v);
        }   
        return v;
    }
    
    private int minValue(Board board, int alpha, int beta, int cutoff) {
        if(board.terminalTest() || cutoff == 0) return ((Board)board).utility();
        int v = Integer.MAX_VALUE;
        for(int action : board.actions()){
            v = Math.min(v, (int)(maxValue(board.result(action, otherPlayer(playerID)), alpha, beta, cutoff-1)*.99));
            if(v <= alpha) return v;
            beta = Math.min(beta, v);
        }
        return v;
    }

    private class Board {
        
        private int[][][] uVal = new int[5][5][5];
        private int x = 0;
        private int y = 0;
        private int playerID;
        private int[][] board;
        private int slotsLeft;
        private Winner winner = Winner.NOT_FINISHED;
        private final int WINNING_VALUE = 10000;
        
        
        public Board(int x, int y, int playerID) {
            this.x = x;
            this.y = y;
            this.playerID = playerID;
            this.board = new int[x][y];
            this.slotsLeft = x * y;
            
            
            //Initialize utility values
            uVal[0][4][0] = 0;
            uVal[0][3][1] = 0;
            uVal[0][2][2] = 0;
            uVal[0][1][3] = 0;
            uVal[0][0][4] = 0;
            uVal[1][3][0] = (int)(.03*WINNING_VALUE);
            uVal[1][2][1] = (int)(.02*WINNING_VALUE);
            uVal[1][1][2] = (int)(.01*WINNING_VALUE);
            uVal[1][0][3] = (int)(.005*WINNING_VALUE);
            uVal[2][2][0] = (int)(.05*WINNING_VALUE);
            uVal[2][1][1] = (int)(.04*WINNING_VALUE);
            uVal[2][0][2] = (int)(.02*WINNING_VALUE);
            uVal[3][1][0] = (int)(.08*WINNING_VALUE);
            uVal[3][0][1] = (int)(.06*WINNING_VALUE);
        }
        
        public void insertCoin(int column, int playerID) {
           int row = 0;
        
            while (board[column][row] != 0) 
            {
                row++;
                if(row == y)
                    throw new IllegalArgumentException("There is no space in column " + column);
            }

            board[column][row] = playerID;

            //---------- Is there now a winner? ---------------//
            // reset counts
            for(Direction d : Direction.values())
                d.Count = 0;

            // For each possible direction
            for(Direction d : Direction.values()){
                // Go a max of 3 out
                for(int i = 1; i <= 3; i++){
                    if(     insideBoard(column + d.x * i, row + d.y * i) &&      // If the new coordinate is inside the board
                            board[column + d.x * i][row + d.y * i] == playerID){ // and the new coordinate is the same color
                        // increase the count in that direction
                        d.Count++;
                    }
                    // If not we do not want to seek further
                    else break;
                }
            }

            // If any of the combinations are 3 or more then we have a winner
            if(     (Direction.HL.Count + Direction.HR.Count) >= 3 ||
                    (Direction.VU.Count + Direction.VD.Count) >= 3 ||
                    (Direction.DDL.Count + Direction.DUR.Count) >= 3 ||
                    (Direction.DDR.Count + Direction.DUL.Count) >= 3)
                    winner = (playerID == 1 ? Winner.PLAYER1 : Winner.PLAYER2);



            // If we still have not found a winner, test for Tie.
            if(winner == Winner.NOT_FINISHED && --slotsLeft == 0) winner = Winner.TIE;
        }
        
        public boolean insideBoard(int col, int row)
        {
            return col < x && col >= 0 && row < y && row >= 0;
        }
        
        public boolean playable(int i, int j) {
            if(!insideBoard(i, j) || board[i][j] != 0) return false;
            return (!insideBoard(i, j-1) || board[i][j-1] != 0);
        }
        
        public boolean columnFull(int column){
            int row = 0;

            while (board[column][row] != 0) {
                row++;
                if(row == y)
                    return true;
            }
            return false;
        }

        public Winner gameFinished() {
            return this.winner;
        }
        
        public boolean terminalTest() {
            return this.gameFinished() != Winner.NOT_FINISHED;
        }

        public int[] actions() {
            int[] temp = new int[x];
            int valid = 0;
            for (int i = 0; i < x; i++) 
                if(!this.columnFull(i))
                    temp[valid++] = i;
            
            int[] list = new int[valid];
            for (int i = 0; i < valid; i++) 
                list[i] = temp[i];
            
            return list;
        }

        public Board result(int column, int playerID) {
            Board newBoard = this.clone();
            newBoard.insertCoin(column, playerID);
            return newBoard;
        }
        

        @Override
        public Board clone() {
            Board newBoard = new Board(x, y, playerID);
            newBoard.slotsLeft = this. slotsLeft;
            newBoard.winner = this.winner;
            
            for (int i = 0; i < x; i++) {
                for (int j = 0; j < y; j++) {
                    newBoard.board[i][j] = this.board[i][j];
                }
            }
            
            return newBoard;
        }
        
        public int utility(){
            if(this.winner != Winner.NOT_FINISHED){
                if(this.winner == Winner.NOT_FINISHED) return 0;
                if(this.winner == Winner.TIE) return 0;
                if((this.winner == Winner.PLAYER1 && this.playerID == 1) ||
                   (this.winner == Winner.PLAYER2 && this.playerID == 2)) return WINNING_VALUE;
                else return -1*WINNING_VALUE;
            }
            
            int value = 0;
            
            for (int i = 0; i < x; i++) {
                for (int j = 0; j < y; j++) {
                    value += checkFeature(i, j);
                }
            }
            
            return value;
        }

        private int checkFeature(int i, int j) {
            
            int value = 0;
            
            //Check vertical
            if(insideBoard(i+3, j)){
                int[] vector = new int[3];
                int p = 0;
                for (int n = 0; n<4; n++) {
                    int f = board[i+n][j];
                    if(f == 0) {
                        if(playable(i+n, j)) vector[1]++;
                        else vector[2]++;
                    }
                    else
                    {
                        if(p == 0) p = f;
                        if(f == p) vector[0]++;
                        else break;
                    }
                }
                
                int factor = (p == playerID) ? 1: -1;
                value += factor * this.uVal[vector[0]][vector[1]][vector[2]];
            }
            
            //Check horizontal
            if(insideBoard(i, j+3)){
                int[] vector = new int[3];
                int p = 0;
                for (int n = 0; n<4; n++) {
                    int f = board[i][j+n];
                    if(f == 0) {
                        if(playable(i, j+n)) vector[1]++;
                        else vector[2]++;
                    }
                    else
                    {
                        if(p == 0) p = f;
                        if(f == p) vector[0]++;
                        else break;
                    }
                }
                int factor = (p == playerID) ? 1: -1;
                value += factor * this.uVal[vector[0]][vector[1]][vector[2]];
            }
            
            //Check diagonal right-up
            if(insideBoard(i+3, j+3)){
                int[] vector = new int[3];
                int p = 0;
                for (int n = 0; n<4; n++) {
                    int f = board[i+n][j+n];
                    if(f == 0) {
                        if(playable(i+n, j+n)) vector[1]++;
                        else vector[2]++;
                    }
                    else
                    {
                        if(p == 0) p = f;
                        if(f == p) vector[0]++;
                        else break;
                    }
                }
                int factor = (p == playerID) ? 1: -1;
                value += factor * this.uVal[vector[0]][vector[1]][vector[2]];
            }
             
            //Check diagonal right-down
            if(insideBoard(i+3, j-3)){
                int[] vector = new int[3];
                int p = 0;
                for (int n = 0; n<4; n++) {
                    int f = board[i+n][j-n];
                    if(f == 0) {
                        if(playable(i+n, j-n)) vector[1]++;
                        else vector[2]++;
                    }
                    else
                    {
                        if(p == 0) p = f;
                        if(f == p) vector[0]++;
                        else break;
                    }
                }
                int factor = (p == playerID) ? 1: -1;
                value += factor * this.uVal[vector[0]][vector[1]][vector[2]];
            }
            
            return value;
        }
    }
    
    public enum Direction {
            HL(-1, 0), HR(1, 0), VU(0, 1), VD(0, -1), DUR(1, 1), DDL(-1, -1), DUL(1, -1), DDR(-1, 1);
            
            public final int x;
            public final int y;
            public int Count;
            
            Direction(int x, int y)
            {
                this.x = x;
                this.y = y;
            }
        }
}
