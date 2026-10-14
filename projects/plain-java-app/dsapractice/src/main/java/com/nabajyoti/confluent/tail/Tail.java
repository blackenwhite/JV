package com.nabajyoti.confluent.tail;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class Tail {
    private final int CHUNK_SIZE = 4096;

    String getLastNLines(File file, int n) throws IOException {
        if(n<=0) {
            return "";
        }

//        try(RandomAccessFile raf = new RandomAccessFile(file, "r")) {
//
//        }

        StringBuilder stringBuilder = new StringBuilder();
//        BufferedOutputStream bufferedOutputStream; -> can use that in prod
        // should use try with resources for randomaccessfile

        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
        long fileSize = randomAccessFile.length();
        if(fileSize==0) {
            return "";
        }

        long lastPosition = fileSize-1;
        // if the last character is a new line then ignore it
        randomAccessFile.seek(lastPosition);
        int lastChar = randomAccessFile.read();
        if(lastChar=='\n') {
            lastPosition--;
        }

        long currentPosition = lastPosition+1;

        int count = 0;

        byte[] buffer = new byte[CHUNK_SIZE];
        boolean isFound = false;
        int ansOffset = -1;

        while(currentPosition>0) {
            // bytes to read, start, end
            long start = Math.max(0, currentPosition-CHUNK_SIZE);
            long end = currentPosition - 1;

            randomAccessFile.seek(start);

            randomAccessFile.readFully(buffer, 0, (int)(end-start+1));

            // go from last to start and count the number of '\n'
            for(int i=(int)end; i>=start;i--) {
                if((char)(buffer[i-(int)start])=='\n') {
                    count++;
                    if(count==n) {
                        isFound = true;
                        ansOffset = i+1;
                        break;
                    }
                }
            }

            if(isFound) {
                break;
            }

            currentPosition = start;
        }

        if(!isFound) {
            // read entire file and retun that
            ansOffset = 0;
        }

        // output
        for(int i=ansOffset;i<fileSize;i+=CHUNK_SIZE) {
            int bytesToRead = Math.min(CHUNK_SIZE, (int)(fileSize-i));
            randomAccessFile.seek(i);
            randomAccessFile.readFully(buffer, 0, bytesToRead);
            stringBuilder.append(new String(buffer, 0, bytesToRead, StandardCharsets.UTF_8));
        }
        randomAccessFile.close();

        return stringBuilder.toString();
    }

    public static void main(String[] args) throws IOException {
        Tail t = new Tail();
        File f = Path.of("/Users/nabajyotimajumdar/F/JV/projects/plain-java-app/dsapractice/src/main/java/com/nabajyoti/confluent/tail/content.txt").toFile();
        System.out.println(f);
        System.out.println(t.getLastNLines(f, 3));
    }
}
