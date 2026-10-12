package com.confluent.sudoku;

import java.util.HashMap;
import java.util.Map;

public class Sudoku {
    public static void main(String[] args) {
        Solution solution = new Solution();
        char[][] board = {
                {'8', '3', '.', '.', '7', '.', '.', '.', '.'},
                {'6', '.', '.', '1', '9', '5', '.', '.', '.'},
                {'.', '9', '8', '.', '.', '.', '.', '6', '.'},
                {'8', '.', '.', '.', '6', '.', '.', '.', '3'},
                {'4', '.', '.', '8', '.', '3', '.', '.', '1'},
                {'7', '.', '.', '.', '2', '.', '.', '.', '6'},
                {'.', '6', '.', '.', '.', '.', '2', '8', '.'},
                {'.', '.', '.', '4', '1', '9', '.', '.', '5'},
                {'.', '.', '.', '.', '8', '.', '.', '7', '9'}
        };
        System.out.println(solution.isValidSudoku(board));
    }
}

class Solution {
    public boolean isValidSudoku(char[][] board) {
        Map<Integer, int[]> rowCountMap = new HashMap<>();
        Map<Integer, int[]> colCountMap = new HashMap<>();
        Map<Integer, int[]> boxCountMap = new HashMap<>();

        for(int rowId = 0; rowId <9; rowId++) {
            for(int colId = 0; colId <9; colId++) {
                if(board[rowId][colId]=='.') {
                    continue;
                }
                int d = board[rowId][colId] - '0';

                int[] rowCount = rowCountMap.getOrDefault(rowId, new int[10]);
                rowCount[d]++;
                rowCountMap.put(rowId, rowCount);
                if(rowCount[d]>1) {
                    return false;
                }
                int[] colCount = colCountMap.getOrDefault(colId, new int[10]);
                colCount[d]++;
                colCountMap.put(colId, colCount);
                if(colCount[d]>1) {
                    return false;
                }

                int startRow = rowId/3;
                int startCol = colId/3;
                int boxId = startRow*3 + startCol;

                int[] boxCount = boxCountMap.getOrDefault(boxId, new int[10]);
                boxCount[d]++;
                boxCountMap.put(boxId, boxCount);
                if(boxCount[d]>1) {
                    return false;
                }
            }
        }
        return true;
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

        for(int d = 1; d<=9;d++) {
            board[r][c] = (char)(d+'0');
            if(isValid(board, r, c, board[r][c])) {
                if(helper(board, r, c+1)) {
                    return true;
                }
            }
            board[r][c] = '.';
        }
        return false;
    }

    private boolean isValid(char[][] board, int r, int c, char d) {
        for(int j=0;j<9;j++) {
            if(j==c) {
                continue;
            }
            if(board[r][j]==d) {
                return false;
            }
        }

        for(int i=0;i<9;i++){
            if(i==r) {
                continue;
            }
            if(board[i][c]==d) {
                return false;
            }
        }

        int startRow = (r/3)*3;
        int startCol = (c/3)*3;
        for(int i=startRow;i<(startRow+3);i++){
            for(int j=startCol; j< (startCol+3);j++) {
                if(i==r && j==c) {
                    continue;
                }
                if(board[i][j]==d) {
                    return false;
                }
            }
        }
        return true;

    }


    public void solveSudoku(char[][] board) {
        if(!isValidSudoku(board)) {
            return;
        }

        helper(board, 0, 0);
    }
}
