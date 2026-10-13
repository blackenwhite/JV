package com.confluent.sudoku;

public class Sudoku1 {
    private int[][] rowCountMap;// rowId -> freq of a digit
    private int[][] colCountMap; // colId -> freq of a digit
    private int[][] boxCountMap; // boxId -> freq of a digit

    public boolean isValidSudoku(char[][] board) {
        rowCountMap = new int[9][10];
        colCountMap = new int[9][10];
        boxCountMap = new int[9][10];
        for(int i=0;i<9;i++) {
            for(int j=0;j<9;j++) {
                if(board[i][j]=='.') {
                    continue;
                }
                int d = board[i][j]-'0';
                if(!isValid(board, i, j, d)) {
                    return false;
                }else{
                    rowCountMap[i][d]++;
                    colCountMap[j][d]++;
                    boxCountMap[getBoxId(i,j)][d]++;
                }
            }
        }
        return true;
    }

    public void solveSudoku(char[][] board) {
        rowCountMap = new int[9][10];
        colCountMap = new int[9][10];
        boxCountMap = new int[9][10];
        if(!isValidSudoku(board)) {
            return;
        }
        populateMapWithExistingValues(board);
        helper(board, 0, 0);
    }

    private void populateMapWithExistingValues(char[][] board) {
        for(int i=0;i<9;i++) {
            for(int j=0;j<9;j++) {
                if(board[i][j]!='.') {
                    int d = board[i][j]-'0';
                    rowCountMap[i][d]++;
                    colCountMap[j][d]++;
                    boxCountMap[getBoxId(i,j)][d]++;
                }
            }
        }
    }

    private boolean helper(char[][] board, int r, int c) {
        if(r==9) {
            return true;
        }
        if(c==9) {
            return helper(board, r+1, 0);
        }
        if(board[r][c]!='.') {
            return helper(board, r, c+1);
        }

        for(int d = 1;d<=9;d++){
            char digit = (char)(d+'0');
            if(isValid(board, r, c, d)) {
                board[r][c] = digit;
                rowCountMap[r][d]++;
                colCountMap[c][d]++;
                boxCountMap[getBoxId(r,c)][d]++;
                if(helper(board, r, c+1)) {
                    return true;
                } else {
                    rowCountMap[r][d]--;
                    colCountMap[c][d]--;
                    boxCountMap[getBoxId(r,c)][d]--;
                }
                board[r][c] = '.';
            }
        }
        return false;
    }

    private boolean isValid(char[][] board, int r, int c, int d) {
        if(rowCountMap[r][d]>=1) return false;
        if(colCountMap[c][d]>=1) return false;
        int boxId = getBoxId(r, c);
        if(boxCountMap[boxId][d]>=1) return false;
        return true;
    }

    private int getBoxId(int r, int c) {
        int startRow = r/3;
        int startCol = c/3;
        return startRow*3+startCol;
    }
}
